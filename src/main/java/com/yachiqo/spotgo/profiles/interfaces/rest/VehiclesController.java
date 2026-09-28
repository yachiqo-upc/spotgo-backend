package com.yachiqo.spotgo.profiles.interfaces.rest;

import com.yachiqo.spotgo.profiles.application.ProfilesCommandService;
import com.yachiqo.spotgo.profiles.application.ProfilesQueryService;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Vehicle;
import com.yachiqo.spotgo.profiles.domain.model.commands.CreateVehicleCommand;
import com.yachiqo.spotgo.profiles.domain.model.commands.DeleteVehicleCommand;
import com.yachiqo.spotgo.profiles.domain.model.commands.UpdateVehicleCommand;
import com.yachiqo.spotgo.profiles.domain.model.queries.GetAllVehiclesQuery;
import com.yachiqo.spotgo.profiles.domain.model.queries.GetVehiclesByClientIdQuery;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.VehicleRepository;
import com.yachiqo.spotgo.profiles.interfaces.rest.resources.CreateVehicleResource;
import com.yachiqo.spotgo.profiles.interfaces.rest.resources.UpdateVehicleResource;
import com.yachiqo.spotgo.profiles.interfaces.rest.resources.VehicleResource;
import com.yachiqo.spotgo.shared.application.security.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles")
@Tag(name = "Vehicles", description = "Endpoints for managing client vehicles")
public class VehiclesController {

    private final ProfilesCommandService profilesCommandService;
    private final ProfilesQueryService profilesQueryService;
    private final VehicleRepository vehicleRepository;
    private final AuthorizationService authorizationService;

    public VehiclesController(ProfilesCommandService profilesCommandService, ProfilesQueryService profilesQueryService,
                              VehicleRepository vehicleRepository, AuthorizationService authorizationService) {
        this.profilesCommandService = profilesCommandService;
        this.profilesQueryService = profilesQueryService;
        this.vehicleRepository = vehicleRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CLIENT')")
    @Operation(summary = "Get vehicles", description = "Returns all vehicles, optionally filtered by client identifier.")
    @ApiResponse(responseCode = "200", description = "Vehicles returned successfully",
            content = @Content(schema = @Schema(implementation = VehicleResource.class)))
    public ResponseEntity<List<VehicleResource>> getVehicles(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                                              @RequestParam(required = false) String clientId) {
        if (authorizationService.isAdmin(principal)) {
            var vehicles = clientId == null || clientId.isBlank()
                    ? profilesQueryService.handle(new GetAllVehiclesQuery())
                    : profilesQueryService.handle(new GetVehiclesByClientIdQuery(clientId));
            return ResponseEntity.ok(vehicles.stream().map(this::toResource).toList());
        }
        if (clientId != null && !clientId.equals(principal.getUserId())) {
            throw new AccessDeniedException("Requested client is outside authenticated scope");
        }
        var vehicles = profilesQueryService.handle(new GetVehiclesByClientIdQuery(principal.getUserId()));
        return ResponseEntity.ok(vehicles.stream().map(this::toResource).toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Create vehicle", description = "Creates a new vehicle for a client.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Vehicle created successfully",
                    content = @Content(schema = @Schema(implementation = VehicleResource.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
    })
    public ResponseEntity<VehicleResource> createVehicle(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                                          @Valid @RequestBody CreateVehicleResource resource) {
        if (!vehicleRepository.findAllByClientId(principal.getUserId()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        var vehicle = profilesCommandService.handle(new CreateVehicleCommand(
                principal.getUserId(), resource.licensePlate(), resource.vehicleType(), resource.brand(), resource.model()));
        return vehicle.map(value -> ResponseEntity.status(HttpStatus.CREATED).body(toResource(value)))
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    @PatchMapping("/{vehicleId}")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Update vehicle", description = "Updates mutable vehicle fields.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vehicle updated successfully",
                    content = @Content(schema = @Schema(implementation = VehicleResource.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found", content = @Content)
    })
    public ResponseEntity<VehicleResource> updateVehicle(
            @AuthenticationPrincipal SpotgoUserPrincipal principal,
            @PathVariable @Parameter(description = "Vehicle identifier") String vehicleId,
            @Valid @RequestBody UpdateVehicleResource resource) {
        var currentVehicle = vehicleRepository.findById(vehicleId).orElse(null);
        if (currentVehicle == null) {
            return ResponseEntity.notFound().build();
        }
        authorizationService.requireVehicleOwner(principal, currentVehicle);
        var vehicle = profilesCommandService.handle(new UpdateVehicleCommand(
                vehicleId, resource.licensePlate(), resource.vehicleType(), resource.brand(), resource.model()));
        return vehicle.map(value -> ResponseEntity.ok(toResource(value)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{vehicleId}")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Delete vehicle", description = "Deletes a vehicle by identifier.")
    @ApiResponse(responseCode = "204", description = "Vehicle deleted successfully")
    public ResponseEntity<Void> deleteVehicle(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                              @PathVariable String vehicleId) {
        var currentVehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found"));
        authorizationService.requireVehicleOwner(principal, currentVehicle);
        profilesCommandService.handle(new DeleteVehicleCommand(vehicleId));
        return ResponseEntity.noContent().build();
    }

    private VehicleResource toResource(Vehicle vehicle) {
        return new VehicleResource(vehicle.getId(), vehicle.getClientId(), vehicle.getLicensePlate(), vehicle.getVehicleType(), vehicle.getBrand(), vehicle.getModel());
    }
}
