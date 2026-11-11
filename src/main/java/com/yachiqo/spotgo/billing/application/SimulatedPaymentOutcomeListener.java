package com.yachiqo.spotgo.billing.application;

import com.yachiqo.spotgo.shared.domain.events.SpotgoDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Completes the academic provider's simulated callbacks after the billing transaction commits. */
@Component
public class SimulatedPaymentOutcomeListener {
    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentOutcomeListener.class);
    private final BillingService billing;

    public SimulatedPaymentOutcomeListener(BillingService billing) {
        this.billing = billing;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(SpotgoDomainEvent event) {
        try {
            if ("billing.payment.status_changed".equals(event.type())
                    && "PENDING".equals(event.data().get("status"))) {
                billing.reconcileSimulatedPayment(event.aggregateId());
            } else if ("billing.refund.status_changed".equals(event.type())
                    && "PROCESSING".equals(event.data().get("status"))) {
                billing.reconcileSimulatedRefund(event.aggregateId());
            }
        } catch (RuntimeException exception) {
            log.error("Simulated payment outcome could not be reconciled for payment {}",
                    event.aggregateId(), exception);
        }
    }
}
