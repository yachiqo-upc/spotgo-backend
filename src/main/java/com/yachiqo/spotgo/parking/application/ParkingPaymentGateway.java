package com.yachiqo.spotgo.parking.application;
import com.yachiqo.spotgo.parking.domain.model.aggregates.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import java.math.BigDecimal;
import java.util.UUID;
import java.time.Instant;
/** Parking's port for payment approval, membership pricing, debts and post-stay billing. */
public interface ParkingPaymentGateway {
 record SpotBillingResult(UUID paymentId,BigDecimal amount,String currency,String status) {}
 void requireApproved(UUID paymentId,UUID profileId,BigDecimal amount);
 void lockProfile(UUID profileId);
 void requireNoDebt(UUID profileId);
 BigDecimal reservationPrice(UUID profileId,BigDecimal base,Instant now);
 void prepareSpotAddition(SpotgoUserPrincipal actor,UUID tenantId);
 SpotBillingResult billSpotAddition(SpotgoUserPrincipal actor,ParkingSpot spot);
 boolean canActivateSpot(UUID tenantId,UUID spotId);
 void cancelled(Reservation reservation);
 void completed(Reservation reservation,ParkingSession session);
}
