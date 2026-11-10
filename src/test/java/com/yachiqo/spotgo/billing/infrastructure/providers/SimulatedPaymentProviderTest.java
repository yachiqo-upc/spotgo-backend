package com.yachiqo.spotgo.billing.infrastructure.providers;

import com.yachiqo.spotgo.billing.domain.model.valueobjects.PaymentStatus;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.RefundStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SimulatedPaymentProviderTest {
    private final SimulatedPaymentProvider provider = new SimulatedPaymentProvider();

    @Test
    void acceptsSyntheticTokenReferencesAndRejectsCardNumbers() {
        var token = provider.inspectToken("simulated-visa-4242");

        assertEquals("VISA", token.brand());
        assertEquals("4242", token.lastFour());
        assertTrue(token.reference().startsWith("simulated:visa:4242:"));
        assertThrows(IllegalArgumentException.class, () -> provider.inspectToken("4111111111111111"));
        assertThrows(IllegalArgumentException.class, () -> provider.inspectToken("visa-4242"));
    }

    @Test
    void approvesChargesIdempotentlyAndCompletesRefundsWithoutMovingMoney() {
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        var token = provider.inspectToken("simulated-visa-4242");

        var first = provider.charge(paymentId, 1, token.reference(), new BigDecimal("30.00"), "PEN");
        var replay = provider.charge(paymentId, 1, token.reference(), new BigDecimal("30.00"), "PEN");
        var reconciled = provider.reconcile(paymentId, 1, first.operationReference());
        var refund = provider.refund(refundId, first.operationReference(), new BigDecimal("30.00"), "PEN");

        assertEquals(PaymentStatus.APPROVED, first.status());
        assertEquals(first.operationReference(), replay.operationReference());
        assertEquals(PaymentStatus.APPROVED, reconciled.status());
        assertEquals(RefundStatus.COMPLETED, refund.status());
        assertEquals("simulated-refund:" + refundId, refund.operationReference());
    }
}
