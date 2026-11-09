package com.yachiqo.spotgo.profiles.interfaces.rest;

import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.profiles.application.internal.ProfilesManagementService;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.DriverProfileType;
import com.yachiqo.spotgo.profiles.interfaces.rest.resources.DriverClassificationResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/driver-classifications", produces = "application/json")
@Tag(name = "Driver classifications", description = "TAXI, EMPLOYEE o PRIVATE por estacionamiento. No son roles de acceso. assignedBy identifica el perfil del administrador.")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "UUID o JSON invalido, profileType desconocido o perfil objetivo que no es conductor.", content = @Content),
    @ApiResponse(responseCode = "403", description = "Falta un token valido, el rol no permite la accion o el administrador no es propietario del estacionamiento.", content = @Content),
    @ApiResponse(responseCode = "404", description = "No existe el perfil propio/objetivo, la cuenta del conductor o el estacionamiento.", content = @Content),
    @ApiResponse(responseCode = "409", description = "Conflicto de integridad o cuenta antigua que requiere migracion a UUID.", content = @Content),
    @ApiResponse(responseCode = "500", description = "Error inesperado del backend. Revisar la consola del servidor.", content = @Content)
})
public class DriverClassificationsController {
    private final ProfilesManagementService service;
    public DriverClassificationsController(ProfilesManagementService service) { this.service = service; }
    public record AssignClassificationResource(
        @Schema(description = "Clasificacion en este tenant. Omitir el campo o enviar null asigna PRIVATE.", example = "EMPLOYEE") DriverProfileType profileType) {}

    @PutMapping("/tenants/{tenantId}/profiles/{profileId}")
    @Operation(summary = "Asignar o cambiar clasificacion", description = "Solo ADMIN propietario. Usa el UUID del perfil del conductor y el UUID del estacionamiento actual como tenantId. El backend obtiene assignedBy del token; no lo envies. Crea o actualiza una sola fila por perfil y tenant.")
    @ApiResponse(responseCode = "200", description = "Clasificacion creada o actualizada. Con {} se asigna PRIVATE.")
    @PreAuthorize("hasRole('ADMIN')")
    public DriverClassificationResource assign(@AuthenticationPrincipal SpotgoUserPrincipal actor,
            @Parameter(description = "UUID del estacionamiento administrado", example = "00000000-0000-4000-8000-000000000021") @PathVariable UUID tenantId,
            @Parameter(description = "UUID del perfil del conductor, no de su cuenta", example = "00000000-0000-4000-8000-000000000012") @PathVariable UUID profileId,
            @RequestBody AssignClassificationResource resource) {
        return DriverClassificationResource.from(service.assign(actor, tenantId, profileId, resource.profileType()));
    }
    @GetMapping("/tenants/{tenantId}")
    @Operation(summary = "Listar clasificaciones de mi estacionamiento", description = "Solo ADMIN propietario del estacionamiento indicado.")
    @ApiResponse(responseCode = "200", description = "Clasificaciones explicitas de este tenant; la lista puede estar vacia.")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DriverClassificationResource> tenant(@AuthenticationPrincipal SpotgoUserPrincipal actor,
            @Parameter(description = "UUID del estacionamiento administrado", example = "00000000-0000-4000-8000-000000000021") @PathVariable UUID tenantId) {
        return service.tenantClassifications(actor, tenantId).stream().map(DriverClassificationResource::from).toList();
    }
    @GetMapping("/me")
    @Operation(summary = "Consultar mis clasificaciones", description = "Solo CLIENT. Devuelve las clasificaciones guardadas para el conductor autenticado en los distintos tenants.")
    @ApiResponse(responseCode = "200", description = "Clasificaciones propias; la lista puede estar vacia.")
    @PreAuthorize("hasRole('CLIENT')")
    public List<DriverClassificationResource> mine(@AuthenticationPrincipal SpotgoUserPrincipal actor) {
        return service.myClassifications(actor).stream().map(DriverClassificationResource::from).toList();
    }
}
