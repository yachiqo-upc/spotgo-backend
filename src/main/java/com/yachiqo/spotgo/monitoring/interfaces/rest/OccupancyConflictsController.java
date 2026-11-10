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

@RestController @RequestMapping(value="/api/v1/occupancy-conflicts",produces="application/json")
@PreAuthorize("hasAnyRole('PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Occupancy conflicts",description="Incidents backed by a trusted reading. reservationId may be NULL; sensor failures are recorded as health events.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid UUID, timestamp, quality, or JSON.",content=@Content),
 @ApiResponse(responseCode="401",description="Invalid session.",content=@Content),
 @ApiResponse(responseCode="403",description="The role or tenant is outside the caller's access scope.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Invalid reference, duplicate resource, or incompatible state; occupancy is not fabricated.",content=@Content)
})
public class OccupancyConflictsController {
 private final MonitoringService service;
 public OccupancyConflictsController(MonitoringService service){this.service=service;}
 
 @GetMapping @Operation(summary="List occupancy conflicts in my scope", description = "Returns occupancy conflicts within the caller's tenant or administrative scope.")
 public List<ConflictView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam(required=false) UUID tenantId){return service.conflicts(actor,tenantId).stream().map(ConflictView::of).toList();}
 @GetMapping("/{conflictId}") @Operation(summary="Get an occupancy conflict", description = "Returns one occupancy conflict when it belongs to the caller's authorized tenant.")
 public ConflictView get(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID conflictId){return ConflictView.of(service.conflict(actor,conflictId));}
 @PostMapping("/{conflictId}/acknowledge") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Acknowledge an occupancy conflict", description = "Marks the specified conflict as acknowledged for the caller's tenant.")
 public ConflictView acknowledge(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID conflictId){return ConflictView.of(service.updateConflict(actor,conflictId,false));}
 @PostMapping("/{conflictId}/resolve") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Resolve a handled occupancy conflict",description="Closes the incident's alerts. It does not modify a reading or automatically create a fine.")
 public ConflictView resolve(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID conflictId){return ConflictView.of(service.updateConflict(actor,conflictId,true));}

}
