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
@Tag(name="Subscriptions",description="Owned by either a Driver profile or a Tenant, never both. Only paid periods are stored; payment_id is required and unique. Each new period creates another row. plan.billing_period_months defines the duration; 1 means monthly.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid UUID, JSON, data, or amount.",content=@Content),
 @ApiResponse(responseCode="401",description="The session or access token is invalid.",content=@Content),
 @ApiResponse(responseCode="403",description="The role, profile, or tenant is outside the caller's access scope.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicate resource, incompatible state, or idempotency conflict.",content=@Content),
 @ApiResponse(responseCode="503",description="The configured payment provider is unavailable.",content=@Content)
})
public class SubscriptionsController {
 private final BillingService service;private final BillingQueryService queries;private final SubscriptionPlans plans;
 public SubscriptionsController(BillingService service,BillingQueryService queries,SubscriptionPlans plans){this.service=service;this.queries=queries;this.plans=plans;}

 @GetMapping @Operation(summary="List subscriptions in my scope",description="DRIVER can view their own subscriptions; PARKING_ADMIN can view subscriptions for their tenant; SUPER_ADMIN can view all subscriptions.")
 public List<SubscriptionView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor){return queries.subscriptions(actor).stream().map(SubscriptionView::of).toList();}
 @GetMapping("/plans") @Operation(summary="View the configured subscription plans",description="Prices and benefits are configured by the server, not by the payer. An empty catalog means plan configuration is pending.")
 public Collection<Map<String,Object>> plans(){return plans.all();}
 @GetMapping("/plans/{code}/quote") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')")
 @Operation(summary="Get a subscription plan quote",description="For PARKING_ADMIN, the monthly price is calculated from every registered parking spot in the tenant. The server recalculates the price when the payment is created.")
 public Map<String,Object> quote(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable String code){return service.subscriptionQuote(actor,code);}
 @GetMapping("/{subscriptionId}") @Operation(summary="Get an authorized subscription", description = "Returns the requested subscription when its driver or tenant owner is within the caller's access scope.")
 public SubscriptionView get(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID subscriptionId){return SubscriptionView.of(queries.subscription(actor,subscriptionId));}
 @PostMapping("/{subscriptionId}/cancel") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Cancel my subscription",description="Preserves the history and sets endAt when cancelled. Resuming requires a new row. Changing the tenant administrator does not change the business subscription owner.")
 public SubscriptionView cancel(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID subscriptionId){return SubscriptionView.of(service.cancelSubscription(actor,subscriptionId));}

}
