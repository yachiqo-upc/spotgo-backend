package com.yachiqo.spotgo.iam.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_entries")
@Getter
public class AuditEntry {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "audit_id", nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id") private UserAccount account;
    @Column(name = "action", nullable = false) private String action;
    @Column(name = "result", nullable = false) private String result;
    @Column(name = "source", nullable = false) private String source;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;
    @Column(name = "correlation_id", nullable = false) private String correlationId;
    protected AuditEntry() {}
    public AuditEntry(UserAccount account, String action, String result, String source, Instant now) {
        this.account = account; this.action = action; this.result = result;
        this.source = source == null ? "API" : source.substring(0, Math.min(255, source.length()));
        this.occurredAt = now; this.correlationId = UUID.randomUUID().toString();
    }
    public UUID getAccountId() { return account == null ? null : account.getId(); }
}
