package com.yachiqo.spotgo.parking.interfaces.rest;

import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Parking;
import com.yachiqo.spotgo.parking.domain.model.commands.CreateParkingCommand;
import com.yachiqo.spotgo.parking.domain.model.commands.UpdateParkingCommand;
import com.yachiqo.spotgo.parking.domain.model.queries.GetAllParkingsQuery;
import com.yachiqo.spotgo.parking.domain.model.queries.GetParkingByIdQuery;
import com.yachiqo.spotgo.parking.application.internal.commandservices.ParkingCommandService;
import com.yachiqo.spotgo.parking.application.internal.queryservices.ParkingQueryService;
import com.yachiqo.spotgo.parking.interfaces.rest.resources.CreateParkingResource;
import com.yachiqo.spotgo.parking.interfaces.rest.resources.ParkingResource;
import com.yachiqo.spotgo.parking.interfaces.rest.resources.UpdateParkingResource;
import com.yachiqo.spotgo.shared.application.security.AuthorizationService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/parkings")
@Tag(name = "Parkings", description = "Endpoints for managing parking facilities")
public class ParkingsController {

    private final ParkingCommandService parkingCommandService;
    private final ParkingQueryService parkingQueryService;
    private final AuthorizationService authorizationService;

    public ParkingsController(ParkingCommandService parkingCommandService, ParkingQueryService parkingQueryService,
                              AuthorizationService authorizationService) {
        this.parkingCommandService = parkingCommandService;
        this.parkingQueryService = parkingQueryService;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('PARKING_ADMIN')")
    @Operation(summary = "Create a parking", description = "Creates a new parking facility.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Parking created successfully",
                    content = @Content(schema = @Schema(implementation = ParkingResource.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    public ResponseEntity<ParkingResource> createParking(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                                         @Valid @RequestBody CreateParkingResource resource) {
        var command = new CreateParkingCommand(
                principal.getUserId(), resource.name(), resource.address(), resource.city(),
                resource.totalSpaces(), resource.availableSpaces(), resource.totalFloors(),
                resource.averageOccupancy(), resource.occupancyTrendPercent(), resource.peakHour(),
                resource.totalRevenue(), resource.systemStatus(), resource.rating(), resource.pricePerHour(),
                resource.revenueTrendPercent(), resource.totalCapacity(), resource.efficiencyIndex());
        var parkingOptional = parkingCommandService.handle(command);
        if (parkingOptional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var createdParking = parkingOptional.get();
        var parkingResource = toResource(createdParking);
        return new ResponseEntity<>(parkingResource, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all parkings", description = "Returns a list of all parking facilities.")
    @ApiResponse(responseCode = "200", description = "List of parkings returned",
            content = @Content(schema = @Schema(implementation = ParkingResource.class)))
    public ResponseEntity<List<ParkingResource>> getAllParkings() {
        var parkings = parkingQueryService.handle(new GetAllParkingsQuery());
        var resources = parkings.stream()
                .map(this::toResource)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/{parkingId}")
    @Operation(summary = "Get parking by id", description = "Returns a parking facility by its identifier.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Parking returned successfully",
                    content = @Content(schema = @Schema(implementation = ParkingResource.class))),
            @ApiResponse(responseCode = "404", description = "Parking not found")
    })
    public ResponseEntity<ParkingResource> getParkingById(@PathVariable String parkingId) {
        var parking = parkingQueryService.handle(new GetParkingByIdQuery(parkingId));
        return parking.map(value -> ResponseEntity.ok(toResource(value)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{parkingId}")
    @PreAuthorize("hasRole('PARKING_ADMIN')")
    @Operation(summary = "Update parking", description = "Updates mutable parking fields used by the frontend.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rating updated successfully",
                    content = @Content(schema = @Schema(implementation = ParkingResource.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "404", description = "Parking not found")
    })
    public ResponseEntity<ParkingResource> updateParkingRating(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                                               @PathVariable String parkingId,
                                                               @RequestBody UpdateParkingResource resource) {
        authorizationService.requireParkingOwnership(principal, parkingId);
        var updatedParkingOpt = parkingCommandService.handle(new UpdateParkingCommand(
                parkingId,
                resource.totalSpaces(),
                resource.availableSpaces(),
                resource.totalFloors(),
                resource.city(),
                resource.rating(),
                resource.pricePerHour()));
        return updatedParkingOpt.map(parking -> ResponseEntity.ok(toResource(parking)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ParkingResource toResource(Parking parking) {
        return new ParkingResource(
                parking.getId(),
                parking.getAdminId(),
                parking.getName(),
                parking.getAddress(),
                parking.getCity(),
                parking.getTotalSpaces(),
                parking.getAvailableSpaces(),
                parking.getTotalFloors(),
                parking.getAverageOccupancy(),
                parking.getOccupancyTrendPercent(),
                parking.getPeakHour(),
                parking.getTotalRevenue(),
                parking.getSystemStatus(),
                parking.getRating(),
                parking.getPricePerHour(),
                parking.getRevenueTrendPercent(),
                parking.getTotalCapacity(),
                parking.getEfficiencyIndex()
        );
    }
}
