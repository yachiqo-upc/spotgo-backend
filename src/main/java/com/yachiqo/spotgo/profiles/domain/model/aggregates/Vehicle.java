package com.yachiqo.spotgo.profiles.domain.model.aggregates;

import com.yachiqo.spotgo.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.VehicleStatus;
import static com.yachiqo.spotgo.profiles.domain.model.valueobjects.ProfileText.required;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
@Getter
public class Vehicle extends AbstractDomainAggregateRoot<Vehicle> {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, updatable = false)
    private Profile profile;
    @Column(name = "plate", unique = true)
    private String plate;
    @Column(name = "make", nullable = false)
    private String make;
    @Column(name = "model", nullable = false)
    private String model;
    @Column(name = "color", nullable = false)
    private String color;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false)
    private VehicleStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Vehicle() {}
    public Vehicle(Profile profile, String plate, String make, String model, String color) {
        this.profile = Objects.requireNonNull(profile, "Profile is required.");
        this.plate = normalizePlate(plate);
        this.make = required(make, "Marca");
        this.model = required(model, "Modelo");
        this.color = required(color, "Color");
        this.status = VehicleStatus.ACTIVE;
    }
    public UUID getProfileId() { return profile.getId(); }
    public static String normalizePlate(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
    public void update(String plate, String make, String model, String color, VehicleStatus status) {
        if (plate != null) this.plate = normalizePlate(plate);
        if (make != null) this.make = required(make, "Marca");
        if (model != null) this.model = required(model, "Modelo");
        if (color != null) this.color = required(color, "Color");
        if (status != null) this.status = status;
    }
    @PrePersist protected void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = Instant.now(); }
}
