package com.yachiqo.spotgo.parking.domain.model.aggregates;

import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReportStatus;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReportType;
import com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.entities.UuidIdentifiedAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;

@Entity
@Table(name = "clientReports", indexes = {
    @Index(name = "idx_clientReport_clientId", columnList = "clientId"),
    @Index(name = "idx_clientReport_parkingId", columnList = "parkingId"),
    @Index(name = "idx_clientReport_reservationId", columnList = "reservationId")
})
@Getter
public class ClientReport extends UuidIdentifiedAggregateRoot<ClientReport> {

    @Column(name = "clientId", nullable = false)
    private String clientId;

    @Column(name = "parkingId", nullable = false)
    private String parkingId;

    @Column(name = "reservationId", nullable = false)
    private String reservationId;

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportType type;

    @Column(nullable = false)
    private Instant date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    public ClientReport() {
    }

    // status always starts as SUBMITTED; it only changes later via PATCH
    public ClientReport(String clientId, String parkingId, String reservationId, String code, ReportType type, Instant date) {
        this.clientId = clientId;
        this.parkingId = parkingId;
        this.reservationId = reservationId;
        this.code = code;
        this.type = type;
        this.date = date;
        this.status = ReportStatus.SUBMITTED;
    }

    public void updateStatus(ReportStatus newStatus) {
        this.status = newStatus;
    }
}
