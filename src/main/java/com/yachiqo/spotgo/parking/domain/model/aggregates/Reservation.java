package com.yachiqo.spotgo.parking.domain.model.aggregates;

import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationStatus;
import com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.entities.UuidIdentifiedAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservations", indexes = {
    @Index(name = "idx_reservation_clientId", columnList = "clientId"),
    @Index(name = "idx_reservation_parkingId", columnList = "parkingId"),
    @Index(name = "idx_reservation_code", columnList = "code")
})
@Getter
public class Reservation extends UuidIdentifiedAggregateRoot<Reservation> {

    @Column(name = "clientId")
    private String clientId;

    @Column(name = "parkingId")
    private String parkingId;

    @Column(name = "code")
    private String code;

    @Column(name = "spot")
    private String spot;

    @Column(name = "startDate")
    private LocalDateTime startDate;

    @Column(name = "endDate")
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "baseAmount")
    private Double baseAmount;

    @Column(name = "rating")
    private Double rating;

    public Reservation() {
    }

    public Reservation(String clientId, String parkingId, String code, String spot,
                       LocalDateTime startDate, LocalDateTime endDate,
                       Double amount, Double baseAmount, Double rating) {
        this.clientId = clientId;
        this.parkingId = parkingId;
        this.code = code;
        this.spot = spot;
        this.startDate = startDate;
        this.endDate = endDate;
        this.amount = amount;
        this.baseAmount = baseAmount;
        this.rating = rating;
        this.status = ReservationStatus.ACTIVE;
    }

    public void updateStatus(ReservationStatus newStatus) {
        this.status = newStatus;
    }

    public void updateDetails(LocalDateTime endDate, Double amount, Double baseAmount, Double rating, ReservationStatus status) {
        if (endDate != null) this.endDate = endDate;
        if (amount != null) this.amount = amount;
        if (baseAmount != null) this.baseAmount = baseAmount;
        if (rating != null) this.rating = rating;
        if (status != null) this.status = status;
    }

    public void updatePricing(Double amount, Double baseAmount) {
        this.amount = amount;
        this.baseAmount = baseAmount;
    }
}
