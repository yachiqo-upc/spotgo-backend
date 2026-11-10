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

@RestController @RequestMapping(value="/api/v1/parking-sessions",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Parking sessions",description="The actual parking session for a reservation. It is distinct from authentication sessions, with one parking session per reservation.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid data, UUID, JSON, date, or state transition.",content=@Content),
 @ApiResponse(responseCode="401",description="The access token or session is invalid.",content=@Content),
 @ApiResponse(responseCode="403",description="Insufficient role, another tenant, or another driver's resource.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicate resource, overlapping interval, expired hold, or incompatible state.",content=@Content)
})
public class ParkingSessionsController {
 private final ParkingCommandServiceImpl commands; private final ParkingQueryServiceImpl queries;
 public ParkingSessionsController(ParkingCommandServiceImpl commands,ParkingQueryServiceImpl queries){this.commands=commands;this.queries=queries;}

 @GetMapping @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="List parking sessions in my scope", description = "Returns parking sessions visible to the authenticated driver or parking administrator.")
 public List<SessionView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor){return queries.sessions(actor).stream().map(SessionView::of).toList();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Start a reservation parking session",description="Requires a RESERVED booking within its time interval. Sets entryAt to the current time and prevents multiple open sessions for the same vehicle or parking spot.")
 @ApiResponse(responseCode="201",description="Parking session created.")
 public SessionView enter(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody EntryBody body){return SessionView.of(commands.enter(actor,body.reservationId()));}
 @PostMapping("/{sessionId}/exit") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="End a parking session",description="Sets exitAt and marks the reservation OVERSTAYED when the grace period is exceeded. Billing then records a separate additional charge and outstanding balance when applicable.")
 public SessionView exit(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID sessionId){return SessionView.of(commands.exit(actor,sessionId));}

}
