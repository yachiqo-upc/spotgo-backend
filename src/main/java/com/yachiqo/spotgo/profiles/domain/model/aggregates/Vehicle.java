package com.yachiqo.spotgo.profiles.domain.model.aggregates;

import com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.entities.UuidIdentifiedAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "vehicles", indexes = {
    @Index(name = "idx_vehicle_clientId", columnList = "clientId")
})
@Getter
public class Vehicle extends UuidIdentifiedAggregateRoot<Vehicle> {

    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String licensePlate;

    @Column(nullable = false)
    private String vehicleType;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String model;

    protected Vehicle() {
    }

    public Vehicle(String clientId, String licensePlate, String vehicleType, String brand, String model) {
        this.clientId = clientId;
        this.licensePlate = licensePlate;
        this.vehicleType = vehicleType;
        this.brand = brand;
        this.model = model;
    }

    public void update(String licensePlate, String vehicleType, String brand, String model) {
        if (licensePlate != null) this.licensePlate = licensePlate;
        if (vehicleType != null) this.vehicleType = vehicleType;
        if (brand != null) this.brand = brand;
        if (model != null) this.model = model;
    }
}
