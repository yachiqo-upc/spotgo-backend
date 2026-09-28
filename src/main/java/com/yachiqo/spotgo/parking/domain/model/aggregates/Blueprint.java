package com.yachiqo.spotgo.parking.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;
import com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.entities.UuidIdentifiedAggregateRoot;

@Entity
@Table(name = "blueprints", indexes = {
    @Index(name = "idx_blueprint_adminId", columnList = "adminId"),
    @Index(name = "idx_blueprint_parkingId", columnList = "parkingId")
})
@Getter
public class Blueprint extends UuidIdentifiedAggregateRoot<Blueprint> {

    @Column(name = "adminId")
    private String adminId;

    @Column(name = "parkingId", nullable = false)
    private String parkingId;

    @Column(name = "name")
    private String name;

    @Column(name = "dataUrl", columnDefinition = "TEXT")
    private String dataUrl;

    public Blueprint() {
    }

    public Blueprint(String adminId, String parkingId, String name, String dataUrl) {
        this.adminId = adminId;
        this.parkingId = parkingId;
        this.name = name;
        this.dataUrl = dataUrl;
    }
}
