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

@RestController @RequestMapping(value="/api/v1/payment-tokens",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Payment tokens",description="Medios tokenizados del perfil. No almacena numero completo de tarjeta, CVV ni tokens JWT.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="UUID, JSON, datos o importe invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Sesion o access token invalidos",content=@Content),
 @ApiResponse(responseCode="403",description="Rol, perfil o tenant fuera de tu alcance",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicado, estado incompatible o conflicto de idempotencia",content=@Content),
 @ApiResponse(responseCode="503",description="Proveedor de pagos no configurado o no disponible; no se inventa aprobacion",content=@Content)
})
public class PaymentTokensController {
 private final BillingService service;private final BillingQueryService queries;private final SubscriptionPlans plans;
 public PaymentTokensController(BillingService service,BillingQueryService queries,SubscriptionPlans plans){this.service=service;this.queries=queries;this.plans=plans;}

 @GetMapping @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Listar mis medios de pago",description="Devuelve solo marca, ultimos cuatro, vencimiento y estado. No expone providerTokenRef.")
 public List<TokenView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor){return queries.tokens(actor).stream().map(TokenView::of).toList();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Registrar medio tokenizado",description="Valida la referencia y obtiene los datos resumidos del proveedor. El cliente no puede registrar un PAN o CVV en estos campos.")
 @ApiResponse(responseCode="201",description="Medio registrado")
 public TokenView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody TokenBody body){return TokenView.of(service.registerToken(actor,body.providerTokenRef()));}
 @DeleteMapping("/{tokenId}") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Revocar mi medio de pago",description="Conserva el historial, marca REVOKED y captura revokedAt.")
 public TokenView revoke(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID tokenId){return TokenView.of(service.revokeToken(actor,tokenId));}

}
