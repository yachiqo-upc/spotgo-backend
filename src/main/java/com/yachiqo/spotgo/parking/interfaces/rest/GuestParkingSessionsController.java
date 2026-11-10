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

@RestController @RequestMapping(value="/api/v1/guest-parking-sessions",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Guest parking sessions",description="Invitados sin cuenta: placa manual, estadia y pago fisico CASH/POS. No crea profiles ni pagos digitales.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Datos, UUID, JSON, fechas o transicion invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Access token o sesion invalidos",content=@Content),
 @ApiResponse(responseCode="403",description="Rol insuficiente, otro tenant o recurso de otro conductor",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicado, intervalo superpuesto, bloqueo vencido o estado incompatible",content=@Content)
})
public class GuestParkingSessionsController {
 private final ParkingCommandServiceImpl commands; private final ParkingQueryServiceImpl queries;
 public GuestParkingSessionsController(ParkingCommandServiceImpl commands,ParkingQueryServiceImpl queries){this.commands=commands;this.queries=queries;}

 @GetMapping @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Listar invitados de mi tenant")
 public List<GuestView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor){return queries.guests(actor).stream().map(GuestView::of).toList();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Registrar entrada de invitado",description="Genera entrada actual y OPEN. Verifica disponibilidad presente; la salida es NULL hasta registrar salida.")
 @ApiResponse(responseCode="201",description="Invitado registrado")
 public GuestView enter(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody GuestBody body){return GuestView.of(commands.guestEnter(actor,body.spotId(),body.manualPlate(),body.physicalPaymentMethod()));}
 @PostMapping("/{guestSessionId}/exit") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Registrar salida y pago fisico",description="Calcula importe por tarifa. Si no se confirma pago queda PENDING_REVIEW; si se confirma queda CLOSED.")
 public GuestView exit(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID guestSessionId,@Valid @RequestBody GuestExitBody body){return GuestView.of(commands.guestExit(actor,guestSessionId,body.physicalPaymentConfirmed()));}
 @PostMapping("/{guestSessionId}/confirm-payment") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Confirmar pago fisico pendiente",description="Solo para una estadia que ya tiene salida registrada.")
 public GuestView paid(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID guestSessionId){return GuestView.of(commands.guestPaid(actor,guestSessionId));}

}
