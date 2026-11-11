package com.yachiqo.spotgo.monitoring.domain.model.aggregates;

import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.AlertStatus;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.Severity;
import com.yachiqo.spotgo.parking.domain.model.aggregates.ParkingZone;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static com.yachiqo.spotgo.monitoring.domain.model.valueobjects.MonitoringValues.instant;

@Entity
@Table(name = "capacity_alerts")
@Getter
public class CapacityAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "capacity_alert_id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", updatable = false)
    private ParkingZone zone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false, updatable = false)
    private CapacitySnapshot snapshot;

    @Column(name = "threshold_percentage", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal thresholdPercentage;

    @Column(name = "occupancy_percentage", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal occupancyPercentage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected CapacityAlert() {}

    public CapacityAlert(CapacitySnapshot snapshot, BigDecimal thresholdPercentage, Instant now) {
        this.snapshot = Objects.requireNonNull(snapshot);
        this.tenant = snapshot.getTenant();
        this.zone = snapshot.getZone();
        this.thresholdPercentage = Objects.requireNonNull(thresholdPercentage);
        this.occupancyPercentage = snapshot.getPercentage();
        if (occupancyPercentage.compareTo(thresholdPercentage) <= 0)
            throw new IllegalArgumentException("A capacity alert requires occupancy above its threshold.");
        this.severity = Severity.CRITICAL;
        this.status = AlertStatus.OPEN;
        this.createdAt = instant(now);
    }

    public UUID getTenantId() {
        return tenant.getId();
    }

    public UUID getZoneId() {
        return zone == null ? null : zone.getId();
    }

    public UUID getSnapshotId() {
        return snapshot.getId();
    }

    public void acknowledge(Instant now) {
        if (status == AlertStatus.OPEN) {
            acknowledgedAt = instant(now);
            status = AlertStatus.ACKNOWLEDGED;
        }
    }

    public void resolve(Instant now) {
        if (status != AlertStatus.CLOSED) {
            resolvedAt = instant(now);
            status = AlertStatus.CLOSED;
        }
    }
}
