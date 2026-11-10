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

@RestController @RequestMapping(value="/api/v1/subscriptions",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Subscriptions",description="Titular Driver o Tenant, nunca ambos. Solo periodos pagados: payment_id obligatorio y unico. Cada periodo nuevo crea otra fila. plan.billing_period_months indica la duracion; 1 es mensual.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="UUID, JSON, datos o importe invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Sesion o access token invalidos",content=@Content),
 @ApiResponse(responseCode="403",description="Rol, perfil o tenant fuera de tu alcance",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicado, estado incompatible o conflicto de idempotencia",content=@Content),
 @ApiResponse(responseCode="503",description="Proveedor de pagos no configurado o no disponible; no se inventa aprobacion",content=@Content)
})
public class SubscriptionsController {
 private final BillingService service;private final BillingQueryService queries;private final SubscriptionPlans plans;
 public SubscriptionsController(BillingService service,BillingQueryService queries,SubscriptionPlans plans){this.service=service;this.queries=queries;this.plans=plans;}

 @GetMapping @Operation(summary="Listar suscripciones de mi alcance",description="DRIVER: propias; PARKING_ADMIN: su Tenant; SUPER_ADMIN: consulta general.")
 public List<SubscriptionView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor){return queries.subscriptions(actor).stream().map(SubscriptionView::of).toList();}
 @GetMapping("/plans") @Operation(summary="Consultar catalogo de planes configurado",description="Los precios y beneficios los configura el servidor, no el pagador. Un catalogo vacio indica configuracion pendiente.")
 public Collection<Map<String,Object>> plans(){return plans.all();}
 @GetMapping("/{subscriptionId}") @Operation(summary="Consultar suscripcion autorizada")
 public SubscriptionView get(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID subscriptionId){return SubscriptionView.of(queries.subscription(actor,subscriptionId));}
 @PostMapping("/{subscriptionId}/cancel") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Cerrar mi suscripcion",description="Conserva el historial y captura endAt al cancelar. Retomar requiere otra fila. No cambia el titular empresarial cuando cambia el administrador.")
 public SubscriptionView cancel(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID subscriptionId){return SubscriptionView.of(service.cancelSubscription(actor,subscriptionId));}

}
