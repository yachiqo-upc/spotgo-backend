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

@RestController @RequestMapping(value="/api/v1/occupancy-reports",produces="application/json")
@PreAuthorize("hasAnyRole('PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Occupancy reports",description="Report for a required zone, calculated from snapshots in the [start, end) interval. snapshotId is not stored.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid UUID, timestamp, quality, or JSON.",content=@Content),
 @ApiResponse(responseCode="401",description="Invalid session.",content=@Content),
 @ApiResponse(responseCode="403",description="The role or tenant is outside the caller's access scope.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Invalid reference, duplicate resource, or incompatible state; occupancy is not fabricated.",content=@Content)
})
public class OccupancyReportsController {
 private final MonitoringService service;
 public OccupancyReportsController(MonitoringService service){this.service=service;}
 
 @GetMapping @Operation(summary="View reports in my scope", description = "Returns occupancy reports available to the authenticated caller within their tenant scope.")
 public List<ReportView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam(required=false) UUID tenantId){return service.reports(actor,tenantId).stream().map(ReportView::of).toList();}
 @PostMapping @PreAuthorize("hasRole('PARKING_ADMIN')") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Generate a zone report",description="Requires trusted samples. totals contains the maximum, the arithmetic mean of samples, the number of snapshots, and alerts created in the interval; the mean is not time-weighted. The interval cannot exceed 366 days or include future dates.")
 public ReportView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody ReportBody b){return ReportView.of(service.report(actor,b.zoneId(),b.intervalStart(),b.intervalEnd()));}

}
