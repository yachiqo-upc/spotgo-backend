package com.yachiqo.spotgo.iam.domain.model.aggregates;

import com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.entities.UuidIdentifiedAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "passwordResetCodes", indexes = {
        @Index(name = "idx_passwordResetCode_email", columnList = "email", unique = true)
})
@Getter
public class PasswordResetCode extends UuidIdentifiedAggregateRoot<PasswordResetCode> {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Integer attempts;

    @Column(nullable = false)
    private Instant requestedAt;

    @Column
    private Instant usedAt;

    protected PasswordResetCode() {
    }

    public PasswordResetCode(String email, String codeHash, Instant expiresAt, Instant requestedAt) {
        this.email = UserAccount.normalizeEmail(email);
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.requestedAt = requestedAt;
        this.attempts = 0;
    }

    public void renew(String codeHash, Instant expiresAt, Instant requestedAt) {
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.requestedAt = requestedAt;
        this.attempts = 0;
        this.usedAt = null;
    }

    public void registerFailedAttempt() {
        this.attempts = this.attempts + 1;
    }

    public void markUsed(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public boolean isExpiredAt(Instant now) {
        return expiresAt.isBefore(now);
    }

    public boolean isUsed() {
        return usedAt != null;
    }
}
