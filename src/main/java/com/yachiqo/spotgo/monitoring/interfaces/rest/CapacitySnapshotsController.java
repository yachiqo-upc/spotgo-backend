package com.yachiqo.spotgo.monitoring.interfaces.rest;
import com.yachiqo.spotgo.monitoring.application.*;
import com.yachiqo.spotgo.monitoring.interfaces.rest.resources.MonitoringResources.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.Content;
import java.util.*;

@RestController @RequestMapping(value="/api/v1/capacity-snapshots",produces="application/json")
@PreAuthorize("hasAnyRole('PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Capacity snapshots",description="Occupancy calculated from operational parking spots. A NULL zoneId summarizes the entire tenant; sensorId and readingId are not returned.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid UUID, timestamp, quality, or JSON.",content=@Content),
 @ApiResponse(responseCode="401",description="Invalid session.",content=@Content),
 @ApiResponse(responseCode="403",description="The role or tenant is outside the caller's access scope.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Invalid reference, duplicate resource, or incompatible state; occupancy is not fabricated.",content=@Content)
})
public class CapacitySnapshotsController {
 private final MonitoringService service;
 public CapacitySnapshotsController(MonitoringService service){this.service=service;}
 
 @GetMapping @Operation(summary="View capacity snapshot history", description = "Returns capacity snapshots for the tenant and optional zone available to the authenticated parking administrator.")
 public List<SnapshotView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam(required=false) UUID tenantId){return service.snapshots(actor,tenantId).stream().map(SnapshotView::of).toList();}
 @PostMapping @PreAuthorize("hasRole('PARKING_ADMIN')") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Create a trusted capacity snapshot",description="Uses the latest reading for each spot. Returns 409 if any spot is UNAVAILABLE instead of reporting it as free. An empty zone produces 0.00 without division by zero. Trusted readings also generate snapshots.")
 public SnapshotView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody SnapshotBody b){return SnapshotView.of(service.snapshot(actor,b.tenantId(),b.zoneId()));}

}
