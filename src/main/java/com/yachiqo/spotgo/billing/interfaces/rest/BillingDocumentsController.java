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

@RestController @RequestMapping(value="/api/v1/billing-documents",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Billing documents",description="Primary billing document for an approved payment. This does not imply SUNAT integration or tax authorization.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid UUID, JSON, data, or amount.",content=@Content),
 @ApiResponse(responseCode="401",description="The session or access token is invalid.",content=@Content),
 @ApiResponse(responseCode="403",description="The role, profile, or tenant is outside the caller's access scope.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicate resource, incompatible state, or idempotency conflict.",content=@Content),
 @ApiResponse(responseCode="503",description="The configured payment provider is unavailable.",content=@Content)
})
public class BillingDocumentsController {
 private final BillingService service;private final BillingQueryService queries;private final SubscriptionPlans plans;
 public BillingDocumentsController(BillingService service,BillingQueryService queries,SubscriptionPlans plans){this.service=service;this.queries=queries;this.plans=plans;}

 @GetMapping @Operation(summary="Get the billing document for an authorized payment", description = "Returns the billing document associated with paymentId when the caller is authorized to view that payment.")
 public DocumentView get(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam UUID paymentId){return DocumentView.of(queries.document(actor,paymentId).orElseThrow(()->BillingService.missing("Billing document")));}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('PARKING_ADMIN','SUPER_ADMIN')") @Operation(summary="Register an issued billing document",description="The issuer provides the series and number. ELECTRONIC_RECEIPT requires an 8-digit DNI; ELECTRONIC_INVOICE requires an 11-digit RUC. Each payment can have one document, and each number must be unique within its series. This does not create an external tax document.")
 @ApiResponse(responseCode="201",description="Billing document created or retrieved.")
 public DocumentView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody DocumentBody body){return DocumentView.of(service.issueDocument(actor,body.paymentId(),body.documentType(),body.documentNumber(),body.series(),body.legalName(),body.taxData()));}
 @PostMapping("/{documentId}/void") @PreAuthorize("hasAnyRole('PARKING_ADMIN','SUPER_ADMIN')") @Operation(summary="Void a billing document",description="Updates the local status. External tax cancellation requires the corresponding document-issuing service.")
 public DocumentView cancel(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID documentId){return DocumentView.of(service.voidDocument(actor,documentId));}

}
