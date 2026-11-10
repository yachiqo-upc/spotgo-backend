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

@RestController @RequestMapping(value="/api/v1/floor-plans",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Floor plans",description="Croquis base por zona. fileReference referencia un archivo existente; no se convierte automaticamente.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Datos, UUID, JSON, fechas o transicion invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Access token o sesion invalidos",content=@Content),
 @ApiResponse(responseCode="403",description="Rol insuficiente, otro tenant o recurso de otro conductor",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicado, intervalo superpuesto, bloqueo vencido o estado incompatible",content=@Content)
})
public class FloorPlansController {
 private final ParkingCommandServiceImpl commands; private final ParkingQueryServiceImpl queries;
 public FloorPlansController(ParkingCommandServiceImpl commands,ParkingQueryServiceImpl queries){this.commands=commands;this.queries=queries;}

 @GetMapping @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Listar croquis de mi zona")
 public List<PlanView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam UUID zoneId){return queries.plans(actor,zoneId).stream().map(PlanView::of).toList();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Registrar croquis",description="Se crea DRAFT con publishedAt NULL. La version no se repite en una misma zona.")
 @ApiResponse(responseCode="201",description="Croquis registrado")
 public PlanView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody PlanBody body){return PlanView.of(commands.createPlan(actor,body.zoneId(),body.version(),body.fileReference()));}
 @PostMapping("/{floorPlanId}/publish") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Publicar croquis",description="Captura publishedAt automaticamente.")
 public PlanView publish(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID floorPlanId){return PlanView.of(commands.publishPlan(actor,floorPlanId,false));}
 @PostMapping("/{floorPlanId}/archive") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Archivar croquis y sus mapas")
 public PlanView archive(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID floorPlanId){return PlanView.of(commands.publishPlan(actor,floorPlanId,true));}

}
