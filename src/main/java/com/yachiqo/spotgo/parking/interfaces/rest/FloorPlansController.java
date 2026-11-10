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

@RestController @RequestMapping(value="/api/v1/floor-plans",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Floor plans",description="Base floor plans for a zone. fileReference points to an existing file; no file conversion is performed.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid data, UUID, JSON, date, or state transition.",content=@Content),
 @ApiResponse(responseCode="401",description="The access token or session is invalid.",content=@Content),
 @ApiResponse(responseCode="403",description="Insufficient role, another tenant, or another driver's resource.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicate resource, overlapping interval, expired hold, or incompatible state.",content=@Content)
})
public class FloorPlansController {
 private final ParkingCommandServiceImpl commands; private final ParkingQueryServiceImpl queries;
 public FloorPlansController(ParkingCommandServiceImpl commands,ParkingQueryServiceImpl queries){this.commands=commands;this.queries=queries;}

 @GetMapping @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="List floor plans in my zone", description = "Returns floor plans registered for the zone managed by the authenticated parking administrator.")
 public List<PlanView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam UUID zoneId){return queries.plans(actor,zoneId).stream().map(PlanView::of).toList();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Create a floor plan",description="Creates a DRAFT floor plan with publishedAt set to NULL. A version cannot be reused within the same zone.")
 @ApiResponse(responseCode="201",description="Floor plan created.")
 public PlanView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody PlanBody body){return PlanView.of(commands.createPlan(actor,body.zoneId(),body.version(),body.fileReference()));}
 @PostMapping("/{floorPlanId}/publish") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Publish a floor plan",description="Sets publishedAt automatically.")
 public PlanView publish(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID floorPlanId){return PlanView.of(commands.publishPlan(actor,floorPlanId,false));}
 @PostMapping("/{floorPlanId}/archive") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Archive a floor plan and its maps", description = "Archives the specified floor plan and its associated map versions.")
 public PlanView archive(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID floorPlanId){return PlanView.of(commands.publishPlan(actor,floorPlanId,true));}

}
