package com.yachiqo.spotgo.profiles.domain.model.aggregates;

import com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.entities.UuidIdentifiedAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "favorites", indexes = {
    @Index(name = "idx_favorite_clientId", columnList = "clientId"),
    @Index(name = "idx_favorite_parkingId", columnList = "parkingId")
})
@Getter
public class Favorite extends UuidIdentifiedAggregateRoot<Favorite> {

    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String parkingId;

    @Column(nullable = false)
    private Double distanceMi;

    @Column(nullable = false)
    private String lastVisited;

    protected Favorite() {
    }

    public Favorite(String clientId, String parkingId, Double distanceMi, String lastVisited) {
        this.clientId = clientId;
        this.parkingId = parkingId;
        this.distanceMi = distanceMi;
        this.lastVisited = lastVisited;
    }
}
