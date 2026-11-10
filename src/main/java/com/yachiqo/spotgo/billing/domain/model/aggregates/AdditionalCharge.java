package com.yachiqo.spotgo.billing.domain.model.aggregates;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.*;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Profile;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Reservation;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Tenant;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
import static com.yachiqo.spotgo.billing.domain.model.valueobjects.BillingValues.*;

@Entity @Table(name="additional_charges") @Getter
@Check(constraints="amount > 0")
public class AdditionalCharge {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="charge_id",nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="reservation_id",nullable=false,updatable=false) private Reservation reservation;

 @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="payment_id",unique=true) private DigitalPayment payment;
  @Column(name="amount",nullable=false,precision=12,scale=2) private BigDecimal amount;

 @Column(nullable=false) private String reason;
 @Column(name="evidence_ref",nullable=false) private String evidenceRef;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private AdditionalChargeStatus status;

 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected AdditionalCharge() {}
 public AdditionalCharge(Reservation reservation,BigDecimal amount,String reason,String evidence,Instant now) {
  this.reservation=Objects.requireNonNull(reservation);this.amount=positive(amount);this.reason=text(reason);evidenceRef=text(evidence);
  createdAt=now;status=AdditionalChargeStatus.PENDING;
 }
 public UUID getReservationId(){return reservation.getId();}
 public UUID getPaymentId(){return payment==null?null:payment.getId();}
 public void attach(DigitalPayment payment){
  if(this.payment!=null&&!this.payment.getId().equals(payment.getId()))throw new IllegalArgumentException("The charge already has a payment operation.");
  this.payment=payment;
 }
 public void paid(){status=AdditionalChargeStatus.PAID;}

}
