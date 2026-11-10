package com.yachiqo.spotgo.parking.interfaces.rest;

import com.yachiqo.spotgo.parking.domain.model.aggregates.Reservation;
import com.yachiqo.spotgo.parking.domain.model.commands.ReserveSpotCommand;
import com.yachiqo.spotgo.parking.domain.model.commands.UpdateReservationCommand;
import com.yachiqo.spotgo.parking.domain.model.queries.GetReservationsByClientIdQuery;
import com.yachiqo.spotgo.parking.domain.model.queries.GetReservationsByParkingIdQuery;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationStatus;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.parking.application.internal.commandservices.ParkingCommandService;
import com.yachiqo.spotgo.parking.application.internal.queryservices.ParkingQueryService;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationRepository;
import com.yachiqo.spotgo.parking.interfaces.rest.resources.CreateReservationResource;
import com.yachiqo.spotgo.parking.interfaces.rest.resources.ReservationResource;
import com.yachiqo.spotgo.parking.interfaces.rest.resources.UpdateReservationResource;
import com.yachiqo.spotgo.shared.application.security.AuthorizationService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reservations", description = "Endpoints for managing parking spot reservations")
public class ReservationsController {

    private static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");

    private final ParkingCommandService parkingCommandService;
    private final ParkingQueryService parkingQueryService;
    private final ReservationRepository reservationRepository;
    private final AuthorizationService authorizationService;

    public ReservationsController(ParkingCommandService parkingCommandService, ParkingQueryService parkingQueryService,
                                  ReservationRepository reservationRepository, AuthorizationService authorizationService) {
        this.parkingCommandService = parkingCommandService;
        this.parkingQueryService = parkingQueryService;
        this.reservationRepository = reservationRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Reserve a spot", description = "Creates a new parking spot reservation.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Reservation created successfully",
                    content = @Content(schema = @Schema(implementation = ReservationResource.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    public ResponseEntity<ReservationResource> reserveSpot(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                                           @Valid @RequestBody CreateReservationResource resource) {
        var command = new ReserveSpotCommand(
                principal.getUserId(),
                resource.parkingId(),
                resource.spot(),
                toLimaLocalDateTime(resource.startDate()),
                toLimaLocalDateTime(resource.endDate()),
                null,
                null,
                null
        );
        var reservationOptional = parkingCommandService.handle(command);
        if (reservationOptional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        var createdReservation = reservationOptional.get();
        return new ResponseEntity<>(toResource(createdReservation), HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PARKING_ADMIN','DRIVER')")
    @Operation(summary = "Get all reservations", description = "Returns a list of reservations, optionally filtered by parking ID.")
    @ApiResponse(responseCode = "200", description = "List of reservations returned",
            content = @Content(schema = @Schema(implementation = ReservationResource.class)))
    public ResponseEntity<List<ReservationResource>> getAllReservations(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                                                        @RequestParam(required = false) String parkingId,
                                                                        @RequestParam(required = false) String clientId) {
        List<Reservation> reservations;
        if (authorizationService.isAdmin(principal)) {
            var scopedParkingId = authorizationService.requireAdminParkingId(principal);
            if (parkingId != null && !parkingId.equals(scopedParkingId)) {
                throw new AccessDeniedException("Requested parking is outside authenticated scope");
            }
            reservations = parkingQueryService.handle(new GetReservationsByParkingIdQuery(scopedParkingId));
        } else {
            if (clientId != null && !clientId.equals(principal.getUserId())) {
                throw new AccessDeniedException("Requested client is outside authenticated scope");
            }
            reservations = parkingQueryService.handle(new GetReservationsByClientIdQuery(principal.getUserId()));
        }
        var resources = reservations.stream()
                .map(this::toResource)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @PatchMapping("/{reservationId}")
    @PreAuthorize("hasAnyRole('PARKING_ADMIN','DRIVER')")
    public ResponseEntity<ReservationResource> updateReservation(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                                                 @PathVariable String reservationId,
                                                                 @RequestBody UpdateReservationResource resource) {
        var currentReservation = reservationRepository.findById(reservationId).orElse(null);
        if (currentReservation == null) {
            return ResponseEntity.notFound().build();
        }
        authorizationService.requireReservationAccess(principal, currentReservation);
        if (authorizationService.isClient(principal) && (resource.amount() != null || resource.baseAmount() != null)) {
            throw new AccessDeniedException("Clients cannot change reservation pricing");
        }
        ReservationStatus status = resource.status() == null ? null : ReservationStatus.fromDisplayName(resource.status());
        var reservation = parkingCommandService.handle(new UpdateReservationCommand(
                reservationId,
                resource.endDate() == null ? null : toLimaLocalDateTime(resource.endDate()),
                authorizationService.isAdmin(principal) ? resource.amount() : null,
                authorizationService.isAdmin(principal) ? resource.baseAmount() : null,
                resource.rating(),
                status
        ));
        return reservation.map(value -> ResponseEntity.ok(toResource(value)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ReservationResource toResource(Reservation reservation) {
        return new ReservationResource(
                reservation.getId(),
                reservation.getClientId(),
                reservation.getParkingId(),
                reservation.getCode(),
                reservation.getSpot(),
                toLimaOffsetDateTime(reservation.getStartDate()),
                toLimaOffsetDateTime(reservation.getEndDate()),
                reservation.getStatus().name().toLowerCase(),
                reservation.getAmount(),
                reservation.getBaseAmount(),
                reservation.getRating()
        );
    }

    private LocalDateTime toLimaLocalDateTime(OffsetDateTime value) {
        return value.atZoneSameInstant(LIMA_ZONE).toLocalDateTime();
    }

    private OffsetDateTime toLimaOffsetDateTime(LocalDateTime value) {
        return value.atZone(LIMA_ZONE).toOffsetDateTime();
    }
}
