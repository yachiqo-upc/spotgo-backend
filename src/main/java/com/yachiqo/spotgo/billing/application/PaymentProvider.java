package com.yachiqo.spotgo.billing.application;
import java.util.UUID;
import java.math.BigDecimal;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.*;
/**
 * Payment processor contract; no raw PAN or CVV.
 * charge MUST be idempotent by (paymentId, attemptNumber); refund by refundId.
 * reconcile only queries an existing operation and MUST NOT create another debit.
 */
public interface PaymentProvider {
 record TokenDetails(String reference,String brand,String lastFour,int expirationMonth,int expirationYear) {}
 record PaymentResult(PaymentStatus status,String operationReference,String errorCode) {}
 record RefundResult(RefundStatus status,String operationReference) {}
 TokenDetails inspectToken(String reference);
 PaymentResult charge(UUID paymentId,int attemptNumber,String providerTokenReference,BigDecimal amount,String currency);
 PaymentResult reconcile(UUID paymentId,int attemptNumber,String operationReference);
 RefundResult refund(UUID refundId,String originalOperationReference,BigDecimal amount,String currency);
}
