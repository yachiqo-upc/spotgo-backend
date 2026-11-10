package com.yachiqo.spotgo.iam.interfaces.rest;

import com.yachiqo.spotgo.iam.application.*;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.iam.interfaces.rest.resources.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/accounts", produces = "application/json")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "UUID, JSON, estado, rol, fechas o alcance de tenant invalidos.", content = @Content),
    @ApiResponse(responseCode = "401", description = "Requiere accessToken valido y sesion habilitada.", content = @Content),
    @ApiResponse(responseCode = "403", description = "La accion requiere SUPER_ADMIN o acceso al recurso propio.", content = @Content),
    @ApiResponse(responseCode = "404", description = "Cuenta o asignacion no encontrada.", content = @Content),
    @ApiResponse(responseCode = "409", description = "Conflicto de integridad; una cuenta solo admite una asignacion.", content = @Content)
})
public class AccountsController {
    private final AccountAdministrationService administration;
    private final UserAccountService authentication;
    public AccountsController(AccountAdministrationService administration, UserAccountService authentication) {
        this.administration = administration; this.authentication = authentication;
    }
    public record ChangeStatusResource(@NotNull AccountStatus status) {}
    public record AssignRoleResource(@NotNull UserRole roleType,
        @Schema(description = "Obligatorio para PARKING_ADMIN; NULL para DRIVER o SUPER_ADMIN") UUID tenantId,
        @Schema(description = "Omitir usa el instante actual") Instant validFrom,
        @Schema(description = "NULL significa sin fecha de termino; no equivale a estado ACTIVE") Instant validTo,
        AssignmentStatus status) {}

    @GetMapping("/me")
    @Operation(tags = "Accounts", summary = "Consultar mi cuenta", description = "Cuenta de quien envio el token, con su correo y estado. El perfil personal se consulta en /profiles/me.")
    @ApiResponse(responseCode = "200", description = "Cuenta propia encontrada.")
    public AccountResource mine(@AuthenticationPrincipal SpotgoUserPrincipal actor) { return AccountResource.from(administration.own(actor)); }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(tags = "Accounts", summary = "Listar cuentas", description = "Solo SUPER_ADMIN. page empieza en 0; size entre 1 y 100.")
    @ApiResponse(responseCode = "200", description = "Pagina de cuentas, sin contraseñas ni tokens.")
    public Page<AccountResource> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return administration.list(actor, page, size).map(AccountResource::from);
    }
    @GetMapping("/{accountId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(tags = "Accounts", summary = "Consultar una cuenta", description = "Solo SUPER_ADMIN; un PARKING_ADMIN no puede consultar libremente cuentas del sistema.")
    @ApiResponse(responseCode = "200", description = "Cuenta encontrada.")
    public AccountResource get(@AuthenticationPrincipal SpotgoUserPrincipal actor, @PathVariable UUID accountId) {
        return AccountResource.from(administration.get(actor, accountId));
    }
    @PatchMapping("/{accountId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(tags = "Accounts", summary = "Cambiar estado de cuenta", description = "Solo SUPER_ADMIN. Pasar a un estado distinto de ACTIVE revoca todas las sesiones. Reactivar la cuenta no reactiva sus sesiones antiguas.")
    @ApiResponse(responseCode = "200", description = "Estado actualizado.")
    public AccountResource status(@AuthenticationPrincipal SpotgoUserPrincipal actor, @PathVariable UUID accountId,
            @Valid @RequestBody ChangeStatusResource body, HttpServletRequest request) {
        return AccountResource.from(administration.status(actor, accountId, body.status(), request.getRemoteAddr()));
    }
    @GetMapping("/me/role")
    @Operation(tags = "Role assignments", summary = "Consultar mi asignacion de rol", description = "effective comprueba status y las fechas; validTo NULL no basta para habilitar un rol.")
    @ApiResponse(responseCode = "200", description = "Asignacion propia.")
    public RoleAssignmentResource ownRole(@AuthenticationPrincipal SpotgoUserPrincipal actor) {
        return RoleAssignmentResource.from(administration.role(actor, actor.getAccountId()), administration.now());
    }
    @GetMapping("/{accountId}/role")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(tags = "Role assignments", summary = "Consultar rol de una cuenta", description = "Solo SUPER_ADMIN.")
    @ApiResponse(responseCode = "200", description = "Asignacion encontrada.")
    public RoleAssignmentResource role(@AuthenticationPrincipal SpotgoUserPrincipal actor, @PathVariable UUID accountId) {
        return RoleAssignmentResource.from(administration.role(actor, accountId), administration.now());
    }
    @PutMapping("/{accountId}/role")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(tags = "Role assignments", summary = "Asignar o cambiar rol", description = "Solo SUPER_ADMIN. Crea o modifica la unica fila permitida por account_id UNIQUE. PARKING_ADMIN requiere un estacionamiento existente. Se comprueba la asignacion actual en cada peticion, incluso si el JWT conserva otro rol en sus claims.")
    @ApiResponse(responseCode = "200", description = "Asignacion creada o actualizada.")
    public RoleAssignmentResource assign(@AuthenticationPrincipal SpotgoUserPrincipal actor, @PathVariable UUID accountId,
            @Valid @RequestBody AssignRoleResource body, HttpServletRequest request) {
        return RoleAssignmentResource.from(administration.assign(actor, accountId, body.roleType(), body.tenantId(),
            body.validFrom(), body.validTo(), body.status(), request.getRemoteAddr()), administration.now());
    }
    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(tags = "Accounts", summary = "Cambiar mi contraseña", description = "Valida la contraseña actual. La nueva debe ser diferente y cumplir la politica. Revoca todas las sesiones, incluida esta; vuelve a iniciar sesion.")
    @ApiResponse(responseCode = "204", description = "Contraseña cambiada; sin cuerpo.", content = @Content)
    public void password(@AuthenticationPrincipal SpotgoUserPrincipal actor,
            @Valid @RequestBody UpdatePasswordResource body, HttpServletRequest request) {
        authentication.updatePassword(actor, body.currentPassword(), body.newPassword(), request.getRemoteAddr());
    }
}
