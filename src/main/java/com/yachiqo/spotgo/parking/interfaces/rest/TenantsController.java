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

@RestController @RequestMapping(value="/api/v1/tenants",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Tenants",description="Cada tenant representa un negocio y un unico estacionamiento fisico.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Datos, UUID, JSON, fechas o transicion invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Access token o sesion invalidos",content=@Content),
 @ApiResponse(responseCode="403",description="Rol insuficiente, otro tenant o recurso de otro conductor",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicado, intervalo superpuesto, bloqueo vencido o estado incompatible",content=@Content)
})
public class TenantsController {
 private final ParkingCommandServiceImpl commands; private final ParkingQueryServiceImpl queries;
 public TenantsController(ParkingCommandServiceImpl commands,ParkingQueryServiceImpl queries){this.commands=commands;this.queries=queries;}

 @GetMapping @Operation(summary="Listar estacionamientos",description="Consulta autenticada de los tenants.")
 public List<TenantView> list(){return queries.tenants().stream().map(TenantView::of).toList();}
 @GetMapping("/{tenantId}") @Operation(summary="Consultar estacionamiento")
 public TenantView get(@PathVariable UUID tenantId){return TenantView.of(queries.tenant(tenantId));}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('SUPER_ADMIN')")
 @Operation(summary="Registrar tenant",description="Solo SUPER_ADMIN. Genera UUID y fechas. No crea una cuenta ni asigna un administrador automaticamente.")
 @ApiResponse(responseCode="201",description="Tenant creado")
 public TenantView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody TenantBody body) {
  return TenantView.of(commands.createTenant(actor,body.name(),body.address(),body.coordinates(),body.contactData(),body.rules()));
 }
 @PutMapping("/{tenantId}") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Actualizar mi estacionamiento",description="Incluye reglas tarifarias en PEN. status y datos completos obligatorios. Solo el administrador asignado al tenant.")
 public TenantView update(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID tenantId,@Valid @RequestBody TenantBody body) {
  if(body.status()==null)throw new IllegalArgumentException("status es obligatorio");
  return TenantView.of(commands.updateTenant(actor,tenantId,body.name(),body.address(),body.coordinates(),body.contactData(),body.rules(),body.status()));
 }

}
