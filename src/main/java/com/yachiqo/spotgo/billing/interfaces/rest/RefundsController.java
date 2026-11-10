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

@RestController @RequestMapping(value="/api/v1/refunds",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Refunds",description="Reembolsos asociados al pago original. La suma comprometida no puede superar el importe aprobado.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="UUID, JSON, datos o importe invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Sesion o access token invalidos",content=@Content),
 @ApiResponse(responseCode="403",description="Rol, perfil o tenant fuera de tu alcance",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicado, estado incompatible o conflicto de idempotencia",content=@Content),
 @ApiResponse(responseCode="503",description="Proveedor de pagos no configurado o no disponible; no se inventa aprobacion",content=@Content)
})
public class RefundsController {
 private final BillingService service;private final BillingQueryService queries;private final SubscriptionPlans plans;
 public RefundsController(BillingService service,BillingQueryService queries,SubscriptionPlans plans){this.service=service;this.queries=queries;this.plans=plans;}

 @GetMapping @Operation(summary="Consultar reembolsos de un pago autorizado")
 public List<RefundView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam UUID paymentId){return queries.refunds(actor,paymentId).stream().map(RefundView::of).toList();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('PARKING_ADMIN','SUPER_ADMIN')") @Operation(summary="Solicitar reembolso autorizado",description="Valida pago aprobado y saldo reembolsable bajo bloqueo. Repetir el mismo pago, motivo e importe devuelve la solicitud anterior.")
 @ApiResponse(responseCode="201",description="Reembolso solicitado o recuperado")
 public RefundView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody RefundBody body){return RefundView.of(service.requestRefund(actor,body.paymentId(),body.amount(),body.reason()));}
 @PostMapping("/{refundId}/process") @PreAuthorize("hasAnyRole('PARKING_ADMIN','SUPER_ADMIN')") @Operation(summary="Procesar solicitud de reembolso",description="El proveedor usa refundId como clave idempotente; completedAt solo se registra al confirmar devolucion.")
 public RefundView process(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID refundId){return RefundView.of(service.processRefund(actor,refundId));}

}
