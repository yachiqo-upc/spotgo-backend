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
@Tag(name="Outstanding balances",description="One row per unpaid charge. Balances can be viewed and paid even when they block new reservations.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid UUID, JSON, data, or amount.",content=@Content),
 @ApiResponse(responseCode="401",description="The session or access token is invalid.",content=@Content),
 @ApiResponse(responseCode="403",description="The role, profile, or tenant is outside the caller's access scope.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicate resource, incompatible state, or idempotency conflict.",content=@Content),
 @ApiResponse(responseCode="503",description="The configured payment provider is unavailable.",content=@Content)
})
public class OutstandingBalancesController {
 private final BillingService service;private final BillingQueryService queries;private final SubscriptionPlans plans;
 public OutstandingBalancesController(BillingService service,BillingQueryService queries,SubscriptionPlans plans){this.service=service;this.queries=queries;this.plans=plans;}

 @GetMapping @Operation(summary="List outstanding balances in my scope", description = "Returns open and regularized balances visible to the authenticated profile or tenant administrator.")
 public List<BalanceView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor){return queries.balances(actor).stream().map(BalanceView::of).toList();}
 @GetMapping("/total") @Operation(summary="Get the outstanding balance total in my scope",description="Sums OPEN outstanding_balances without counting additional_charges a second time.")
 public Map<String,BigDecimal> total(@AuthenticationPrincipal SpotgoUserPrincipal actor){return Map.of("amount",queries.outstandingTotal(actor));}

}
