package com.yachiqo.spotgo.iam.interfaces.rest;

import com.yachiqo.spotgo.iam.application.AuthenticationService;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.iam.interfaces.rest.resources.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/authentication", produces = "application/json")
@Tag(name = "Authentication", description = "Cuenta, credencial PASSWORD y sesiones UUID. Authorize recibe accessToken; refreshToken solo se envia a /refresh.")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "JSON invalido o contraseña sin al menos 8 caracteres, mayuscula, minuscula, numero y simbolo.", content = @Content),
    @ApiResponse(responseCode = "401", description = "Credenciales, token o sesion invalidos, vencidos o revocados; cuenta no habilitada.", content = @Content),
    @ApiResponse(responseCode = "409", description = "Correo duplicado o conflicto de integridad.", content = @Content),
    @ApiResponse(responseCode = "429", description = "Demasiados intentos. Respetar Retry-After antes de reintentar.", content = @Content),
    @ApiResponse(responseCode = "500", description = "Error inesperado del servidor.", content = @Content)
})
public class AuthenticationController {
    private final AuthenticationService service;
    public AuthenticationController(AuthenticationService service) { this.service = service; }

    @PostMapping("/sign-in")
    @SecurityRequirements
    @Operation(summary = "Iniciar sesion", description = "Verifica credentials.secret_hash, registra una sesion y devuelve accessToken y refreshToken. Copia SOLO accessToken en Authorize. La contraseña y los tokens no se guardan en claro.")
    @ApiResponse(responseCode = "200", description = "Sesion creada. Guarda ambos tokens y sus vencimientos.")
    public AuthenticatedUserResource signIn(@Valid @RequestBody SignInResource body, HttpServletRequest request) {
        return service.signIn(body.email(), body.password(), request.getRemoteAddr());
    }

    @PostMapping("/sign-up")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar cuenta DRIVER", description = "Crea accounts, una credencial PASSWORD, una asignacion DRIVER y una sesion. Los datos personales se crean por separado con POST /profiles; no se duplican en accounts. No permite seleccionar roles privilegiados.")
    @ApiResponse(responseCode = "201", description = "Cuenta DRIVER y sesion creadas; el perfil todavia puede no existir.")
    public AuthenticatedUserResource signUp(@Valid @RequestBody SignUpResource body, HttpServletRequest request) {
        return service.signUp(body.email(), body.password(), request.getRemoteAddr());
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Renovar access token", description = "Recibe refreshToken sin requerir un access token vigente. Conserva sessionId y el vencimiento original de la sesion; entrega OTRO refreshToken e invalida el anterior. No reutilices el token viejo.")
    @ApiResponse(responseCode = "200", description = "Nuevos tokens emitidos. Reemplaza ambos en el cliente.")
    public AuthenticatedUserResource refresh(@Valid @RequestBody RefreshTokenResource body, HttpServletRequest request) {
        return service.refresh(body.refreshToken(), request.getRemoteAddr());
    }

    @PostMapping("/sign-out")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cerrar la sesion actual", description = "Requiere accessToken en Authorize. Revoca la sesion del token: tanto el access token como su renovacion dejan de ser aceptados.")
    @ApiResponse(responseCode = "204", description = "Sesion revocada; sin cuerpo de respuesta.", content = @Content)
    public void signOut(@AuthenticationPrincipal SpotgoUserPrincipal actor, HttpServletRequest request) {
        service.signOut(actor, request.getRemoteAddr());
    }

    @PostMapping("/password-reset/request")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Solicitar codigo de recuperacion", description = "Requiere configurar Resend. Devuelve la misma respuesta para correos conocidos y desconocidos. El codigo viaja por correo y su hash queda en la tabla auxiliar de recuperacion ya existente.")
    @ApiResponse(responseCode = "204", description = "Solicitud recibida; no confirma si el correo existe.", content = @Content)
    @ApiResponse(responseCode = "503", description = "Servicio de correo no configurado o no disponible.", content = @Content)
    public void requestReset(@Valid @RequestBody PasswordResetRequestResource body, HttpServletRequest request) {
        service.requestPasswordReset(body.email(), request.getRemoteAddr());
    }

    @PostMapping("/password-reset/confirm")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Confirmar recuperacion", description = "Valida codigo, vencimiento, maximo de intentos y uso unico. Cambia el hash de la contraseña y revoca TODAS las sesiones de la cuenta.")
    @ApiResponse(responseCode = "204", description = "Contraseña cambiada. Inicia sesion nuevamente.", content = @Content)
    public void confirmReset(@Valid @RequestBody PasswordResetConfirmResource body, HttpServletRequest request) {
        service.confirmPasswordReset(body.email(), body.code(), body.newPassword(), request.getRemoteAddr());
    }
}
