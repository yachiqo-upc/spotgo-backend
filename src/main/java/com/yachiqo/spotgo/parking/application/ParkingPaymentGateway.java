package com.yachiqo.spotgo.parking.application;
import java.math.BigDecimal;
import java.util.UUID;
/** Port: Billing must prove approval, ownership, operation and exact amount. */
public interface ParkingPaymentGateway {
 void requireApproved(UUID paymentId,UUID profileId,BigDecimal amount);
}
