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
@Tag(name="Additional charges",description="Additional charges associated with a reservation. Each charge is separate from the initial payment, and one reservation can have multiple charges.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid UUID, JSON, data, or amount.",content=@Content),
 @ApiResponse(responseCode="401",description="The session or access token is invalid.",content=@Content),
 @ApiResponse(responseCode="403",description="The role, profile, or tenant is outside the caller's access scope.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicate resource, incompatible state, or idempotency conflict.",content=@Content),
 @ApiResponse(responseCode="503",description="The configured payment provider is unavailable.",content=@Content)
})
public class AdditionalChargesController {
 private final BillingService service;private final BillingQueryService queries;private final SubscriptionPlans plans;
 public AdditionalChargesController(BillingService service,BillingQueryService queries,SubscriptionPlans plans){this.service=service;this.queries=queries;this.plans=plans;}

 @GetMapping @Operation(summary="List charges in my scope", description = "Returns additional charges visible to the authenticated profile or the administrator of its tenant.")
 public List<ChargeView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor){return queries.charges(actor).stream().map(ChargeView::of).toList();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Create a documented charge for my tenant",description="Requires a completed reservation, a positive amount, a reason, and evidence. Repeating the same reservation, reason, and evidence does not duplicate the charge.")
 @ApiResponse(responseCode="201",description="Charge created or retrieved.")
 public ChargeView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody ChargeBody body){return ChargeView.of(service.createCharge(actor,body.reservationId(),body.amount(),body.reason(),body.evidenceRef()));}
 @PostMapping("/{chargeId}/mark-unpaid") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Record an unpaid charge as an outstanding balance",description="Creates at most one balance per charge. A pending or unknown payment is not treated as declined. The charge amount is not added to the balance a second time.")
 public BalanceView unpaid(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID chargeId){return BalanceView.of(service.markUnpaid(actor,chargeId));}

}
