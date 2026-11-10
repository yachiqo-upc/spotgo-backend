package com.yachiqo.spotgo.billing.infrastructure;
import com.yachiqo.spotgo.parking.application.ParkingPaymentGateway;
import com.yachiqo.spotgo.parking.domain.model.aggregates.*;
import com.yachiqo.spotgo.billing.application.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.UUID;
import java.time.Instant;
@Component
public class ParkingBillingAdapter implements ParkingPaymentGateway {
 private final BillingService billing;
 public ParkingBillingAdapter(BillingService billing){this.billing=billing;}
 public void requireApproved(UUID payment,UUID profile,BigDecimal amount){billing.requireApproved(payment,profile,amount);}
 public void lockProfile(UUID profile){billing.lockProfile(profile);}
 public void requireNoDebt(UUID profile){billing.requireNoDebt(profile);}
 public BigDecimal reservationPrice(UUID profile,BigDecimal base,Instant now){return billing.reservationPrice(profile,base,now);}
 public void cancelled(Reservation reservation){billing.cancelled(reservation);}
 public void completed(Reservation reservation,ParkingSession session){billing.completed(reservation,session);}
}
