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
@Tag(name="Billing documents",description="Registro del comprobante principal de un pago aprobado. No implica integracion ni autorizacion tributaria de SUNAT.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="UUID, JSON, datos o importe invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Sesion o access token invalidos",content=@Content),
 @ApiResponse(responseCode="403",description="Rol, perfil o tenant fuera de tu alcance",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicado, estado incompatible o conflicto de idempotencia",content=@Content),
 @ApiResponse(responseCode="503",description="Proveedor de pagos no configurado o no disponible; no se inventa aprobacion",content=@Content)
})
public class BillingDocumentsController {
 private final BillingService service;private final BillingQueryService queries;private final SubscriptionPlans plans;
 public BillingDocumentsController(BillingService service,BillingQueryService queries,SubscriptionPlans plans){this.service=service;this.queries=queries;this.plans=plans;}

 @GetMapping @Operation(summary="Consultar comprobante de un pago autorizado")
 public DocumentView get(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam UUID paymentId){return DocumentView.of(queries.document(actor,paymentId).orElseThrow(()->BillingService.missing("Comprobante")));}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('PARKING_ADMIN','SUPER_ADMIN')") @Operation(summary="Registrar comprobante emitido",description="El emisor proporciona serie y numero. ELECTRONIC_RECEIPT requiere DNI de 8 digitos; ELECTRONIC_INVOICE, RUC de 11. Un documento por pago y numero unico por serie. No inventa un comprobante fiscal externo.")
 @ApiResponse(responseCode="201",description="Comprobante registrado o recuperado")
 public DocumentView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody DocumentBody body){return DocumentView.of(service.issueDocument(actor,body.paymentId(),body.documentType(),body.documentNumber(),body.series(),body.legalName(),body.taxData()));}
 @PostMapping("/{documentId}/void") @PreAuthorize("hasAnyRole('PARKING_ADMIN','SUPER_ADMIN')") @Operation(summary="Registrar anulacion del comprobante",description="Cambia el estado local; la anulacion tributaria externa requiere el servicio de emision correspondiente.")
 public DocumentView cancel(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID documentId){return DocumentView.of(service.voidDocument(actor,documentId));}

}
