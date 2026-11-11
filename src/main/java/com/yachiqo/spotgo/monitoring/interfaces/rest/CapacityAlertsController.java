package com.yachiqo.spotgo.monitoring.interfaces.rest;

import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.monitoring.application.MonitoringService;
import com.yachiqo.spotgo.monitoring.interfaces.rest.resources.MonitoringResources.CapacityAlertView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/capacity-alerts", produces = "application/json")
@PreAuthorize("hasAnyRole('PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name = "Capacity alerts", description = "Critical alerts raised when trusted occupancy exceeds 95% of operational parking spots.")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "A valid access token and active session are required."),
        @ApiResponse(responseCode = "403", description = "The caller cannot access or modify this tenant's alerts."),
        @ApiResponse(responseCode = "404", description = "The requested tenant or alert was not found."),
        @ApiResponse(responseCode = "500", description = "Unexpected backend error.")
})
public class CapacityAlertsController {
    private final MonitoringService service;

    public CapacityAlertsController(MonitoringService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List capacity alerts in my scope", description = "Returns capacity alerts for the authenticated tenant. SUPER_ADMIN may provide tenantId to filter the results.")
    @ApiResponse(responseCode = "200", description = "Capacity alerts visible to the authenticated caller.")
    public List<CapacityAlertView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,
                                         @RequestParam(required = false) UUID tenantId) {
        return service.capacityAlerts(actor, tenantId).stream().map(CapacityAlertView::of).toList();
    }

    @PostMapping("/{alertId}/acknowledge")
    @PreAuthorize("hasRole('PARKING_ADMIN')")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Acknowledge a capacity alert", description = "Records that the tenant administrator has reviewed the critical high-capacity alert. The alert is resolved automatically when trusted occupancy returns to 95% or below.")
    @ApiResponse(responseCode = "200", description = "The capacity alert was acknowledged.")
    public CapacityAlertView acknowledge(@AuthenticationPrincipal SpotgoUserPrincipal actor,
                                         @PathVariable UUID alertId) {
        return CapacityAlertView.of(service.acknowledgeCapacityAlert(actor, alertId));
    }
}
