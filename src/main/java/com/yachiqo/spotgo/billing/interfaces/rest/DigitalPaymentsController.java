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
@Tag(name="Digital payments",description="Payment amounts are calculated by the server and operations are idempotent. Creating a payment operation does not mean the charge has been approved.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid UUID, JSON, data, or amount.",content=@Content),
 @ApiResponse(responseCode="401",description="The session or access token is invalid.",content=@Content),
 @ApiResponse(responseCode="403",description="The role, profile, or tenant is outside the caller's access scope.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicate resource, incompatible state, or idempotency conflict.",content=@Content),
 @ApiResponse(responseCode="503",description="The configured payment provider is unavailable.",content=@Content)
})
public class DigitalPaymentsController {
 private final BillingService service;private final BillingQueryService queries;private final SubscriptionPlans plans;
 public DigitalPaymentsController(BillingService service,BillingQueryService queries,SubscriptionPlans plans){this.service=service;this.queries=queries;this.plans=plans;}

 @GetMapping @Operation(summary="List authorized payments",description="Returns the authenticated profile's payments; SUPER_ADMIN can perform an administrative query.")
 public List<PaymentView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor){return queries.payments(actor).stream().map(PaymentView::of).toList();}
 @GetMapping("/{paymentId}") @Operation(summary="Get an authorized payment", description = "Returns the requested payment after checking that it belongs to the caller or is within the SUPER_ADMIN scope.")
 public PaymentView get(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID paymentId){return PaymentView.of(queries.payment(actor,paymentId));}
 @PostMapping("/reservations/{reservationId}") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('DRIVER')") @Operation(summary="Create a payment for my reservation",description="Uses the reservation amount and sets reservations.payment_id. Repeating the same idempotencyKey and request returns the existing operation; reusing the key with a different request returns 409.")
 @ApiResponse(responseCode="201",description="Operation created or retrieved idempotently.")
  public PaymentView reservation(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID reservationId,@Valid @RequestBody PaymentBody body){return PaymentView.of(service.reservationPayment(actor,reservationId,body.tokenId(),body.idempotencyKey()));}
  @PostMapping("/reservation-changes/{changeId}") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('DRIVER')")
  @Operation(summary="Create a payment for a rescheduling price increase",description="Charges only the difference calculated by the backend. The reservation change is applied only after payment approval.")
  public PaymentView reservationChange(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID changeId,@Valid @RequestBody PaymentBody body){
   return PaymentView.of(service.reservationChangePayment(actor,changeId,body.tokenId(),body.idempotencyKey()));
  }
 @PostMapping("/subscriptions") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Create a payment to subscribe to a plan",description="Accepts planCode, tokenId, and idempotencyKey. No subscription is created until payment is approved. Approval creates an ACTIVE row with a required payment_id and start_at set to the approval time. billing_period_months=1 represents one calendar month.")
 @ApiResponse(responseCode="201",description="Operation created or retrieved.")
 public PaymentView subscription(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody SubscriptionBody body){return PaymentView.of(service.subscriptionPayment(actor,body.planCode(),body.tokenId(),body.idempotencyKey()));}
 @PostMapping("/additional-charges/{chargeId}") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('DRIVER')") @Operation(summary="Create a payment for my additional charge",description="This payment is separate from the initial reservation payment. Approval regularizes the outstanding balance for this charge, if one exists.")
 @ApiResponse(responseCode="201",description="Operation created or retrieved.")
 public PaymentView charge(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID chargeId,@Valid @RequestBody PaymentBody body){return PaymentView.of(service.chargePayment(actor,chargeId,body.tokenId(),body.idempotencyKey()));}
 @PostMapping("/{paymentId}/process") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Process my payment operation",description="The payment provider determines the result. The client cannot submit status or approvedAt. An already approved payment is returned without charging again.")
 public PaymentView process(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID paymentId){return PaymentView.of(service.process(actor,paymentId,false));}
 @PostMapping("/{paymentId}/retry") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Retry a payment with a known failure",description="Keeps paymentId and adds a payment_attempt. tokenId can replace the payment method after a definitive decline. The retry delay is enforced. An unknown result must be reconciled.")
 public PaymentView retry(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID paymentId,@RequestBody(required=false) RetryPaymentBody body){return PaymentView.of(service.process(actor,paymentId,true,body==null?null:body.tokenId()));}
 @PostMapping("/{paymentId}/reconcile") @PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN')") @Operation(summary="Reconcile a pending payment with the provider",description="Does not create another charge. If approval is confirmed, updates the related reservation, subscription, or charge.")
 public PaymentView reconcile(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID paymentId){return PaymentView.of(service.reconcile(actor,paymentId));}

}
