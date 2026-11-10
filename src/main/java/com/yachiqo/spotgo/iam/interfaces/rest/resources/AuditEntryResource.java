package com.yachiqo.spotgo.iam.interfaces.rest.resources;
import com.yachiqo.spotgo.iam.domain.model.aggregates.AuditEntry;
import java.time.Instant;
import java.util.UUID;
public record AuditEntryResource(UUID id, UUID accountId, String action, String result, String source,
                                 Instant occurredAt, String correlationId) {
    public static AuditEntryResource from(AuditEntry a) {
        return new AuditEntryResource(a.getId(), a.getAccountId(), a.getAction(), a.getResult(), a.getSource(), a.getOccurredAt(), a.getCorrelationId());
    }
}
