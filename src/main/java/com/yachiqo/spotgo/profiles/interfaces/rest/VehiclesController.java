package com.yachiqo.spotgo.profiles.interfaces.rest;

import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.profiles.application.ProfilesCommandService;
import com.yachiqo.spotgo.profiles.application.ProfilesQueryService;
import com.yachiqo.spotgo.profiles.application.internal.ProfilesManagementService;
import com.yachiqo.spotgo.profiles.domain.model.commands.*;
import com.yachiqo.spotgo.profiles.domain.model.queries.GetVehiclesByProfileIdQuery;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.VehicleRepository;
import com.yachiqo.spotgo.profiles.interfaces.rest.resources.*;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.Content;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/vehicles", produces = "application/json")
@Tag(name = "Vehicles", description = "Vehiculos del conductor autenticado. El rol del IAM actual se llama DRIVER. Un perfil puede tener varios vehiculos.")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Requiere accessToken valido y sesion habilitada.", content = @Content),
    @ApiResponse(responseCode = "400", description = "UUID, JSON o estado invalido; marca, modelo o color vacios.", content = @Content),
    @ApiResponse(responseCode = "403", description = "Rol distinto de DRIVER o vehiculo de otra cuenta.", content = @Content),
    @ApiResponse(responseCode = "404", description = "No existe el vehiculo o todavia no creaste tu perfil.", content = @Content),
    @ApiResponse(responseCode = "409", description = "Placa duplicada o conflicto de integridad.", content = @Content),
    @ApiResponse(responseCode = "500", description = "Error inesperado del backend. Revisar la consola del servidor.", content = @Content)
})
public class VehiclesController {
    private final ProfilesCommandService commands;
    private final ProfilesQueryService queries;
    private final VehicleRepository vehicles;
    private final ProfilesManagementService profiles;
    public VehiclesController(ProfilesCommandService commands, ProfilesQueryService queries,
                              VehicleRepository vehicles, ProfilesManagementService profiles) {
        this.commands = commands;
        this.queries = queries;
        this.vehicles = vehicles;
        this.profiles = profiles;
    }

    @GetMapping
    @Operation(summary = "Listar mis vehiculos", description = "Devuelve solo los vehiculos del perfil autenticado, incluidos los INACTIVE. No recibe clientId ni profileId.")
    @ApiResponse(responseCode = "200", description = "Lista propia; puede estar vacia.")
    @PreAuthorize("hasRole('DRIVER')")
    public List<VehicleResource> mine(@AuthenticationPrincipal SpotgoUserPrincipal actor) {
        var profile = profiles.driver(actor);
        return queries.handle(new GetVehiclesByProfileIdQuery(profile.getId())).stream()
            .map(VehicleResource::from).toList();
    }

    @PostMapping
    @Operation(summary = "Agregar un vehiculo", description = "Asocia el vehiculo al perfil del token y genera su UUID y fechas. La placa se normaliza a mayusculas; puede omitirse. No envies profileId.")
    @ApiResponse(responseCode = "201", description = "Vehiculo creado con estado ACTIVE.")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<VehicleResource> create(@AuthenticationPrincipal SpotgoUserPrincipal actor,
                                                 @Valid @RequestBody CreateVehicleResource resource) {
        var profile = profiles.driver(actor);
        var vehicle = commands.handle(new CreateVehicleCommand(profile.getId(), resource.plate(),
            resource.make(), resource.model(), resource.color())).orElseThrow();
        return ResponseEntity.status(HttpStatus.CREATED).body(VehicleResource.from(vehicle));
    }

    @PatchMapping("/{vehicleId}")
    @Operation(summary = "Actualizar mi vehiculo", description = "Envia solo los campos que deseas cambiar. Los omitidos se conservan. plate vacio elimina la placa. status admite ACTIVE o INACTIVE.")
    @ApiResponse(responseCode = "200", description = "Vehiculo propio actualizado.")
    @PreAuthorize("hasRole('DRIVER')")
    public VehicleResource update(@AuthenticationPrincipal SpotgoUserPrincipal actor,
                                   @Parameter(description = "UUID del vehiculo, no del perfil", example = "00000000-0000-4000-8000-000000000051") @PathVariable UUID vehicleId,
                                   @Valid @RequestBody UpdateVehicleResource resource) {
        var current = vehicles.findById(vehicleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehiculo inexistente"));
        profiles.requireOwner(actor, current);
        return VehicleResource.from(commands.handle(new UpdateVehicleCommand(vehicleId, resource.plate(),
            resource.make(), resource.model(), resource.color(), resource.status())).orElseThrow());
    }

    @DeleteMapping("/{vehicleId}")
    @Operation(summary = "Desactivar mi vehiculo", description = "Cambia status a INACTIVE; no elimina la fila. Una respuesta 204 es exitosa y no tiene cuerpo.")
    @ApiResponse(responseCode = "204", description = "Vehiculo desactivado; respuesta sin contenido.", content = @Content)
    @PreAuthorize("hasRole('DRIVER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal SpotgoUserPrincipal actor,
                       @Parameter(description = "UUID del vehiculo propio", example = "00000000-0000-4000-8000-000000000051") @PathVariable UUID vehicleId) {
        var current = vehicles.findById(vehicleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehiculo inexistente"));
        profiles.requireOwner(actor, current);
        // Preserve the vehicle for reservations that will reference it after Parking migration.
        commands.handle(new DeleteVehicleCommand(vehicleId));
    }
}
