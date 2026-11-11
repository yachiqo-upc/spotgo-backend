package com.yachiqo.spotgo.notifications.application;

import com.yachiqo.spotgo.shared.domain.events.SpotgoDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DomainEventNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(DomainEventNotificationListener.class);
    private final PushNotificationService notifications;

    public DomainEventNotificationListener(PushNotificationService notifications) {
        this.notifications = notifications;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(SpotgoDomainEvent event) {
        try {
            notifications.send(event);
        } catch (RuntimeException exception) {
            log.error("Notification dispatch failed for event {} ({})", event.eventId(), event.type(), exception);
        }
    }
}
