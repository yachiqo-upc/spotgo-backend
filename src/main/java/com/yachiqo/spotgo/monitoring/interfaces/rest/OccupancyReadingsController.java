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

@RestController @RequestMapping(value="/api/v1/occupancy-readings",produces="application/json")
@PreAuthorize("hasAnyRole('PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Occupancy readings",description="Sensor readings. The server calculates SHA-256 and receivedAt; duplicate messages do not create duplicate incidents.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid UUID, timestamp, quality, or JSON.",content=@Content),
 @ApiResponse(responseCode="401",description="Invalid session.",content=@Content),
 @ApiResponse(responseCode="403",description="The role or tenant is outside the caller's access scope.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Invalid reference, duplicate resource, or incompatible state; occupancy is not fabricated.",content=@Content)
})
public class OccupancyReadingsController {
 private final MonitoringService service;
 public OccupancyReadingsController(MonitoringService service){this.service=service;}
 
 @GetMapping @Operation(summary="View a sensor's reading history", description = "Returns readings for the specified sensor in chronological order, subject to tenant access checks.")
 public List<ReadingView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam UUID sensorId){return service.readings(actor,sensorId).stream().map(ReadingView::of).toList();}
 @PostMapping @PreAuthorize("hasRole('PARKING_ADMIN')") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Receive a sensor reading",description="Authenticated endpoint for tenant integrations; it is not a public webhook. Reading quality ranges from 0 to 100, with configurable minimum quality and freshness. Late readings do not replace newer observations or close incidents.")
 public ReadingView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody ReadingBody b){return ReadingView.of(service.receive(actor,b.sensorId(),b.occupancyStatus(),b.occurredAt(),b.quality()));}

}
