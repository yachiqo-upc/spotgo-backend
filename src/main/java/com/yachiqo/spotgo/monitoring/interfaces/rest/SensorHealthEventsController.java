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

@RestController @RequestMapping(value="/api/v1/sensor-health-events",produces="application/json")
@PreAuthorize("hasAnyRole('PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Sensor health events",description="Device problems; a failure does not prove that a parking spot is occupied. recoveredAt is NULL while the health event remains open.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid UUID, timestamp, quality, or JSON.",content=@Content),
 @ApiResponse(responseCode="401",description="Invalid session.",content=@Content),
 @ApiResponse(responseCode="403",description="The role or tenant is outside the caller's access scope.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Invalid reference, duplicate resource, or incompatible state; occupancy is not fabricated.",content=@Content)
})
public class SensorHealthEventsController {
 private final MonitoringService service;
 public SensorHealthEventsController(MonitoringService service){this.service=service;}
 
 @GetMapping @Operation(summary="View sensor health events", description = "Returns sensor health events visible to the authenticated caller within their tenant scope.")
 public List<HealthView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam UUID sensorId){return service.health(actor,sensorId).stream().map(HealthView::of).toList();}
 @GetMapping("/{healthEventId}") @Operation(summary="Get a sensor health event", description = "Returns the requested sensor health event after checking tenant access.")
 public HealthView get(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID healthEventId){return HealthView.of(service.healthEvent(actor,healthEventId));}
 @PostMapping @PreAuthorize("hasRole('PARKING_ADMIN')") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Record a sensor health diagnosis",description="A failure creates an alert linked by healthEventId. HEALTHY resolves open episodes but does not fabricate a free-space reading.")
 public HealthView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody HealthBody b){return HealthView.of(service.recordHealth(actor,b.sensorId(),b.health(),b.reason()));}
 @PostMapping("/{healthEventId}/recover") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Record recovery for a health event", description = "Marks the specified health event as recovered and closes its active health episode.")
 public HealthView recover(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID healthEventId){return HealthView.of(service.recoverHealth(actor,healthEventId));}

}
