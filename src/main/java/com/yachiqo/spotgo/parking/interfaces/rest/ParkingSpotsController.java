package com.yachiqo.spotgo.parking.interfaces.rest;
import com.yachiqo.spotgo.parking.application.internal.commandservices.ParkingCommandServiceImpl;
import com.yachiqo.spotgo.parking.application.internal.queryservices.ParkingQueryServiceImpl;
import com.yachiqo.spotgo.parking.interfaces.rest.resources.ParkingResources.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.Content;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.time.Instant;

@RestController @RequestMapping(value="/api/v1/parking-spots",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Parking spots",description="Espacios individuales. operationalStatus no representa ocupacion fisica.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Datos, UUID, JSON, fechas o transicion invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Access token o sesion invalidos",content=@Content),
 @ApiResponse(responseCode="403",description="Rol insuficiente, otro tenant o recurso de otro conductor",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicado, intervalo superpuesto, bloqueo vencido o estado incompatible",content=@Content)
})
public class ParkingSpotsController {
 private final ParkingCommandServiceImpl commands; private final ParkingQueryServiceImpl queries;
 public ParkingSpotsController(ParkingCommandServiceImpl commands,ParkingQueryServiceImpl queries){this.commands=commands;this.queries=queries;}

 @GetMapping @Operation(summary="Listar espacios de una zona")
 public List<SpotView> list(@RequestParam UUID zoneId){return queries.spots(zoneId).stream().map(SpotView::of).toList();}
 @GetMapping("/availability") @Operation(summary="Consultar disponibilidad por intervalo",description="Proyeccion calculada de estado operativo, reservas, bloqueos y estadias abiertas. physicalOccupancy es UNKNOWN hasta integrar Monitoring; no se inventan lecturas de sensores.")
 public List<ParkingQueryServiceImpl.Availability> availability(@RequestParam UUID zoneId,@RequestParam Instant startAt,@RequestParam Instant endAt) {
  return queries.availability(zoneId,startAt,endAt);
 }
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Crear espacio",description="code es unico dentro de su zona. Categoria abierta como STANDARD; features es un objeto JSON de caracteristicas.")
 @ApiResponse(responseCode="201",description="Espacio creado")
 public SpotView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam UUID zoneId,@Valid @RequestBody SpotBody body) {
  return SpotView.of(commands.createSpot(actor,zoneId,body.code(),body.spotType(),body.features()));
 }
 @PutMapping("/{spotId}") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Actualizar espacio",description="operationalStatus debe ser ACTIVE, INACTIVE o MAINTENANCE. No recibe ocupado/libre.")
 public SpotView update(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID spotId,@Valid @RequestBody SpotBody body) {
  if(body.operationalStatus()==null)throw new IllegalArgumentException("operationalStatus es obligatorio");
  return SpotView.of(commands.updateSpot(actor,spotId,body.code(),body.spotType(),body.features(),body.operationalStatus()));
 }

}
