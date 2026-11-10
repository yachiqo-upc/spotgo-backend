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
@Tag(name="Payment tokens",description="Tokenized payment methods owned by a profile. Full card numbers, CVVs, and JWTs are not stored.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid UUID, JSON, data, or amount.",content=@Content),
 @ApiResponse(responseCode="401",description="The session or access token is invalid.",content=@Content),
 @ApiResponse(responseCode="403",description="The role, profile, or tenant is outside the caller's access scope.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicate resource, incompatible state, or idempotency conflict.",content=@Content),
 @ApiResponse(responseCode="503",description="The configured payment provider is unavailable.",content=@Content)
})
public class PaymentTokensController {
 private final BillingService service;private final BillingQueryService queries;private final SubscriptionPlans plans;
 public PaymentTokensController(BillingService service,BillingQueryService queries,SubscriptionPlans plans){this.service=service;this.queries=queries;this.plans=plans;}

 @GetMapping @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="List my payment methods",description="Returns the brand, last four digits, expiration, status, and whether the token is the tenant's default payment method. Does not expose providerTokenRef.")
 public List<TokenView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor){var defaultId=service.defaultPaymentTokenId(actor);return queries.tokens(actor).stream().map(t->TokenView.of(t,Objects.equals(t.getId(),defaultId))).toList();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Register a tokenized payment method",description="The academic default accepts synthetic references such as simulated-visa-4242 and returns summarized details. It rejects card numbers and security codes; no real payment method is contacted.")
 @ApiResponse(responseCode="201",description="Payment method registered.")
 public TokenView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody TokenBody body){return TokenView.of(service.registerToken(actor,body.providerTokenRef()));}
 @PutMapping("/{tokenId}/default") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Set the tenant's default payment method",description="The tenant's default payment method is used for prorated charges when new parking spots are added.")
 public TokenView setDefault(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID tokenId){return TokenView.of(service.setDefaultPaymentToken(actor,tokenId),true);}
 @DeleteMapping("/{tokenId}") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Revoke my payment method",description="Preserves the history, sets the status to REVOKED, and records revokedAt.")
 public TokenView revoke(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID tokenId){return TokenView.of(service.revokeToken(actor,tokenId));}

}
