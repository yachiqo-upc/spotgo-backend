package com.yachiqo.spotgo.parking.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;
import com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.entities.UuidIdentifiedAggregateRoot;

@Entity
@Table(name = "parkings", indexes = {
    @Index(name = "idx_parking_adminId", columnList = "adminId")
})
@Getter
public class Parking extends UuidIdentifiedAggregateRoot<Parking> {

    @Column(name = "adminId")
    private String adminId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column
    private String city;

    @Column(name = "totalSpaces", nullable = false)
    private Integer totalSpaces;

    @Column(name = "availableSpaces")
    private Integer availableSpaces;

    @Column(name = "totalFloors")
    private Integer totalFloors;

    @Column
    private Double averageOccupancy;

    @Column
    private Double occupancyTrendPercent;

    @Column
    private String peakHour;

    @Column
    private Double totalRevenue;

    @Column
    private String systemStatus;

    @Column
    private Double rating;

    @Column(name = "pricePerHour")
    private Double pricePerHour;

    @Column
    private Double revenueTrendPercent;

    @Column
    private Integer totalCapacity;

    @Column
    private Double efficiencyIndex;

    public Parking() {
    }

    public Parking(String adminId, String name, String address, String city,
                   Integer totalSpaces, Integer availableSpaces, Integer totalFloors,
                   Double averageOccupancy, Double occupancyTrendPercent, String peakHour,
                   Double totalRevenue, String systemStatus, Double rating, Double pricePerHour,
                   Double revenueTrendPercent, Integer totalCapacity, Double efficiencyIndex) {
        this.adminId = adminId;
        this.name = name;
        this.address = address;
        this.city = city;
        this.totalSpaces = totalSpaces;
        this.availableSpaces = availableSpaces;
        this.totalFloors = totalFloors;
        this.averageOccupancy = averageOccupancy;
        this.occupancyTrendPercent = occupancyTrendPercent;
        this.peakHour = peakHour;
        this.totalRevenue = totalRevenue;
        this.systemStatus = systemStatus;
        this.rating = rating;
        this.pricePerHour = pricePerHour;
        this.revenueTrendPercent = revenueTrendPercent;
        this.totalCapacity = totalCapacity;
        this.efficiencyIndex = efficiencyIndex;
    }

    public void updateRating(Double rating) {
        this.rating = rating;
    }

    public void updateStats(Integer totalSpaces, Integer availableSpaces, Integer totalFloors, String city, Double rating, Double pricePerHour) {
        if (totalSpaces != null) this.totalSpaces = totalSpaces;
        if (availableSpaces != null) this.availableSpaces = availableSpaces;
        if (totalFloors != null) this.totalFloors = totalFloors;
        if (city != null) this.city = city;
        if (rating != null) this.rating = rating;
        if (pricePerHour != null) this.pricePerHour = pricePerHour;
    }
}
