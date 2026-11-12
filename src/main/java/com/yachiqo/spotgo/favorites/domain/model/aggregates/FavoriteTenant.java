package com.yachiqo.spotgo.favorites.domain.model.aggregates;

import com.yachiqo.spotgo.parking.domain.model.aggregates.Tenant;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Profile;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "favorite_tenants",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_favorite_tenant_profile_tenant",
        columnNames = {"profile_id", "tenant_id"}
    )
)
@Getter
public class FavoriteTenant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "favorite_id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, updatable = false)
    private Profile profile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FavoriteTenant() {}

    public FavoriteTenant(Profile profile, Tenant tenant) {
        this.profile = Objects.requireNonNull(profile);
        this.tenant = Objects.requireNonNull(tenant);
    }

    @PrePersist
    void created() {
        createdAt = Instant.now();
    }

    public UUID getProfileId() {
        return profile.getId();
    }

    public UUID getTenantId() {
        return tenant.getId();
    }
}
