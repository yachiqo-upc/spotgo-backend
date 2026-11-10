package com.yachiqo.spotgo.monitoring.interfaces.rest;

import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.WeeklyTrend;
import com.yachiqo.spotgo.monitoring.domain.model.queries.GetWeeklyTrendsByParkingIdQuery;
import com.yachiqo.spotgo.monitoring.application.internal.queryservices.MonitoringQueryService;
import com.yachiqo.spotgo.monitoring.interfaces.rest.resources.WeeklyTrendResource;
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
@RequestMapping("/api/v1/weeklyTrends")
@PreAuthorize("hasRole('PARKING_ADMIN')")
@Tag(name = "WeeklyTrends", description = "Endpoints for reading weekly occupancy trend data")
public class WeeklyTrendsController {

    private final MonitoringQueryService monitoringQueryService;
    private final AuthorizationService authorizationService;

    public WeeklyTrendsController(MonitoringQueryService monitoringQueryService, AuthorizationService authorizationService) {
        this.monitoringQueryService = monitoringQueryService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    @Operation(summary = "Get weekly trends", description = "Returns weekly trend points, optionally filtered by parking ID.")
    @ApiResponse(responseCode = "200", description = "List of weekly trend points returned",
            content = @Content(schema = @Schema(implementation = WeeklyTrendResource.class)))
    public ResponseEntity<List<WeeklyTrendResource>> getWeeklyTrends(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                                                     @RequestParam(required = false) String parkingId) {
        var scopedParkingId = authorizationService.requireAdminParkingId(principal);
        if (parkingId != null && !parkingId.equals(scopedParkingId)) {
            throw new org.springframework.security.access.AccessDeniedException("Requested parking is outside authenticated scope");
        }
        List<WeeklyTrend> points = monitoringQueryService.handle(new GetWeeklyTrendsByParkingIdQuery(scopedParkingId));
        var resources = points.stream().map(this::toResource).toList();
        return ResponseEntity.ok(resources);
    }

    private WeeklyTrendResource toResource(WeeklyTrend point) {
        return new WeeklyTrendResource(point.getId(), point.getParkingId(), point.getDay(), point.getValue());
    }
}
