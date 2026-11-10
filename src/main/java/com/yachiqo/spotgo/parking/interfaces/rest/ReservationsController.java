package com.yachiqo.spotgo.parking.interfaces.rest;
import com.yachiqo.spotgo.parking.application.internal.commandservices.ParkingCommandServiceImpl;
import com.yachiqo.spotgo.parking.application.internal.queryservices.ParkingQueryServiceImpl;
import com.yachiqo.spotgo.parking.interfaces.rest.resources.ParkingResources.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.Content;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.time.Instant;

@RestController @RequestMapping(value="/api/v1/reservations",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Reservations",description="Reservations reference profile_id and vehicle_id. The backend calculates the amount and obtains the caller identity from the access token.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid data, UUID, JSON, date, or state transition.",content=@Content),
 @ApiResponse(responseCode="401",description="The access token or session is invalid.",content=@Content),
 @ApiResponse(responseCode="403",description="Insufficient role, another tenant, or another driver's resource.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicate resource, overlapping interval, expired hold, or incompatible state.",content=@Content)
})
public class ReservationsController {
 private final ParkingCommandServiceImpl commands; private final ParkingQueryServiceImpl queries;
 public ReservationsController(ParkingCommandServiceImpl commands,ParkingQueryServiceImpl queries){this.commands=commands;this.queries=queries;}

 @GetMapping @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="List reservations in my scope",description="DRIVER can view their own reservations; PARKING_ADMIN can view reservations for their tenant.")
 public List<ReservationView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor){return queries.reservations(actor).stream().map(ReservationView::of).toList();}
 @GetMapping("/{reservationId}") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Get an authorized reservation", description = "Returns the requested reservation if it belongs to the authenticated driver or the driver's tenant scope.")
 public ReservationView get(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID reservationId){return ReservationView.of(queries.reservation(actor,reservationId));}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('DRIVER')") @Operation(summary="Reserve a parking spot and create a temporary hold",description="Creates a PENDING_PAYMENT reservation with paymentId set to NULL. Validates an active vehicle owned by the caller, an operational spot, and a non-overlapping time interval. The base amount is calculated in PEN and rounded up. Subscription discounts are applied by Billing.")
 @ApiResponse(responseCode="201",description="Reservation is awaiting payment and the hold is active.")
 public ReservationView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody ReserveBody body){return ReservationView.of(commands.reserve(actor,body.spotId(),body.vehicleId(),body.startAt(),body.endAt()));}
 @PostMapping("/{reservationId}/confirm-payment") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')")
 @Operation(summary="Confirm reservation payment",description="Verifies that digital_payments is APPROVED for the RESERVATION operation, profile, and exact amount, and that the hold is still valid. The client cannot assert that payment succeeded.")
 @ApiResponse(responseCode="503",description="Billing does not have digital_payments available yet.",content=@Content)
 public ReservationView confirm(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID reservationId,@Valid @RequestBody PaymentBody body){return ReservationView.of(commands.confirm(actor,reservationId,body.paymentId()));}
 @PostMapping("/{reservationId}/cancel") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Cancel a reservation",description="Releases the reservation hold. It does not automatically request a Billing refund.")
 public ReservationView cancel(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID reservationId){return ReservationView.of(commands.cancel(actor,reservationId));}
 @PostMapping("/{reservationId}/no-show") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Record a reservation no-show",description="Available only after a confirmed reservation's time interval has ended.")
 public ReservationView noShow(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID reservationId){return ReservationView.of(commands.noShow(actor,reservationId));}

}
