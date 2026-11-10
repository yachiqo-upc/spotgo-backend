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

@RestController @RequestMapping(value="/api/v1/occupancy-conflicts",produces="application/json")
@PreAuthorize("hasAnyRole('PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Occupancy conflicts",description="Incidentes respaldados por una lectura confiable. reservationId puede ser NULL; una falla de sensor se registra en salud.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="UUID, fecha, calidad o JSON invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Sesion no valida",content=@Content),
 @ApiResponse(responseCode="403",description="Rol o Tenant fuera de tu alcance",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Referencia, duplicado o estado incompatible; no se inventa ocupacion",content=@Content)
})
public class OccupancyConflictsController {
 private final MonitoringService service;
 public OccupancyConflictsController(MonitoringService service){this.service=service;}
 
 @GetMapping @Operation(summary="Listar conflictos de mi alcance")
 public List<ConflictView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam(required=false) UUID tenantId){return service.conflicts(actor,tenantId).stream().map(ConflictView::of).toList();}
 @GetMapping("/{conflictId}") @Operation(summary="Consultar conflicto")
 public ConflictView get(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID conflictId){return ConflictView.of(service.conflict(actor,conflictId));}
 @PostMapping("/{conflictId}/acknowledge") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Reconocer conflicto")
 public ConflictView acknowledge(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID conflictId){return ConflictView.of(service.updateConflict(actor,conflictId,false));}
 @PostMapping("/{conflictId}/resolve") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Resolver conflicto atendido",description="Cierra sus alertas. No modifica una lectura ni genera multas automaticamente.")
 public ConflictView resolve(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID conflictId){return ConflictView.of(service.updateConflict(actor,conflictId,true));}

}
