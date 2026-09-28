package com.yachiqo.spotgo.monitoring.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;
import com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.entities.UuidIdentifiedAggregateRoot;

@Entity
@Table(name = "weeklyTrends", indexes = {
    @Index(name = "idx_weeklyTrend_parkingId", columnList = "parkingId")
})
@Getter
public class WeeklyTrend extends UuidIdentifiedAggregateRoot<WeeklyTrend> {

    @Column(name = "parkingId", nullable = false)
    private String parkingId;

    @Column(nullable = false)
    private String day;

    @Column(nullable = false)
    private Double value;

    public WeeklyTrend() {
    }

    public WeeklyTrend(String parkingId, String day, Double value) {
        this.parkingId = parkingId;
        this.day = day;
        this.value = value;
    }
}
