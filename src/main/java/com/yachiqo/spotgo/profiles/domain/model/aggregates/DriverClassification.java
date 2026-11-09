package com.yachiqo.spotgo.profiles.domain.model.aggregates;

import com.yachiqo.spotgo.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.DriverProfileType;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "driver_classifications", uniqueConstraints =
    @UniqueConstraint(name = "uk_classification_profile_tenant", columnNames = {"profile_id", "tenant_id"}))
@Getter
public class DriverClassification extends AbstractDomainAggregateRoot<DriverClassification> {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "classification_id", nullable = false, updatable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, updatable = false)
    private Profile profile;
    // Logical reference until Parking is migrated to tenants with native UUID keys.
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Enumerated(EnumType.STRING) @Column(name = "profile_type", nullable = false)
    private DriverProfileType profileType;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false)
    private Profile assignedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    protected DriverClassification() {}
    public DriverClassification(Profile profile, UUID tenantId, DriverProfileType type, Profile administrator) {
        this.profile = Objects.requireNonNull(profile);
        this.tenantId = Objects.requireNonNull(tenantId);
        assign(type, administrator);
    }
    public void assign(DriverProfileType type, Profile administrator) {
        this.profileType = type == null ? DriverProfileType.PRIVATE : type;
        this.assignedBy = Objects.requireNonNull(administrator);
    }
    public UUID getProfileId() { return profile.getId(); }
    public UUID getAssignedById() { return assignedBy.getId(); }
    @PrePersist protected void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = Instant.now(); }
}
