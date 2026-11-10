package com.yachiqo.spotgo.billing.interfaces.rest.resources;
import com.yachiqo.spotgo.billing.domain.model.aggregates.*;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
public final class BillingResources {
 private BillingResources() {}
 public record TokenBody(@NotBlank @Schema(description="Referencia obtenida mediante tokenizacion del proveedor; no numero de tarjeta ni CVV") String providerTokenRef) {}
 public record PaymentBody(@NotNull UUID tokenId,@NotBlank @Size(max=255) @Schema(example="mi-operacion-unica-001") String idempotencyKey) {}
 public record SubscriptionBody(@NotBlank @Schema(description="Codigo del catalogo configurado en el servidor; no se aceptan precio ni descuento enviados por el cliente") String planCode,@NotNull UUID tokenId,@NotBlank @Size(max=255) String idempotencyKey) {}
 public record ChargeBody(@NotNull UUID reservationId,@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,@NotBlank String reason,@NotBlank String evidenceRef) {}
 public record RefundBody(@NotNull UUID paymentId,@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,@NotBlank String reason) {}
 public record DocumentBody(@NotNull UUID paymentId,@NotNull BillingDocumentType documentType,@NotBlank String documentNumber,
  @NotBlank String series,@NotBlank String legalName,@NotNull @Schema(example="{\"document_type\":\"DNI\",\"document_number\":\"00000000\"}") Map<String,Object> taxData) {}
 public record TokenView(UUID tokenId,UUID profileId,String brand,String lastFour,short expirationMonth,short expirationYear,PaymentTokenStatus status,Instant createdAt,Instant revokedAt) {
  public static TokenView of(PaymentToken t){return new TokenView(t.getId(),t.getProfileId(),t.getBrand(),t.getLastFour(),t.getExpirationMonth(),t.getExpirationYear(),t.getStatus(),t.getCreatedAt(),t.getRevokedAt());}
 }
 public record PaymentView(UUID paymentId,UUID tokenId,UUID profileId,PaymentOperationType operationType,BigDecimal amount,String currency,PaymentStatus status,
  String providerOperationRef,String idempotencyKey,String requestHash,Instant firstSeenAt,Instant lastSeenAt,String resultReference,Instant createdAt,Instant approvedAt) {
  public static PaymentView of(DigitalPayment p){return new PaymentView(p.getId(),p.getTokenId(),p.getProfileId(),p.getOperationType(),p.getAmount(),p.getCurrency(),p.getStatus(),
   p.getProviderOperationRef(),p.getIdempotencyKey(),p.getRequestHash(),p.getFirstSeenAt(),p.getLastSeenAt(),p.getResultReference(),p.getCreatedAt(),p.getApprovedAt());}
 }
 public record AttemptView(UUID attemptId,UUID paymentId,int attemptNumber,Instant startedAt,Instant finishedAt,PaymentAttemptStatus status,String errorCode,Instant nextAttemptAt) {
  public static AttemptView of(PaymentAttempt a){return new AttemptView(a.getId(),a.getPaymentId(),a.getAttemptNumber(),a.getStartedAt(),a.getFinishedAt(),a.getStatus(),a.getErrorCode(),a.getNextAttemptAt());}
 }
 public record SubscriptionView(UUID subscriptionId,UUID profileId,UUID tenantId,UUID paymentId,Map<String,Object> plan,Instant startAt,Instant endAt,SubscriptionStatus status) {
  public static SubscriptionView of(Subscription s){return new SubscriptionView(s.getId(),s.getProfileId(),s.getTenantId(),s.getPaymentId(),s.getPlan(),s.getStartAt(),s.getEndAt(),s.getStatus());}
 }
 public record ChargeView(UUID chargeId,UUID reservationId,UUID paymentId,BigDecimal amount,String reason,String evidenceRef,AdditionalChargeStatus status,Instant createdAt) {
  public static ChargeView of(AdditionalCharge c){return new ChargeView(c.getId(),c.getReservationId(),c.getPaymentId(),c.getAmount(),c.getReason(),c.getEvidenceRef(),c.getStatus(),c.getCreatedAt());}
 }
 public record BalanceView(UUID balanceId,UUID profileId,UUID chargeId,BigDecimal amount,Instant dueAt,OutstandingBalanceStatus status,Instant createdAt,Instant regularizedAt) {
  public static BalanceView of(OutstandingBalance b){return new BalanceView(b.getId(),b.getProfileId(),b.getChargeId(),b.getAmount(),b.getDueAt(),b.getStatus(),b.getCreatedAt(),b.getRegularizedAt());}
 }
 public record RefundView(UUID refundId,UUID paymentId,BigDecimal amount,String reason,RefundStatus status,String providerOperationRef,Instant requestedAt,Instant completedAt) {
  public static RefundView of(Refund r){return new RefundView(r.getId(),r.getPaymentId(),r.getAmount(),r.getReason(),r.getStatus(),r.getProviderOperationRef(),r.getRequestedAt(),r.getCompletedAt());}
 }
 public record DocumentView(UUID documentId,UUID paymentId,BillingDocumentType documentType,String documentNumber,String series,String legalName,Map<String,Object> taxData,Instant issuedAt,BillingDocumentStatus status) {
  public static DocumentView of(BillingDocument d){return new DocumentView(d.getId(),d.getPaymentId(),d.getDocumentType(),d.getDocumentNumber(),d.getSeries(),d.getLegalName(),d.getTaxData(),d.getIssuedAt(),d.getStatus());}
 }
}
