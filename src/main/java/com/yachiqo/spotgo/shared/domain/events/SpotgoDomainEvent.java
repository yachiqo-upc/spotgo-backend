package com.yachiqo.spotgo.shared.domain.events;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable event exchanged between bounded contexts inside the modular monolith. */
public record SpotgoDomainEvent(
        UUID eventId,
        String type,
        String sourceContext,
        String aggregateType,
        UUID aggregateId,
        UUID tenantId,
        List<UUID> recipientAccountIds,
        Map<String, String> data,
        Instant occurredAt) {

    public SpotgoDomainEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(sourceContext);
        Objects.requireNonNull(aggregateType);
        Objects.requireNonNull(aggregateId);
        recipientAccountIds = recipientAccountIds == null ? List.of() : recipientAccountIds.stream()
                .filter(Objects::nonNull).distinct().toList();
        data = data == null ? Map.of() : Map.copyOf(data);
        Objects.requireNonNull(occurredAt);
    }
}
