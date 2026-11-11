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
 * returns asynchronous charge and refund outcomes without an external service.
 */
public final class SimulatedPaymentProvider implements PaymentProvider {
    private static final Pattern TOKEN_REFERENCE = Pattern.compile(
            "simulated-(visa|mastercard|amex|discover)-([0-9]{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERNAL_TOKEN_REFERENCE = Pattern.compile(
            "simulated:(visa|mastercard|amex|discover):([0-9]{4}):([0-9a-fA-F-]{36})", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARGE_REFERENCE = Pattern.compile(
            "simulated-charge:(APPROVED|REJECTED|PENDING):([0-9a-fA-F-]{36}):([1-9][0-9]*)");

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
        Matcher tokenMatch = INTERNAL_TOKEN_REFERENCE.matcher(providerTokenReference);
        if (!tokenMatch.matches()) throw new IllegalArgumentException("Invalid simulated payment token.");
        String lastFour = tokenMatch.group(2);
        String simulatedOutcome = switch (lastFour) {
            case "0000" -> "REJECTED";
            case "1111" -> "PENDING";
            default -> "APPROVED";
        };
        return new PaymentResult(PaymentStatus.PENDING,
                chargeReference(paymentId, attemptNumber, simulatedOutcome), null);
    }

    @Override
    public PaymentResult reconcile(UUID paymentId, int attemptNumber, String operationReference) {
        Matcher match = CHARGE_REFERENCE.matcher(operationReference == null ? "" : operationReference);
        if (paymentId != null && attemptNumber > 0 && match.matches()
                && paymentId.toString().equalsIgnoreCase(match.group(2))
                && Integer.toString(attemptNumber).equals(match.group(3))) {
            PaymentStatus status = PaymentStatus.valueOf(match.group(1));
            return new PaymentResult(status, operationReference,
                    status == PaymentStatus.REJECTED ? "SIMULATED_DECLINE" : null);
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
        return new RefundResult(RefundStatus.PROCESSING, "simulated-refund:" + refundId);
    }

    @Override
    public RefundResult reconcileRefund(UUID refundId, String operationReference) {
        if (refundId != null && ("simulated-refund:" + refundId).equals(operationReference)) {
            return new RefundResult(RefundStatus.COMPLETED, operationReference);
        }
        return new RefundResult(RefundStatus.RECONCILIATION_REQUIRED, null);
    }

    private static void requireCharge(UUID paymentId, int attemptNumber, String tokenReference,
                                      BigDecimal amount, String currency) {
        if (paymentId == null || attemptNumber < 1 || tokenReference == null
                || !INTERNAL_TOKEN_REFERENCE.matcher(tokenReference).matches() || amount == null || amount.signum() <= 0
                || !"PEN".equals(currency)) {
            throw new IllegalArgumentException("Invalid simulated payment.");
        }
    }

    private static String chargeReference(UUID paymentId, int attemptNumber, String outcome) {
        return "simulated-charge:" + outcome + ":" + paymentId + ":" + attemptNumber;
    }
}
