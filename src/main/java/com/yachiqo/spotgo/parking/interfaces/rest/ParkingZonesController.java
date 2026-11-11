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

@RestController @RequestMapping(value="/api/v1/parking-zones",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Parking zones",description="Tenant parking zones, such as Floor1-A, Floor2-A, Indoor, or Outdoor.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid data, UUID, JSON, date, or state transition.",content=@Content),
 @ApiResponse(responseCode="401",description="The access token or session is invalid.",content=@Content),
 @ApiResponse(responseCode="403",description="Insufficient role, another tenant, or another driver's resource.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicate resource, overlapping interval, expired hold, or incompatible state.",content=@Content)
})
public class ParkingZonesController {
 private final ParkingCommandServiceImpl commands; private final ParkingQueryServiceImpl queries;
 public ParkingZonesController(ParkingCommandServiceImpl commands,ParkingQueryServiceImpl queries){this.commands=commands;this.queries=queries;}

 @GetMapping @Operation(summary="List accessible zones for a tenant", description = "Returns zones for the requested tenant. DRIVER sees only zones allowing the driver's tenant-specific profile type (PRIVATE when no explicit classification exists); PARKING_ADMIN is limited to the assigned tenant; SUPER_ADMIN can inspect any tenant.")
 public List<ZoneView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam UUID tenantId){return queries.zones(actor,tenantId).stream().map(ZoneView::of).toList();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Create a parking zone in my tenant", description = "Creates a zone for the authenticated parking administrator's tenant. allowedProfileTypes controls which driver classifications may access the zone; omit it to allow TAXI, EMPLOYEE, and PRIVATE.")
 @ApiResponse(responseCode="201",description="Parking zone created.")
 public ZoneView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam UUID tenantId,@Valid @RequestBody ZoneBody body) {
  return ZoneView.of(commands.createZone(actor,tenantId,body.name(),body.location(),body.colorCode(),body.allowedProfileTypes()));
 }
 @PutMapping("/{zoneId}") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Update a parking zone in my tenant", description = "Updates the specified zone after verifying tenant ownership. Include allowedProfileTypes to replace the permitted driver classifications; omit it to keep the current list.")
 public ZoneView update(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID zoneId,@Valid @RequestBody ZoneBody body) {
  if(body.status()==null)throw new IllegalArgumentException("status is required.");
  return ZoneView.of(commands.updateZone(actor,zoneId,body.name(),body.location(),body.colorCode(),body.status(),body.allowedProfileTypes()));
 }

}
