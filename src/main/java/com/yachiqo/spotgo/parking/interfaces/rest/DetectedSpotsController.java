package com.yachiqo.spotgo.parking.interfaces.rest;

import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.monitoring.application.EmployeeSpotAssignmentService;
import com.yachiqo.spotgo.parking.domain.model.aggregates.DetectedSpot;
import com.yachiqo.spotgo.parking.domain.model.commands.UpdateSpotStatusCommand;
import com.yachiqo.spotgo.parking.domain.model.queries.GetAllDetectedSpotsQuery;
import com.yachiqo.spotgo.parking.domain.model.queries.GetDetectedSpotsByParkingIdQuery;
import com.yachiqo.spotgo.parking.domain.model.queries.GetSpotsByBlueprintIdQuery;
import com.yachiqo.spotgo.parking.domain.model.commands.CreateDetectedSpotCommand;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.SpotStatus;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.Employee;
import com.yachiqo.spotgo.parking.application.internal.commandservices.ParkingCommandService;
import com.yachiqo.spotgo.parking.application.internal.queryservices.ParkingQueryService;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.DetectedSpotRepository;
import com.yachiqo.spotgo.parking.interfaces.rest.resources.DetectedSpotResource;
import com.yachiqo.spotgo.parking.interfaces.rest.resources.CreateDetectedSpotResource;
import com.yachiqo.spotgo.shared.application.security.AuthorizationService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/detectedSpots")
@Tag(name = "DetectedSpots", description = "Endpoints for managing detected parking spots")
public class DetectedSpotsController {

    private static final Logger log = LoggerFactory.getLogger(DetectedSpotsController.class);

    private final ParkingCommandService parkingCommandService;
    private final ParkingQueryService parkingQueryService;
    private final DetectedSpotRepository detectedSpotRepository;
    private final AuthorizationService authorizationService;
    private final EmployeeSpotAssignmentService employeeSpotAssignmentService;
    private final Clock clock;

    public DetectedSpotsController(ParkingCommandService parkingCommandService, ParkingQueryService parkingQueryService,
                                   DetectedSpotRepository detectedSpotRepository, AuthorizationService authorizationService,
                                   EmployeeSpotAssignmentService employeeSpotAssignmentService,
                                   Clock clock) {
        this.parkingCommandService = parkingCommandService;
        this.parkingQueryService = parkingQueryService;
        this.detectedSpotRepository = detectedSpotRepository;
        this.authorizationService = authorizationService;
        this.employeeSpotAssignmentService = employeeSpotAssignmentService;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "Get all detected spots", description = "Returns a list of detected parking spots, optionally filtered by parking ID or blueprint ID.")
    @ApiResponse(responseCode = "200", description = "List of detected spots returned",
            content = @Content(schema = @Schema(implementation = DetectedSpotResource.class)))
    public ResponseEntity<List<DetectedSpotResource>> getAllDetectedSpots(
            @AuthenticationPrincipal SpotgoUserPrincipal principal,
            @RequestParam(required = false) String parkingId,
            @RequestParam(required = false) String blueprintId) {
        List<DetectedSpot> spots;
        String parkingScope = parkingId;
        if (authorizationService.isAdmin(principal)) {
            var scopedParkingId = authorizationService.requireAdminParkingId(principal);
            if (parkingId != null && !parkingId.equals(scopedParkingId)) {
                throw new org.springframework.security.access.AccessDeniedException("Requested parking is outside authenticated scope");
            }
            parkingScope = scopedParkingId;
            if (blueprintId != null) {
                spots = parkingQueryService.handle(new GetSpotsByBlueprintIdQuery(blueprintId));
            } else {
                spots = parkingQueryService.handle(new GetDetectedSpotsByParkingIdQuery(scopedParkingId));
            }
        } else if (parkingId != null) {
            spots = parkingQueryService.handle(new GetDetectedSpotsByParkingIdQuery(parkingId));
        } else if (blueprintId != null) {
            spots = parkingQueryService.handle(new GetSpotsByBlueprintIdQuery(blueprintId));
            parkingScope = spots.isEmpty() ? null : spots.getFirst().getParkingId();
        } else {
            spots = parkingQueryService.handle(new GetAllDetectedSpotsQuery());
        }
        var reservedEmployees = parkingScope == null
                ? java.util.Map.<String, Employee>of()
                : employeeSpotAssignmentService.getReservedEmployeesBySpot(parkingScope, java.time.LocalDateTime.now(clock));
        var resources = spots.stream()
                .map(spot -> toResource(spot, reservedEmployees.get(toSpotCode(spot))))
                .toList();
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/blueprint/{blueprintId}")
    @Operation(summary = "Get spots by blueprint ID", description = "Returns all detected spots for a specific blueprint.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Spots returned",
                    content = @Content(schema = @Schema(implementation = DetectedSpotResource.class))),
            @ApiResponse(responseCode = "404", description = "Blueprint not found")
    })
    public ResponseEntity<List<DetectedSpotResource>> getSpotsByBlueprintId(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                                                            @PathVariable String blueprintId) {
        var spots = parkingQueryService.handle(new GetSpotsByBlueprintIdQuery(blueprintId));
        var parkingId = spots.isEmpty() ? null : spots.getFirst().getParkingId();
        if (authorizationService.isAdmin(principal) && parkingId != null) {
            authorizationService.requireParkingOwnership(principal, parkingId);
        }
        var reservedEmployees = parkingId == null
                ? java.util.Map.<String, Employee>of()
                : employeeSpotAssignmentService.getReservedEmployeesBySpot(parkingId, java.time.LocalDateTime.now(clock));
        var resources = spots.stream()
                .map(spot -> toResource(spot, reservedEmployees.get(toSpotCode(spot))))
                .toList();
        return ResponseEntity.ok(resources);
    }

    @PatchMapping("/{spotId}/status")
    @Operation(summary = "Update spot status", description = "Updates the status of a detected parking spot.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid status value")
    })
    public ResponseEntity<Void> updateSpotStatus(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                                 @PathVariable String spotId, @RequestParam String status) {
        var adminParkingId = authorizationService.requireAdminParkingId(principal);
        var currentSpot = detectedSpotRepository.findById(spotId).orElse(null);
        if (currentSpot == null) {
            log.warn("updateSpotStatus: spot not found, spotId={}", spotId);
            return ResponseEntity.badRequest().build();
        }
        if (!adminParkingId.equals(currentSpot.getParkingId())) {
            log.warn("updateSpotStatus: parking mismatch, adminParkingId={}, spotParkingId={}", adminParkingId, currentSpot.getParkingId());
            return ResponseEntity.badRequest().build();
        }
        var spotStatus = SpotStatus.fromDisplayName(status);
        var command = new UpdateSpotStatusCommand(spotId, spotStatus);
        var updatedSpotOptional = parkingCommandService.handle(command);
        if (updatedSpotOptional.isEmpty()) {
            log.warn("updateSpotStatus: command returned empty, spotId={}, status={}", spotId, status);
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping
    @PreAuthorize("hasRole('PARKING_ADMIN')")
    @Operation(summary = "Create a detected spot", description = "Creates a new detected parking spot on a blueprint.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Detected spot created successfully",
                    content = @Content(schema = @Schema(implementation = DetectedSpotResource.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    public ResponseEntity<DetectedSpotResource> createDetectedSpot(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                                                   @Valid @RequestBody CreateDetectedSpotResource resource) {
        authorizationService.requireParkingOwnership(principal, resource.parkingId());
        var status = SpotStatus.fromDisplayName(resource.status());
        var command = new CreateDetectedSpotCommand(
                resource.code(), resource.blueprintId(), resource.parkingId(),
                resource.row(), resource.col(),
                resource.xPct(), resource.yPct(), resource.wPct(), resource.hPct(),
                status);
        var spotOptional = parkingCommandService.handle(command);
        if (spotOptional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var createdSpot = spotOptional.get();
        return new ResponseEntity<>(toResource(createdSpot, null), HttpStatus.CREATED);
    }

    private DetectedSpotResource toResource(DetectedSpot spot, Employee assignedEmployee) {
        return new DetectedSpotResource(
                spot.getId(),
                spot.getCode(),
                spot.getBlueprintId(),
                spot.getParkingId(),
                spot.getRow(),
                spot.getCol(),
                spot.getXPct(),
                spot.getYPct(),
                spot.getWPct(),
                spot.getHPct(),
                assignedEmployee == null ? null : assignedEmployee.getId(),
                assignedEmployee == null ? null : assignedEmployee.getFirstName() + " " + assignedEmployee.getLastName(),
                spot.getStatus().getDisplayName()
        );
    }

    private String toSpotCode(DetectedSpot spot) {
        if (spot.getRow() == null || spot.getCol() == null) {
            return null;
        }
        return "%s%d".formatted(String.valueOf((char) ('A' + spot.getRow())), spot.getCol() + 1);
    }
}
