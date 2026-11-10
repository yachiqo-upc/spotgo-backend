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
@Tag(name="Refunds",description="Refunds linked to the original payment. The total committed refund amount cannot exceed the approved payment amount.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid UUID, JSON, data, or amount.",content=@Content),
 @ApiResponse(responseCode="401",description="The session or access token is invalid.",content=@Content),
 @ApiResponse(responseCode="403",description="The role, profile, or tenant is outside the caller's access scope.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicate resource, incompatible state, or idempotency conflict.",content=@Content),
 @ApiResponse(responseCode="503",description="The configured payment provider is unavailable.",content=@Content)
})
public class RefundsController {
 private final BillingService service;private final BillingQueryService queries;private final SubscriptionPlans plans;
 public RefundsController(BillingService service,BillingQueryService queries,SubscriptionPlans plans){this.service=service;this.queries=queries;this.plans=plans;}

 @GetMapping @Operation(summary="View refunds for an authorized payment", description = "Returns refund requests associated with the specified payment after checking payment access.")
 public List<RefundView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam UUID paymentId){return queries.refunds(actor,paymentId).stream().map(RefundView::of).toList();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('PARKING_ADMIN','SUPER_ADMIN')") @Operation(summary="Request an authorized refund",description="Requires an approved payment and locks the remaining refundable balance. Repeating the same payment, reason, and amount returns the existing request.")
 @ApiResponse(responseCode="201",description="Refund requested or retrieved.")
 public RefundView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody RefundBody body){return RefundView.of(service.requestRefund(actor,body.paymentId(),body.amount(),body.reason()));}
 @PostMapping("/{refundId}/process") @PreAuthorize("hasAnyRole('PARKING_ADMIN','SUPER_ADMIN')") @Operation(summary="Process a refund request",description="The provider uses refundId as the idempotency key. completedAt is recorded only after the refund is confirmed.")
 public RefundView process(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID refundId){return RefundView.of(service.processRefund(actor,refundId));}

}
