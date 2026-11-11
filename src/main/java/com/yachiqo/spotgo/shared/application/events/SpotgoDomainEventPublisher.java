package com.yachiqo.spotgo.shared.application.events;

import com.yachiqo.spotgo.shared.domain.events.SpotgoDomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Publishes in-process domain events; transactional listeners receive them after commit. */
@Component
public class SpotgoDomainEventPublisher {
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SpotgoDomainEventPublisher(ApplicationEventPublisher events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    public void publish(String type, String sourceContext, String aggregateType, UUID aggregateId,
                        UUID tenantId, List<UUID> recipientAccountIds, Map<String, String> data) {
        events.publishEvent(new SpotgoDomainEvent(UUID.randomUUID(), type, sourceContext, aggregateType,
                aggregateId, tenantId, recipientAccountIds, data, clock.instant()));
    }
}
