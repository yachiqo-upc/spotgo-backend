package com.yachiqo.spotgo.iam.domain.model.aggregates;

import com.yachiqo.spotgo.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.AccountStatus;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
public class UserAccount extends AbstractDomainAggregateRoot<UserAccount> {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "email", nullable = false, unique = true)
    private String email;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false)
    private AccountStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    protected UserAccount() {}
    public UserAccount(String email) {
        this.email = normalizeEmail(email);
        if (this.email == null || this.email.isBlank()) throw new IllegalArgumentException("El correo es obligatorio");
        this.status = AccountStatus.ACTIVE;
    }
    public static String normalizeEmail(String email) { return email == null ? null : email.trim().toLowerCase(Locale.ROOT); }
    public void changeStatus(AccountStatus status) { this.status = Objects.requireNonNull(status); }
    public boolean isActive() { return status == AccountStatus.ACTIVE; }
    @PrePersist protected void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = Instant.now(); }
}
