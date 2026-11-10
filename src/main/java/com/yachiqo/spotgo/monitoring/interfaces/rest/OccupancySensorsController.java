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

@RestController @RequestMapping(value="/api/v1/occupancy-sensors",produces="application/json")
@PreAuthorize("hasAnyRole('PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Occupancy sensors",description="One device per parking spot, identified by UUID, with at most one ACTIVE sensor per spot. The tenant subscription limits the number of active sensors.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid UUID, timestamp, quality, or JSON.",content=@Content),
 @ApiResponse(responseCode="401",description="Invalid session.",content=@Content),
 @ApiResponse(responseCode="403",description="The role or tenant is outside the caller's access scope.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Invalid reference, duplicate resource, or incompatible state; occupancy is not fabricated.",content=@Content)
})
public class OccupancySensorsController {
 private final MonitoringService service;
 public OccupancySensorsController(MonitoringService service){this.service=service;}
 
 @GetMapping @Operation(summary="List sensors in my scope", description = "Returns occupancy sensors visible to the authenticated caller within their tenant scope.")
 public List<SensorView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam(required=false) UUID tenantId){return service.sensors(actor,tenantId).stream().map(SensorView::of).toList();}
 @GetMapping("/{sensorId}") @Operation(summary="Get a sensor", description = "Returns the requested occupancy sensor when it belongs to the caller's authorized tenant.")
 public SensorView get(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID sensorId){return SensorView.of(service.sensor(actor,sensorId));}
 @PostMapping @PreAuthorize("hasRole('PARKING_ADMIN')") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Register a sensor with its first real reading",description="Derives the tenant and zone from the spot. The server sets lastSeenAt and receivedAt. installedAt must be earlier than the first recent reading. MQTT requires configuration.topic; the HTTP adapter receives readings but does not install a broker.")
 public SensorView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody SensorBody body){var r=body.firstReading();return SensorView.of(service.register(actor,body.spotId(),body.protocol(),body.configuration(),body.installedAt(),r.occupancyStatus(),r.occurredAt(),r.quality()));}
 @PostMapping("/{sensorId}/deactivate") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Deactivate a sensor and keep its history", description = "Deactivates the specified sensor while preserving its historical readings and health events.")
 public SensorView deactivate(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID sensorId){return SensorView.of(service.deactivate(actor,sensorId));}
 @GetMapping("/spots/{spotId}/occupancy") @Operation(summary="Get trusted physical occupancy",description="Returns AVAILABLE, OCCUPIED, or UNAVAILABLE. A spot is not reported as free without a sensor, a fresh reading, healthy sensor status, and an active subscription.")
 public MonitoringOccupancyService.Observation occupancy(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID spotId){return service.observation(actor,spotId);}

}
