package com.yachiqo.spotgo.billing.interfaces.rest;
import com.yachiqo.spotgo.billing.application.*;
import com.yachiqo.spotgo.billing.interfaces.rest.resources.BillingResources.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.Content;
import java.util.*;
import java.math.BigDecimal;

@RestController @RequestMapping(value="/api/v1/digital-payments",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Digital payments",description="Operaciones con importe calculado por el servidor e idempotencia. Crear la operacion no significa aprobar el cobro.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="UUID, JSON, datos o importe invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Sesion o access token invalidos",content=@Content),
 @ApiResponse(responseCode="403",description="Rol, perfil o tenant fuera de tu alcance",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicado, estado incompatible o conflicto de idempotencia",content=@Content),
 @ApiResponse(responseCode="503",description="Proveedor de pagos no configurado o no disponible; no se inventa aprobacion",content=@Content)
})
public class DigitalPaymentsController {
 private final BillingService service;private final BillingQueryService queries;private final SubscriptionPlans plans;
 public DigitalPaymentsController(BillingService service,BillingQueryService queries,SubscriptionPlans plans){this.service=service;this.queries=queries;this.plans=plans;}

 @GetMapping @Operation(summary="Listar pagos autorizados",description="Perfil: sus pagos; SUPER_ADMIN: consulta administrativa.")
 public List<PaymentView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor){return queries.payments(actor).stream().map(PaymentView::of).toList();}
 @GetMapping("/{paymentId}") @Operation(summary="Consultar un pago autorizado")
 public PaymentView get(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID paymentId){return PaymentView.of(queries.payment(actor,paymentId));}
 @PostMapping("/reservations/{reservationId}") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('DRIVER')") @Operation(summary="Crear pago de mi reserva",description="Usa el importe de la reserva y asocia reservations.payment_id. Misma idempotencyKey y contenido devuelve la operacion previa; otra carga con esa clave responde 409.")
 @ApiResponse(responseCode="201",description="Operacion creada o recuperada idempotentemente")
 public PaymentView reservation(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID reservationId,@Valid @RequestBody PaymentBody body){return PaymentView.of(service.reservationPayment(actor,reservationId,body.tokenId(),body.idempotencyKey()));}
 @PostMapping("/subscriptions") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Crear pago para contratar un plan",description="Recibe planCode, tokenId e idempotencyKey. No crea ninguna suscripcion todavia. Al aprobarse crea una fila ACTIVE con payment_id obligatorio y start_at de aprobacion. billing_period_months=1 indica un mes.")
 @ApiResponse(responseCode="201",description="Operacion creada o recuperada")
 public PaymentView subscription(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody SubscriptionBody body){return PaymentView.of(service.subscriptionPayment(actor,body.planCode(),body.tokenId(),body.idempotencyKey()));}
 @PostMapping("/additional-charges/{chargeId}") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('DRIVER')") @Operation(summary="Crear pago de mi cargo adicional",description="Pago separado del pago inicial. Al aprobarse regulariza la deuda de ese cargo si existe.")
 @ApiResponse(responseCode="201",description="Operacion creada o recuperada")
 public PaymentView charge(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID chargeId,@Valid @RequestBody PaymentBody body){return PaymentView.of(service.chargePayment(actor,chargeId,body.tokenId(),body.idempotencyKey()));}
 @PostMapping("/{paymentId}/process") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Procesar mi operacion de pago",description="El proveedor decide el resultado. No recibe status ni approvedAt del cliente. Un pago aprobado se devuelve sin cobrar de nuevo.")
 public PaymentView process(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID paymentId){return PaymentView.of(service.process(actor,paymentId,false));}
 @PostMapping("/{paymentId}/retry") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Reintentar pago con fallo conocido",description="Conserva paymentId y agrega un payment_attempt. Respeta el tiempo de espera. Un resultado desconocido exige conciliacion.")
 public PaymentView retry(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID paymentId){return PaymentView.of(service.process(actor,paymentId,true));}
 @PostMapping("/{paymentId}/reconcile") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Consultar resultado pendiente al proveedor",description="No realiza otro cargo. Si confirma aprobacion, actualiza la reserva, suscripcion o cargo de origen.")
 public PaymentView reconcile(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID paymentId){return PaymentView.of(service.reconcile(actor,paymentId));}

}
