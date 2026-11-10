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

@RestController @RequestMapping(value="/api/v1/capacity-snapshots",produces="application/json")
@PreAuthorize("hasAnyRole('PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Capacity snapshots",description="Ocupacion calculada sobre espacios operativos. zoneId NULL resume todo el Tenant; no hay sensorId ni readingId.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="UUID, fecha, calidad o JSON invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Sesion no valida",content=@Content),
 @ApiResponse(responseCode="403",description="Rol o Tenant fuera de tu alcance",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Referencia, duplicado o estado incompatible; no se inventa ocupacion",content=@Content)
})
public class CapacitySnapshotsController {
 private final MonitoringService service;
 public CapacitySnapshotsController(MonitoringService service){this.service=service;}
 
 @GetMapping @Operation(summary="Consultar historial de capacidad")
 public List<SnapshotView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam(required=false) UUID tenantId){return service.snapshots(actor,tenantId).stream().map(SnapshotView::of).toList();}
 @PostMapping @PreAuthorize("hasRole('PARKING_ADMIN')") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Calcular snapshot confiable",description="Consulta ultima lectura por espacio. Si alguno es UNAVAILABLE, responde 409 en vez de publicarlo como libre. Cero espacios produce 0.00 sin dividir por cero. Se generan tambien al recibir lecturas confiables.")
 public SnapshotView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody SnapshotBody b){return SnapshotView.of(service.snapshot(actor,b.tenantId(),b.zoneId()));}

}
