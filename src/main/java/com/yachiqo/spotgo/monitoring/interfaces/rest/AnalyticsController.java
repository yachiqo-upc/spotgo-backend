package com.yachiqo.spotgo.monitoring.interfaces.rest;

import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.monitoring.application.AnalyticsService;
import com.yachiqo.spotgo.monitoring.interfaces.rest.resources.AnalyticsResource;
import com.yachiqo.spotgo.shared.application.security.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Analytics", description = "Aggregated analytics for the authenticated admin parking")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AuthorizationService authorizationService;

    public AnalyticsController(AnalyticsService analyticsService, AuthorizationService authorizationService) {
        this.analyticsService = analyticsService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    @Operation(summary = "Get analytics", description = "Returns aggregated analytics for the authenticated admin parking and requested period.")
    @ApiResponse(responseCode = "200", description = "Analytics returned successfully",
            content = @Content(schema = @Schema(implementation = AnalyticsResource.class)))
    public ResponseEntity<AnalyticsResource> getAnalytics(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                                          @RequestParam(defaultValue = "today") String period,
                                                          @RequestParam(required = false) LocalDate from,
                                                          @RequestParam(required = false) LocalDate to) {
        var parkingId = authorizationService.requireAdminParkingId(principal);
        return ResponseEntity.ok(analyticsService.getAnalytics(parkingId, period, from, to));
    }
}
