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
@Tag(name = "Sessions", description = "Account access sessions, not parking sessions. Session hashes and refresh tokens are never returned.")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "The current session is unauthenticated, expired, or revoked.", content = @Content),
    @ApiResponse(responseCode = "403", description = "The session belongs to another account.", content = @Content),
    @ApiResponse(responseCode = "404", description = "Session not found.", content = @Content),
    @ApiResponse(responseCode = "400", description = "Invalid UUID.", content = @Content)
})
public class SessionsController {
    private final AuthenticationService service;
    private final Clock clock;
    public SessionsController(AuthenticationService service, Clock clock) { this.service = service; this.clock = clock; }
    @GetMapping("/me")
    @Operation(summary = "List my sessions", description = "Includes revoked and expired sessions. usable is derived from the stored status and expiresAt. Does not expose refreshTokenHash.")
    @ApiResponse(responseCode = "200", description = "Sessions owned by the authenticated account.")
    public List<SessionResource> mine(@AuthenticationPrincipal SpotgoUserPrincipal actor) {
        return service.ownSessions(actor).stream().map(s -> SessionResource.from(s, clock.instant())).toList();
    }
    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke one of my sessions", description = "Allows the caller to revoke another session belonging to the same account. Its access and refresh tokens can no longer be used.")
    @ApiResponse(responseCode = "204", description = "Session revoked.", content = @Content)
    public void revoke(@AuthenticationPrincipal SpotgoUserPrincipal actor, @PathVariable UUID sessionId, HttpServletRequest request) {
        service.revokeSession(actor, sessionId, request.getRemoteAddr());
    }
}
