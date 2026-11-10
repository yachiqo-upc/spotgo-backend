package com.yachiqo.spotgo.iam.interfaces.rest;
import com.yachiqo.spotgo.iam.application.AuthenticationService;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.iam.interfaces.rest.resources.SessionResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.Content;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.util.*;
@RestController
@RequestMapping(value = "/api/v1/sessions", produces = "application/json")
@Tag(name = "Sessions", description = "Sesiones de acceso, no de estacionamiento. Nunca devuelve hashes ni refresh tokens.")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Sesion actual no autenticada, vencida o revocada.", content = @Content),
    @ApiResponse(responseCode = "403", description = "La sesion pertenece a otra cuenta.", content = @Content),
    @ApiResponse(responseCode = "404", description = "Sesion inexistente.", content = @Content),
    @ApiResponse(responseCode = "400", description = "UUID invalido.", content = @Content)
})
public class SessionsController {
    private final AuthenticationService service;
    private final Clock clock;
    public SessionsController(AuthenticationService service, Clock clock) { this.service = service; this.clock = clock; }
    @GetMapping("/me")
    @Operation(summary = "Listar mis sesiones", description = "Incluye las revocadas y vencidas. usable combina el estado guardado y expiresAt; no expone refreshTokenHash.")
    @ApiResponse(responseCode = "200", description = "Sesiones propias.")
    public List<SessionResource> mine(@AuthenticationPrincipal SpotgoUserPrincipal actor) {
        return service.ownSessions(actor).stream().map(s -> SessionResource.from(s, clock.instant())).toList();
    }
    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revocar una sesion propia", description = "Permite cerrar otra sesion de tu misma cuenta. Impide usar su access token y su refresh token.")
    @ApiResponse(responseCode = "204", description = "Sesion revocada.", content = @Content)
    public void revoke(@AuthenticationPrincipal SpotgoUserPrincipal actor, @PathVariable UUID sessionId, HttpServletRequest request) {
        service.revokeSession(actor, sessionId, request.getRemoteAddr());
    }
}
