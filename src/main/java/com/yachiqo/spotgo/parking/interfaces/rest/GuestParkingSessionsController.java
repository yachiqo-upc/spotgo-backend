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

@RestController @RequestMapping(value="/api/v1/guest-parking-sessions",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Guest parking sessions",description="Guest parking sessions without an account: manually entered plate, session, and CASH/POS in-person payment. No profile or digital payment is created.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid data, UUID, JSON, date, or state transition.",content=@Content),
 @ApiResponse(responseCode="401",description="The access token or session is invalid.",content=@Content),
 @ApiResponse(responseCode="403",description="Insufficient role, another tenant, or another driver's resource.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicate resource, overlapping interval, expired hold, or incompatible state.",content=@Content)
})
public class GuestParkingSessionsController {
 private final ParkingCommandServiceImpl commands; private final ParkingQueryServiceImpl queries;
 public GuestParkingSessionsController(ParkingCommandServiceImpl commands,ParkingQueryServiceImpl queries){this.commands=commands;this.queries=queries;}

 @GetMapping @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="List guest parking sessions for my tenant", description = "Returns guest parking sessions recorded for the authenticated parking administrator's tenant.")
 public List<GuestView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor){return queries.guests(actor).stream().map(GuestView::of).toList();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Start a guest parking session",description="Creates an OPEN session with the current entry time. Checks current availability; the exit time remains NULL until recorded.")
 @ApiResponse(responseCode="201",description="Guest session created.")
 public GuestView enter(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody GuestBody body){return GuestView.of(commands.guestEnter(actor,body.spotId(),body.manualPlate(),body.physicalPaymentMethod()));}
 @PostMapping("/{guestSessionId}/exit") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="End a guest session and record an in-person payment",description="Calculates the fee from the tenant's tariff. The session remains PENDING_REVIEW until payment is confirmed, then changes to CLOSED.")
 public GuestView exit(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID guestSessionId,@Valid @RequestBody GuestExitBody body){return GuestView.of(commands.guestExit(actor,guestSessionId,body.physicalPaymentConfirmed()));}
 @PostMapping("/{guestSessionId}/confirm-payment") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Confirm a pending in-person payment",description="Available only after the session has an exit time.")
 public GuestView paid(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID guestSessionId){return GuestView.of(commands.guestPaid(actor,guestSessionId));}

}
