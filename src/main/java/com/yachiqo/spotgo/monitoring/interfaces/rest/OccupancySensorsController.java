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
@Tag(name="Occupancy sensors",description="Dispositivo por espacio, UUID y un solo ACTIVE por spot. La suscripcion empresarial limita los sensores activos.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="UUID, fecha, calidad o JSON invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Sesion no valida",content=@Content),
 @ApiResponse(responseCode="403",description="Rol o Tenant fuera de tu alcance",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Referencia, duplicado o estado incompatible; no se inventa ocupacion",content=@Content)
})
public class OccupancySensorsController {
 private final MonitoringService service;
 public OccupancySensorsController(MonitoringService service){this.service=service;}
 
 @GetMapping @Operation(summary="Listar sensores de mi alcance")
 public List<SensorView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam(required=false) UUID tenantId){return service.sensors(actor,tenantId).stream().map(SensorView::of).toList();}
 @GetMapping("/{sensorId}") @Operation(summary="Consultar sensor")
 public SensorView get(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID sensorId){return SensorView.of(service.sensor(actor,sensorId));}
 @PostMapping @PreAuthorize("hasRole('PARKING_ADMIN')") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Registrar sensor con primera medicion real",description="Deriva Tenant y zona del spot. lastSeenAt y receivedAt se capturan en el servidor. installedAt debe ser anterior a la primera lectura reciente. MQTT requiere configuration.topic; el adaptador HTTP recibe las mediciones, no instala un broker.")
 public SensorView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody SensorBody body){var r=body.firstReading();return SensorView.of(service.register(actor,body.spotId(),body.protocol(),body.configuration(),body.installedAt(),r.occupancyStatus(),r.occurredAt(),r.quality()));}
 @PostMapping("/{sensorId}/deactivate") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Desactivar sensor conservando historial")
 public SensorView deactivate(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID sensorId){return SensorView.of(service.deactivate(actor,sensorId));}
 @GetMapping("/spots/{spotId}/occupancy") @Operation(summary="Consultar ocupacion fisica confiable",description="AVAILABLE, OCCUPIED o UNAVAILABLE. Sin sensor, lectura fresca, salud o suscripcion vigente no se afirma que este libre.")
 public MonitoringOccupancyService.Observation occupancy(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID spotId){return service.observation(actor,spotId);}

}
