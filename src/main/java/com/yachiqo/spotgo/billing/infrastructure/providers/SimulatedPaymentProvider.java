package com.yachiqo.spotgo.billing.infrastructure.providers;

import com.yachiqo.spotgo.billing.application.PaymentProvider;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.PaymentStatus;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.RefundStatus;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local academic payment simulation. It accepts only synthetic token references,
 * approves valid charges, and completes refunds without contacting an external service.
 */
public final class SimulatedPaymentProvider implements PaymentProvider {
    private static final Pattern TOKEN_REFERENCE = Pattern.compile(
            "simulated-(visa|mastercard|amex|discover)-([0-9]{4})", Pattern.CASE_INSENSITIVE);

    @Override
    public TokenDetails inspectToken(String reference) {
        Matcher match = TOKEN_REFERENCE.matcher(reference == null ? "" : reference.trim());
        if (!match.matches()) {
            throw new IllegalArgumentException(
                    "Use a synthetic token such as simulated-visa-4242. Card numbers and security codes are not accepted.");
        }

        String brand = match.group(1).toUpperCase(Locale.ROOT);
        String lastFour = match.group(2);
        String uniqueReference = "simulated:" + brand.toLowerCase(Locale.ROOT) + ":" + lastFour + ":" + UUID.randomUUID();
        return new TokenDetails(uniqueReference, brand, lastFour, 12, 2099);
    }

    @Override
    public PaymentResult charge(UUID paymentId, int attemptNumber, String providerTokenReference,
                                BigDecimal amount, String currency) {
        requireCharge(paymentId, attemptNumber, providerTokenReference, amount, currency);
        return new PaymentResult(PaymentStatus.APPROVED, chargeReference(paymentId, attemptNumber), null);
    }

    @Override
    public PaymentResult reconcile(UUID paymentId, int attemptNumber, String operationReference) {
        if (paymentId != null && attemptNumber > 0
                && chargeReference(paymentId, attemptNumber).equals(operationReference)) {
            return new PaymentResult(PaymentStatus.APPROVED, operationReference, null);
        }
        return new PaymentResult(PaymentStatus.RECONCILIATION_REQUIRED, null, "SIMULATED_OPERATION_NOT_FOUND");
    }

    @Override
    public RefundResult refund(UUID refundId, String originalOperationReference, BigDecimal amount, String currency) {
        if (refundId == null || originalOperationReference == null
                || !originalOperationReference.startsWith("simulated-charge:")
                || amount == null || amount.signum() <= 0 || !"PEN".equals(currency)) {
            throw new IllegalArgumentException("Invalid simulated refund.");
        }
        return new RefundResult(RefundStatus.COMPLETED, "simulated-refund:" + refundId);
    }

    private static void requireCharge(UUID paymentId, int attemptNumber, String tokenReference,
                                      BigDecimal amount, String currency) {
        if (paymentId == null || attemptNumber < 1 || tokenReference == null
                || !tokenReference.startsWith("simulated:") || amount == null || amount.signum() <= 0
                || !"PEN".equals(currency)) {
            throw new IllegalArgumentException("Invalid simulated payment.");
        }
    }

    private static String chargeReference(UUID paymentId, int attemptNumber) {
        return "simulated-charge:" + paymentId + ":" + attemptNumber;
    }
}
