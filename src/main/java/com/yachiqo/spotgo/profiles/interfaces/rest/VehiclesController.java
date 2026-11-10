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
@Tag(name = "Vehicles", description = "Vehicles owned by the authenticated driver. The current IAM role is DRIVER. A profile can have multiple vehicles.")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "A valid accessToken and an active session are required.", content = @Content),
    @ApiResponse(responseCode = "400", description = "Invalid UUID, JSON, or status; make, model, or color cannot be empty.", content = @Content),
    @ApiResponse(responseCode = "403", description = "The role is not DRIVER, or the vehicle belongs to another account.", content = @Content),
    @ApiResponse(responseCode = "404", description = "The vehicle does not exist, or the caller has not created a profile yet.", content = @Content),
    @ApiResponse(responseCode = "409", description = "Duplicate plate or integrity conflict.", content = @Content),
    @ApiResponse(responseCode = "500", description = "Unexpected backend error. Check the server logs.", content = @Content)
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
    @Operation(summary = "List my vehicles", description = "Returns vehicles for the authenticated profile, including INACTIVE vehicles. clientId and profileId are not accepted.")
    @ApiResponse(responseCode = "200", description = "Resources owned by the authenticated account; the list may be empty.")
    @PreAuthorize("hasRole('DRIVER')")
    public List<VehicleResource> mine(@AuthenticationPrincipal SpotgoUserPrincipal actor) {
        var profile = profiles.driver(actor);
        return queries.handle(new GetVehiclesByProfileIdQuery(profile.getId())).stream()
            .map(VehicleResource::from).toList();
    }

    @PostMapping
    @Operation(summary = "Add a vehicle", description = "Associates the vehicle with the authenticated profile and generates its UUID and timestamps. The plate is normalized to uppercase and is optional. Do not send profileId.")
    @ApiResponse(responseCode = "201", description = "Vehicle created with ACTIVE status.")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<VehicleResource> create(@AuthenticationPrincipal SpotgoUserPrincipal actor,
                                                 @Valid @RequestBody CreateVehicleResource resource) {
        var profile = profiles.driver(actor);
        var vehicle = commands.handle(new CreateVehicleCommand(profile.getId(), resource.plate(),
            resource.make(), resource.model(), resource.color())).orElseThrow();
        return ResponseEntity.status(HttpStatus.CREATED).body(VehicleResource.from(vehicle));
    }

    @PatchMapping("/{vehicleId}")
    @Operation(summary = "Update my vehicle", description = "Send only the fields to change; omitted fields remain unchanged. An empty plate removes the plate. status accepts ACTIVE or INACTIVE.")
    @ApiResponse(responseCode = "200", description = "Vehicle updated.")
    @PreAuthorize("hasRole('DRIVER')")
    public VehicleResource update(@AuthenticationPrincipal SpotgoUserPrincipal actor,
                                   @Parameter(description = "UUID of the vehicle, not the profile.", example = "00000000-0000-4000-8000-000000000051") @PathVariable UUID vehicleId,
                                   @Valid @RequestBody UpdateVehicleResource resource) {
        var current = vehicles.findById(vehicleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found."));
        profiles.requireOwner(actor, current);
        return VehicleResource.from(commands.handle(new UpdateVehicleCommand(vehicleId, resource.plate(),
            resource.make(), resource.model(), resource.color(), resource.status())).orElseThrow());
    }

    @DeleteMapping("/{vehicleId}")
    @Operation(summary = "Deactivate my vehicle", description = "Sets status to INACTIVE without deleting the row. A successful 204 response has no body.")
    @ApiResponse(responseCode = "204", description = "Vehicle deactivated; no response body.", content = @Content)
    @PreAuthorize("hasRole('DRIVER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal SpotgoUserPrincipal actor,
                       @Parameter(description = "UUID of the caller's vehicle.", example = "00000000-0000-4000-8000-000000000051") @PathVariable UUID vehicleId) {
        var current = vehicles.findById(vehicleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found."));
        profiles.requireOwner(actor, current);
        // Preserve the vehicle for reservations that will reference it after Parking migration.
        commands.handle(new DeleteVehicleCommand(vehicleId));
    }
}
