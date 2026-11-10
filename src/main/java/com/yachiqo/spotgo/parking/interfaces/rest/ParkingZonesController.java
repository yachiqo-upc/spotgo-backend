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

@RestController @RequestMapping(value="/api/v1/parking-zones",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Parking zones",description="Divisiones del tenant: Piso1-A, Piso2-A, Indoor u Outdoor.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Datos, UUID, JSON, fechas o transicion invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Access token o sesion invalidos",content=@Content),
 @ApiResponse(responseCode="403",description="Rol insuficiente, otro tenant o recurso de otro conductor",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicado, intervalo superpuesto, bloqueo vencido o estado incompatible",content=@Content)
})
public class ParkingZonesController {
 private final ParkingCommandServiceImpl commands; private final ParkingQueryServiceImpl queries;
 public ParkingZonesController(ParkingCommandServiceImpl commands,ParkingQueryServiceImpl queries){this.commands=commands;this.queries=queries;}

 @GetMapping @Operation(summary="Listar zonas de un tenant")
 public List<ZoneView> list(@RequestParam UUID tenantId){return queries.zones(tenantId).stream().map(ZoneView::of).toList();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Crear zona en mi tenant")
 @ApiResponse(responseCode="201",description="Zona creada")
 public ZoneView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam UUID tenantId,@Valid @RequestBody ZoneBody body) {
  return ZoneView.of(commands.createZone(actor,tenantId,body.name(),body.location(),body.colorCode()));
 }
 @PutMapping("/{zoneId}") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Actualizar zona de mi tenant")
 public ZoneView update(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID zoneId,@Valid @RequestBody ZoneBody body) {
  if(body.status()==null)throw new IllegalArgumentException("status es obligatorio");
  return ZoneView.of(commands.updateZone(actor,zoneId,body.name(),body.location(),body.colorCode(),body.status()));
 }

}
