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

@Entity @Table(name="payment_attempts",uniqueConstraints=@UniqueConstraint(columnNames={"payment_id","attempt_number"})) @Getter
public class PaymentAttempt {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="attempt_id",nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="payment_id",nullable=false,updatable=false) private DigitalPayment payment;

 @Column(name="attempt_number",nullable=false,updatable=false) private int attemptNumber;
 @Column(name="started_at",nullable=false,updatable=false) private Instant startedAt;
 @Column(name="finished_at") private Instant finishedAt;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private PaymentAttemptStatus status;

 @Column(name="error_code") private String errorCode;
 @Column(name="next_attempt_at") private Instant nextAttemptAt;
 protected PaymentAttempt() {}
 public PaymentAttempt(DigitalPayment payment,int number,Instant now) {
  this.payment=Objects.requireNonNull(payment);if(number<1)throw new IllegalArgumentException("Invalid attempt number.");
  attemptNumber=number;startedAt=now;status=PaymentAttemptStatus.STARTED;
 }
 public UUID getPaymentId(){return payment.getId();}
 public void finish(PaymentAttemptStatus result,String error,Instant next,Instant now){
  status=Objects.requireNonNull(result);errorCode=error;nextAttemptAt=next;finishedAt=now;
 }

}
