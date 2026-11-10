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

@RestController @RequestMapping(value="/api/v1/additional-charges",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Additional charges",description="Cargos adicionales de una reserva. El pago es distinto del pago inicial; una reserva puede originar varios cargos.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="UUID, JSON, datos o importe invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Sesion o access token invalidos",content=@Content),
 @ApiResponse(responseCode="403",description="Rol, perfil o tenant fuera de tu alcance",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicado, estado incompatible o conflicto de idempotencia",content=@Content),
 @ApiResponse(responseCode="503",description="Proveedor de pagos no configurado o no disponible; no se inventa aprobacion",content=@Content)
})
public class AdditionalChargesController {
 private final BillingService service;private final BillingQueryService queries;private final SubscriptionPlans plans;
 public AdditionalChargesController(BillingService service,BillingQueryService queries,SubscriptionPlans plans){this.service=service;this.queries=queries;this.plans=plans;}

 @GetMapping @Operation(summary="Listar cargos de mi alcance")
 public List<ChargeView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor){return queries.charges(actor).stream().map(ChargeView::of).toList();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Registrar cargo justificado en mi tenant",description="Requiere reserva utilizada, importe positivo, motivo y evidencia. Repetir misma reserva, motivo y evidencia no duplica el cargo.")
 @ApiResponse(responseCode="201",description="Cargo registrado o recuperado")
 public ChargeView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody ChargeBody body){return ChargeView.of(service.createCharge(actor,body.reservationId(),body.amount(),body.reason(),body.evidenceRef()));}
 @PostMapping("/{chargeId}/mark-unpaid") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Registrar deuda por cargo impago",description="Una deuda por cargo. Un pago pendiente o desconocido no se declara rechazado. No suma nuevamente el cargo al importe adeudado.")
 public BalanceView unpaid(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID chargeId){return BalanceView.of(service.markUnpaid(actor,chargeId));}

}
