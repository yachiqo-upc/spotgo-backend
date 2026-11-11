package com.yachiqo.spotgo.billing.infrastructure.providers;

import com.yachiqo.spotgo.billing.application.PaymentProvider;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.PaymentStatus;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.RefundStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulatedPaymentProviderTest {
    private final SimulatedPaymentProvider provider = new SimulatedPaymentProvider();

    @Test
    void syntheticChargeOutcomesAreResolvedWithoutAnExternalProcessor() {
        assertChargeOutcome("simulated-visa-4242", PaymentStatus.APPROVED);
        assertChargeOutcome("simulated-visa-0000", PaymentStatus.REJECTED);
        assertChargeOutcome("simulated-visa-1111", PaymentStatus.PENDING);
    }

    @Test
    void refundIsAsynchronousAndIdempotentlyReconciled() {
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        String chargeReference = provider.reconcile(paymentId, 1,
                provider.charge(paymentId, 1, provider.inspectToken("simulated-visa-4242").reference(),
                        new BigDecimal("12.00"), "PEN").operationReference()).operationReference();

        var started = provider.refund(refundId, chargeReference, new BigDecimal("12.00"), "PEN");
        assertEquals(RefundStatus.PROCESSING, started.status());
        assertEquals(RefundStatus.COMPLETED,
                provider.reconcileRefund(refundId, started.operationReference()).status());
    }

    private void assertChargeOutcome(String syntheticToken, PaymentStatus expected) {
        UUID paymentId = UUID.randomUUID();
        PaymentProvider.TokenDetails token = provider.inspectToken(syntheticToken);
        var started = provider.charge(paymentId, 1, token.reference(), new BigDecimal("12.00"), "PEN");
        assertEquals(PaymentStatus.PENDING, started.status());
        assertEquals(expected, provider.reconcile(paymentId, 1, started.operationReference()).status());
    }
}
