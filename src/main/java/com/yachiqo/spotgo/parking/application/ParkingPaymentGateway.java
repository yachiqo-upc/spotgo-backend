package com.yachiqo.spotgo.parking.application;
import com.yachiqo.spotgo.parking.domain.model.aggregates.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.time.Instant;
/** Parking's port for payment approval, membership pricing, debts and post-stay billing. */
public interface ParkingPaymentGateway {
 void requireApproved(UUID paymentId,UUID profileId,BigDecimal amount);
 void lockProfile(UUID profileId);
 void requireNoDebt(UUID profileId);
 BigDecimal reservationPrice(UUID profileId,BigDecimal base,Instant now);
 void cancelled(Reservation reservation);
 void completed(Reservation reservation,ParkingSession session);
}