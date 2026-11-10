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

@RestController @RequestMapping(value="/api/v1/reservations",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Reservations",description="Reservas por profile_id y vehicle_id. El backend calcula el importe y obtiene la identidad del token.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Datos, UUID, JSON, fechas o transicion invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Access token o sesion invalidos",content=@Content),
 @ApiResponse(responseCode="403",description="Rol insuficiente, otro tenant o recurso de otro conductor",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicado, intervalo superpuesto, bloqueo vencido o estado incompatible",content=@Content)
})
public class ReservationsController {
 private final ParkingCommandServiceImpl commands; private final ParkingQueryServiceImpl queries;
 public ReservationsController(ParkingCommandServiceImpl commands,ParkingQueryServiceImpl queries){this.commands=commands;this.queries=queries;}

 @GetMapping @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Listar reservas de mi alcance",description="DRIVER: sus reservas; PARKING_ADMIN: las de su tenant.")
 public List<ReservationView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor){return queries.reservations(actor).stream().map(ReservationView::of).toList();}
 @GetMapping("/{reservationId}") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Consultar reserva autorizada")
 public ReservationView get(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID reservationId){return ReservationView.of(queries.reservation(actor,reservationId));}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('DRIVER')") @Operation(summary="Reservar espacio y bloquear temporalmente",description="Crea PENDING_PAYMENT con paymentId NULL. Valida vehiculo propio activo, espacio operativo e intervalos sin solapamiento. Importe base en PEN por CEILING; membresias pendientes de adaptar Billing.")
 @ApiResponse(responseCode="201",description="Reserva pendiente de pago con bloqueo vigente")
 public ReservationView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody ReserveBody body){return ReservationView.of(commands.reserve(actor,body.spotId(),body.vehicleId(),body.startAt(),body.endAt()));}
 @PostMapping("/{reservationId}/confirm-payment") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')")
 @Operation(summary="Confirmar pago de reserva",description="Verifica digital_payments: APPROVED, operacion RESERVATION, perfil e importe exactos y bloqueo vigente. No acepta la palabra del cliente sobre el estado del pago.")
 @ApiResponse(responseCode="503",description="Billing todavia no dispone de digital_payments",content=@Content)
 public ReservationView confirm(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID reservationId,@Valid @RequestBody PaymentBody body){return ReservationView.of(commands.confirm(actor,reservationId,body.paymentId()));}
 @PostMapping("/{reservationId}/cancel") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Cancelar reserva",description="Libera el bloqueo; no ejecuta automaticamente un reembolso de Billing.")
 public ReservationView cancel(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID reservationId){return ReservationView.of(commands.cancel(actor,reservationId));}
 @PostMapping("/{reservationId}/no-show") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Registrar ausencia",description="Solo al finalizar el intervalo de una reserva confirmada.")
 public ReservationView noShow(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID reservationId){return ReservationView.of(commands.noShow(actor,reservationId));}

}
