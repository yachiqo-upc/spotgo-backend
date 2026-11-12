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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 private final com.yachiqo.spotgo.parking.application.ReservationReceiptService receipts;
 public ReservationsController(ParkingCommandServiceImpl commands,ParkingQueryServiceImpl queries,com.yachiqo.spotgo.parking.application.ReservationReceiptService receipts){this.commands=commands;this.queries=queries;this.receipts=receipts;}

 @GetMapping @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="List reservations in my scope",description="DRIVER can view their own reservations; PARKING_ADMIN can view reservations for their tenant.")
 public List<ReservationView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor){return queries.reservations(actor).stream().map(ReservationView::of).toList();}
 @GetMapping("/{reservationId}") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Get an authorized reservation", description = "Returns the requested reservation if it belongs to the authenticated driver or the driver's tenant scope.")
  public ReservationView get(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID reservationId){return ReservationView.of(queries.reservation(actor,reservationId));}
  @GetMapping("/{reservationId}/changes") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
  @Operation(summary="List the change history for an authorized reservation",description="Includes pending price increases and scheduled refund reductions so the driver can resume an interrupted payment flow.")
  public List<ReservationChangeView> changes(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID reservationId){
   return queries.reservationChanges(actor,reservationId).stream().map(ReservationChangeView::of).toList();
  }
  @GetMapping(value="/{reservationId}/receipt",produces=MediaType.APPLICATION_PDF_VALUE) @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
  @Operation(summary="Download an informational reservation receipt",description="Returns a non-fiscal PDF after the reservation payment has been approved.")
  public ResponseEntity<byte[]> receipt(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID reservationId){
   return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
    .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=spotgo-reservation-"+reservationId+".pdf")
    .body(receipts.pdf(actor,reservationId));
  }
  @PostMapping("/{reservationId}/receipt/email") @ResponseStatus(HttpStatus.OK) @PreAuthorize("hasRole('DRIVER')")
  @Operation(summary="Email an informational receipt to the account address",description="Sends the non-fiscal PDF to the email address registered on the driver's SpotGo account.")
  public Map<String,Object> emailReceipt(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID reservationId){
   receipts.email(actor,reservationId);return Map.of("status","SENT","informational",true);
  }
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('DRIVER')") @Operation(summary="Reserve a parking spot and create a temporary hold",description="Creates a PENDING_PAYMENT reservation with paymentId set to NULL. Validates an active vehicle owned by the caller, an operational spot, and a non-overlapping time interval. The base amount is calculated in PEN and rounded up. Subscription discounts are applied by Billing.")
 @ApiResponse(responseCode="201",description="Reservation is awaiting payment and the hold is active.")
  public ReservationView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody ReserveBody body){return ReservationView.of(commands.reserve(actor,body.spotId(),body.vehicleId(),body.startAt(),body.endAt()));}
  @PostMapping("/{reservationId}/reschedule") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('DRIVER')")
  @Operation(summary="Reschedule a confirmed reservation",description="A confirmed reservation can be edited until five minutes before it starts. Increases require an approved payment; reductions are refunded five minutes after the latest edit.")
  public ReservationChangeView reschedule(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID reservationId,@Valid @RequestBody RescheduleBody body){
   return ReservationChangeView.of(commands.reschedule(actor,reservationId,body.startAt(),body.endAt()));
  }
  @PostMapping("/{reservationId}/extend") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('DRIVER')")
  @Operation(summary="Request a paid extension",description="Creates a linked reservation segment starting at the end of the current segment. The driver must pay before the original active reservation ends.")
  public ReservationView extend(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID reservationId,@Valid @RequestBody ExtendBody body){
   return ReservationView.of(commands.extend(actor,reservationId,body.endAt(),body.spotId()));
  }
 @PostMapping("/{reservationId}/confirm-payment") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')")
 @Operation(summary="Confirm reservation payment",description="Verifies that digital_payments is APPROVED for the RESERVATION operation, profile, and exact amount, and that the hold is still valid. The client cannot assert that payment succeeded.")
 @ApiResponse(responseCode="503",description="Billing does not have digital_payments available yet.",content=@Content)
 public ReservationView confirm(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID reservationId,@Valid @RequestBody PaymentBody body){return ReservationView.of(commands.confirm(actor,reservationId,body.paymentId()));}
 @PostMapping("/{reservationId}/cancel") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Cancel a reservation",description="Releases the reservation hold. It does not automatically request a Billing refund.")
 public ReservationView cancel(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID reservationId){return ReservationView.of(commands.cancel(actor,reservationId));}
 @PostMapping("/{reservationId}/no-show") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Record a reservation no-show",description="Available only after a confirmed reservation's time interval has ended.")
 public ReservationView noShow(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID reservationId){return ReservationView.of(commands.noShow(actor,reservationId));}

}
