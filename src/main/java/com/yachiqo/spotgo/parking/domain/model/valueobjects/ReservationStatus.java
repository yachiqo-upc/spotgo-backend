package com.yachiqo.spotgo.parking.domain.model.valueobjects;

public enum ReservationStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED;

    public static ReservationStatus fromDisplayName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Reservation status cannot be null");
        }
        try {
            return ReservationStatus.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid reservation status: " + value, e);
        }
    }
}
