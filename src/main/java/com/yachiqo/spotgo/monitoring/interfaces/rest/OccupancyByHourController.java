package com.yachiqo.spotgo.monitoring.interfaces.rest;

import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.OccupancyByHour;
import com.yachiqo.spotgo.monitoring.domain.model.queries.GetOccupancyByHourByParkingIdQuery;
import com.yachiqo.spotgo.monitoring.application.internal.queryservices.MonitoringQueryService;
import com.yachiqo.spotgo.monitoring.interfaces.rest.resources.OccupancyByHourResource;
import com.yachiqo.spotgo.shared.application.security.AuthorizationService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/occupancyByHour")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "OccupancyByHour", description = "Endpoints for reading hourly occupancy data")
public class OccupancyByHourController {

    private final MonitoringQueryService monitoringQueryService;
    private final AuthorizationService authorizationService;

    public OccupancyByHourController(MonitoringQueryService monitoringQueryService, AuthorizationService authorizationService) {
        this.monitoringQueryService = monitoringQueryService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    @Operation(summary = "Get occupancy by hour", description = "Returns hourly occupancy points, optionally filtered by parking ID.")
    @ApiResponse(responseCode = "200", description = "List of occupancy points returned",
            content = @Content(schema = @Schema(implementation = OccupancyByHourResource.class)))
    public ResponseEntity<List<OccupancyByHourResource>> getOccupancyByHour(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                                                             @RequestParam(required = false) String parkingId) {
        var scopedParkingId = authorizationService.requireAdminParkingId(principal);
        if (parkingId != null && !parkingId.equals(scopedParkingId)) {
            throw new org.springframework.security.access.AccessDeniedException("Requested parking is outside authenticated scope");
        }
        List<OccupancyByHour> points = monitoringQueryService.handle(new GetOccupancyByHourByParkingIdQuery(scopedParkingId));
        var resources = points.stream().map(this::toResource).toList();
        return ResponseEntity.ok(resources);
    }

    private OccupancyByHourResource toResource(OccupancyByHour point) {
        return new OccupancyByHourResource(point.getId(), point.getParkingId(), point.getHour(), point.getIntensity() / 100.0);
    }
}
