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

@Entity @Table(name="refunds") @Getter
@Check(constraints="amount > 0")
public class Refund {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="refund_id",nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="payment_id",nullable=false,updatable=false) private DigitalPayment payment;

  @Column(name="amount",nullable=false,precision=12,scale=2) private BigDecimal amount;

 @Column(nullable=false) private String reason;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private RefundStatus status;

 @Column(name="provider_operation_ref",unique=true) private String providerOperationRef;
 @Column(name="requested_at",nullable=false,updatable=false) private Instant requestedAt;
 @Column(name="completed_at") private Instant completedAt;
 protected Refund() {}
 public Refund(DigitalPayment payment,BigDecimal amount,String reason,Instant now){
  this.payment=Objects.requireNonNull(payment);this.amount=positive(amount);this.reason=text(reason);requestedAt=now;status=RefundStatus.REQUESTED;
 }
 public UUID getPaymentId(){return payment.getId();}
 public void result(RefundStatus next,String ref,Instant now){status=next;if(ref!=null)providerOperationRef=text(ref);if(next==RefundStatus.COMPLETED)completedAt=now;}

}
