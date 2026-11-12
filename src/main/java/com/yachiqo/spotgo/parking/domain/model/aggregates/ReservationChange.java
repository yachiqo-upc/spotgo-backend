package com.yachiqo.spotgo.parking.domain.model.aggregates;

import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationChangeStatus;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "reservation_changes")
@Getter
public class ReservationChange {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "reservation_change_id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, updatable = false)
    private Reservation reservation;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "payment_id", unique = true)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationChangeStatus status;

    @Column(name = "refund_due_at")
    private Instant refundDueAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void created() { createdAt = updatedAt = Instant.now(); }

    @PreUpdate
    void updated() { updatedAt = Instant.now(); }

    protected ReservationChange() {}

    public ReservationChange(Reservation reservation, Instant startAt, Instant endAt,
                             BigDecimal amount, BigDecimal paymentAmount,
                             ReservationChangeStatus status, Instant refundDueAt) {
        this.reservation = Objects.requireNonNull(reservation);
        this.startAt = Objects.requireNonNull(startAt);
        this.endAt = Objects.requireNonNull(endAt);
        this.amount = Objects.requireNonNull(amount);
        this.paymentAmount = Objects.requireNonNull(paymentAmount);
        this.status = Objects.requireNonNull(status);
        this.refundDueAt = refundDueAt;
    }

    public UUID getReservationId() { return reservation.getId(); }
    public UUID getProfileId() { return reservation.getProfileId(); }

    public void attachPayment(UUID payment) {
        if (status != ReservationChangeStatus.PENDING_PAYMENT || paymentId != null)
            throw new IllegalArgumentException("The reservation change already has a payment operation.");
        paymentId = Objects.requireNonNull(payment);
    }

    public void applied() {
        if (status != ReservationChangeStatus.PENDING_PAYMENT)
            throw new IllegalArgumentException("The reservation change is not awaiting payment.");
        status = ReservationChangeStatus.APPLIED;
    }

    public void refundRequested() {
        if (status != ReservationChangeStatus.REFUND_SCHEDULED)
            throw new IllegalArgumentException("The reservation change has no scheduled refund.");
        status = ReservationChangeStatus.REFUND_REQUESTED;
    }

    public void refundResult(boolean complete, boolean failed) {
        if (complete) status = ReservationChangeStatus.REFUNDED;
        else if (failed) status = ReservationChangeStatus.REFUND_FAILED;
    }

    public void supersede() {
        if (status == ReservationChangeStatus.REFUND_SCHEDULED || status == ReservationChangeStatus.PENDING_PAYMENT)
            status = ReservationChangeStatus.SUPERSEDED;
    }

    public void paymentApproved() {
        if (status != ReservationChangeStatus.PENDING_PAYMENT)
            throw new IllegalArgumentException("The reservation change is not awaiting payment.");
        status = ReservationChangeStatus.APPLIED;
    }
}
