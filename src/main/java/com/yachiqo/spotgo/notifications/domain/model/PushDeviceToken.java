package com.yachiqo.spotgo.notifications.domain.model;

import com.yachiqo.spotgo.iam.domain.model.aggregates.Account;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "push_device_tokens", indexes = @Index(name = "ix_push_device_account", columnList = "account_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_push_device_token", columnNames = "device_token"))
@Getter
public class PushDeviceToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "device_token_id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "device_token", nullable = false, length = 4096)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    private DevicePlatform platform;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected PushDeviceToken() {}

    public PushDeviceToken(Account account, String token, DevicePlatform platform, Instant now) {
        register(account, token, platform, now);
        createdAt = now;
    }

    public void register(Account account, String token, DevicePlatform platform, Instant now) {
        this.account = Objects.requireNonNull(account);
        this.token = requiredToken(token);
        this.platform = Objects.requireNonNull(platform);
        this.lastSeenAt = Objects.requireNonNull(now);
    }

    public UUID getAccountId() {
        return account.getId();
    }

    private static String requiredToken(String token) {
        if (token == null || token.isBlank() || token.length() > 4096)
            throw new IllegalArgumentException("A valid Firebase device token is required.");
        return token.trim();
    }
}
