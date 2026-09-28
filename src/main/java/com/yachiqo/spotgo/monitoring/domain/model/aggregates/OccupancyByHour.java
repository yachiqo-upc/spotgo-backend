package com.yachiqo.spotgo.monitoring.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;
import com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.entities.UuidIdentifiedAggregateRoot;

@Entity
@Table(name = "occupancyByHour", indexes = {
    @Index(name = "idx_occupancyByHour_parkingId", columnList = "parkingId")
})
@Getter
public class OccupancyByHour extends UuidIdentifiedAggregateRoot<OccupancyByHour> {

    @Column(name = "parkingId", nullable = false)
    private String parkingId;

    @Column(nullable = false)
    private String hour;

    @Column(nullable = false)
    private Integer intensity;

    public OccupancyByHour() {
    }

    public OccupancyByHour(String parkingId, String hour, Integer intensity) {
        this.parkingId = parkingId;
        this.hour = hour;
        this.intensity = intensity;
    }
}
