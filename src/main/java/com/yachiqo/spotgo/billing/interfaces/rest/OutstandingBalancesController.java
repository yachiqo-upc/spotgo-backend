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

@RestController @RequestMapping(value="/api/v1/outstanding-balances",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Outstanding balances",description="Una fila por cargo impago. Permite consultar y pagar aunque impida crear nuevas reservas.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="UUID, JSON, datos o importe invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Sesion o access token invalidos",content=@Content),
 @ApiResponse(responseCode="403",description="Rol, perfil o tenant fuera de tu alcance",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicado, estado incompatible o conflicto de idempotencia",content=@Content),
 @ApiResponse(responseCode="503",description="Proveedor de pagos no configurado o no disponible; no se inventa aprobacion",content=@Content)
})
public class OutstandingBalancesController {
 private final BillingService service;private final BillingQueryService queries;private final SubscriptionPlans plans;
 public OutstandingBalancesController(BillingService service,BillingQueryService queries,SubscriptionPlans plans){this.service=service;this.queries=queries;this.plans=plans;}

 @GetMapping @Operation(summary="Listar deudas de mi alcance")
 public List<BalanceView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor){return queries.balances(actor).stream().map(BalanceView::of).toList();}
 @GetMapping("/total") @Operation(summary="Calcular total pendiente de mi alcance",description="Suma outstanding_balances OPEN; no suma otra vez additional_charges.")
 public Map<String,BigDecimal> total(@AuthenticationPrincipal SpotgoUserPrincipal actor){return Map.of("amount",queries.outstandingTotal(actor));}

}
