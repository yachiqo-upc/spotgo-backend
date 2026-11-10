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

@RestController @RequestMapping(value="/api/v1/parking-spots",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Parking spots",description="Individual parking spots. operationalStatus does not represent physical occupancy.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid data, UUID, JSON, date, or state transition.",content=@Content),
 @ApiResponse(responseCode="401",description="The access token or session is invalid.",content=@Content),
 @ApiResponse(responseCode="403",description="Insufficient role, another tenant, or another driver's resource.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicate resource, overlapping interval, expired hold, or incompatible state.",content=@Content)
})
public class ParkingSpotsController {
 private final ParkingCommandServiceImpl commands; private final ParkingQueryServiceImpl queries;
 public ParkingSpotsController(ParkingCommandServiceImpl commands,ParkingQueryServiceImpl queries){this.commands=commands;this.queries=queries;}

 @GetMapping @Operation(summary="List parking spots in a zone", description = "Returns parking spots registered in the zone identified by zoneId.")
 public List<SpotView> list(@RequestParam UUID zoneId){return queries.spots(zoneId).stream().map(SpotView::of).toList();}
 @GetMapping("/availability") @Operation(summary="Check availability for a time interval",description="Calculates availability from operational status, reservations, holds, and open parking sessions. physicalOccupancy comes from Monitoring and can be AVAILABLE, OCCUPIED, or UNAVAILABLE. The current time requires AVAILABLE; a current observation does not guarantee future availability.")
 public List<ParkingQueryServiceImpl.Availability> availability(@RequestParam UUID zoneId,@RequestParam Instant startAt,@RequestParam Instant endAt) {
  return queries.availability(zoneId,startAt,endAt);
 }
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Create a parking spot",description="code must be unique within its zone. The default category is STANDARD; features is a JSON object containing spot attributes.")
 @ApiResponse(responseCode="201",description="Parking spot created.")
 public SpotView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam UUID zoneId,@Valid @RequestBody SpotBody body) {
  var created=commands.createSpotWithBilling(actor,zoneId,body.code(),body.spotType(),body.features());
  return SpotView.of(created.spot(),created.billingAdjustment());
 }
 @PutMapping("/{spotId}") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Update a parking spot",description="operationalStatus must be ACTIVE, INACTIVE, or MAINTENANCE. Occupancy status is not accepted here.")
 public SpotView update(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID spotId,@Valid @RequestBody SpotBody body) {
  if(body.operationalStatus()==null)throw new IllegalArgumentException("operationalStatus is required.");
  return SpotView.of(commands.updateSpot(actor,spotId,body.code(),body.spotType(),body.features(),body.operationalStatus()));
 }

}
