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

@RestController @RequestMapping(value="/api/v1/digital-parking-maps",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Digital parking maps",description="Elementos JSON que la app renderiza a partir de un croquis; no crea espacios automaticamente.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Datos, UUID, JSON, fechas o transicion invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Access token o sesion invalidos",content=@Content),
 @ApiResponse(responseCode="403",description="Rol insuficiente, otro tenant o recurso de otro conductor",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicado, intervalo superpuesto, bloqueo vencido o estado incompatible",content=@Content)
})
public class DigitalParkingMapsController {
 private final ParkingCommandServiceImpl commands; private final ParkingQueryServiceImpl queries;
 public DigitalParkingMapsController(ParkingCommandServiceImpl commands,ParkingQueryServiceImpl queries){this.commands=commands;this.queries=queries;}

 @GetMapping @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Listar mapas de un croquis propio")
 public List<MapView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam UUID floorPlanId){return queries.maps(actor,floorPlanId).stream().map(MapView::of).toList();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Registrar version de mapa",description="DRAFT. Los elementos SPOT requieren spot_id de la zona del croquis; los demas datos son del renderizador.")
 @ApiResponse(responseCode="201",description="Mapa registrado")
 public MapView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody MapBody body){return MapView.of(commands.createMap(actor,body.floorPlanId(),body.version(),body.elements()));}
 @PostMapping("/{mapId}/publish") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Publicar mapa",description="Archiva las demas versiones de ese croquis: solo una queda publicada.")
 public MapView publish(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID mapId){return MapView.of(commands.publishMap(actor,mapId,false));}
 @PostMapping("/{mapId}/archive") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Archivar mapa")
 public MapView archive(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID mapId){return MapView.of(commands.publishMap(actor,mapId,true));}

}
