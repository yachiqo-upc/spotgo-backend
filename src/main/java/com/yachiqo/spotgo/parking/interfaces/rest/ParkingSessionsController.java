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

@RestController @RequestMapping(value="/api/v1/parking-sessions",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Parking sessions",description="Estadia efectiva de una reserva; distinta de sessions de autenticacion. Una sesion por reserva.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Datos, UUID, JSON, fechas o transicion invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Access token o sesion invalidos",content=@Content),
 @ApiResponse(responseCode="403",description="Rol insuficiente, otro tenant o recurso de otro conductor",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicado, intervalo superpuesto, bloqueo vencido o estado incompatible",content=@Content)
})
public class ParkingSessionsController {
 private final ParkingCommandServiceImpl commands; private final ParkingQueryServiceImpl queries;
 public ParkingSessionsController(ParkingCommandServiceImpl commands,ParkingQueryServiceImpl queries){this.commands=commands;this.queries=queries;}

 @GetMapping @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Listar estadias de mi alcance")
 public List<SessionView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor){return queries.sessions(actor).stream().map(SessionView::of).toList();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Registrar entrada de una reserva",description="Solo reserva RESERVED dentro del intervalo. Registra entryAt actual; no permite dos sesiones abiertas para un vehiculo o espacio.")
 @ApiResponse(responseCode="201",description="Sesion de estacionamiento creada")
 public SessionView enter(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody EntryBody body){return SessionView.of(commands.enter(actor,body.reservationId()));}
 @PostMapping("/{sessionId}/exit") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Registrar salida",description="Captura exitAt. Marca reserva OVERSTAYED si supera tolerancia; no genera cargos ficticios mientras Billing este pendiente.")
 public SessionView exit(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID sessionId){return SessionView.of(commands.exit(actor,sessionId));}

}
