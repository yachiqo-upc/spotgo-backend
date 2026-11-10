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
@Tag(name="Sensor health events",description="Problemas del dispositivo; una falla no demuestra ocupacion. recoveredAt es NULL durante el episodio.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="UUID, fecha, calidad o JSON invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Sesion no valida",content=@Content),
 @ApiResponse(responseCode="403",description="Rol o Tenant fuera de tu alcance",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Referencia, duplicado o estado incompatible; no se inventa ocupacion",content=@Content)
})
public class SensorHealthEventsController {
 private final MonitoringService service;
 public SensorHealthEventsController(MonitoringService service){this.service=service;}
 
 @GetMapping @Operation(summary="Consultar salud de un sensor")
 public List<HealthView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam UUID sensorId){return service.health(actor,sensorId).stream().map(HealthView::of).toList();}
 @GetMapping("/{healthEventId}") @Operation(summary="Consultar evento de salud")
 public HealthView get(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID healthEventId){return HealthView.of(service.healthEvent(actor,healthEventId));}
 @PostMapping @PreAuthorize("hasRole('PARKING_ADMIN')") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Registrar diagnostico de salud",description="Falla genera una alerta con healthEventId. HEALTHY recupera episodios abiertos; no inventa una lectura libre.")
 public HealthView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody HealthBody b){return HealthView.of(service.recordHealth(actor,b.sensorId(),b.health(),b.reason()));}
 @PostMapping("/{healthEventId}/recover") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Registrar recuperacion del episodio")
 public HealthView recover(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID healthEventId){return HealthView.of(service.recoverHealth(actor,healthEventId));}

}
