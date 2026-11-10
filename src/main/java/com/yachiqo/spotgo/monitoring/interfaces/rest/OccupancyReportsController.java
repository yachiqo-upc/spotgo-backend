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

@RestController @RequestMapping(value="/api/v1/occupancy-reports",produces="application/json")
@PreAuthorize("hasAnyRole('PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Occupancy reports",description="Reporte por zona obligatoria, calculado con snapshots de [inicio,fin). No guarda snapshotId.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="UUID, fecha, calidad o JSON invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Sesion no valida",content=@Content),
 @ApiResponse(responseCode="403",description="Rol o Tenant fuera de tu alcance",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Referencia, duplicado o estado incompatible; no se inventa ocupacion",content=@Content)
})
public class OccupancyReportsController {
 private final MonitoringService service;
 public OccupancyReportsController(MonitoringService service){this.service=service;}
 
 @GetMapping @Operation(summary="Consultar reportes de mi alcance")
 public List<ReportView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam(required=false) UUID tenantId){return service.reports(actor,tenantId).stream().map(ReportView::of).toList();}
 @PostMapping @PreAuthorize("hasRole('PARKING_ADMIN')") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Generar reporte de zona",description="Requiere muestras confiables. totals contiene maximo, media simple de muestras, cantidad de snapshots y alertas creadas en el intervalo; no es media ponderada por tiempo. Maximo 366 dias, sin fechas futuras.")
 public ReportView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody ReportBody b){return ReportView.of(service.report(actor,b.zoneId(),b.intervalStart(),b.intervalEnd()));}

}
