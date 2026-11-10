package com.yachiqo.spotgo.iam.domain.model.aggregates;

import com.yachiqo.spotgo.iam.domain.model.valueobjects.SessionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Check;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sessions")
@Check(constraints = "expires_at > created_at")
@Getter
public class AccessSession {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private UserAccount account;
    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64)
    private String refreshTokenHash;
    @Column(name = "last_activity_at", nullable = false) private Instant lastActivityAt;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private SessionStatus status;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    protected AccessSession() {}
    public AccessSession(UserAccount account, String hash, Instant now, Instant expiresAt) {
        this.account = Objects.requireNonNull(account);
        if (expiresAt == null || !expiresAt.isAfter(now)) throw new IllegalArgumentException("Expiracion de sesion invalida");
        this.refreshTokenHash = Objects.requireNonNull(hash);
        this.createdAt = this.lastActivityAt = now.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        this.expiresAt = expiresAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS); this.status = SessionStatus.ACTIVE;
    }
    public UUID getAccountId() { return account.getId(); }
    public boolean isUsableAt(Instant now) { return status == SessionStatus.ACTIVE && now.isBefore(expiresAt); }
    public void rotate(String hash, Instant now) { refreshTokenHash = Objects.requireNonNull(hash); lastActivityAt = now; }
    public void touch(Instant now) { lastActivityAt = now; }
    public void revoke(Instant now) { status = SessionStatus.REVOKED; revokedAt = now; }
}
