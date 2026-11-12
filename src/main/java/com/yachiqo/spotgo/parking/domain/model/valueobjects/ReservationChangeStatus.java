package com.yachiqo.spotgo.parking.domain.model.valueobjects;

public enum ReservationChangeStatus {
    PENDING_PAYMENT,
    REFUND_SCHEDULED,
    REFUND_REQUESTED,
    APPLIED,
    REFUNDED,
    REFUND_FAILED,
    SUPERSEDED
}
