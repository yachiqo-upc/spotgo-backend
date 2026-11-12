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

@Entity @Table(name="digital_payments") @Getter
@Check(constraints="amount > 0")
public class DigitalPayment {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="payment_id",nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=true) @JoinColumn(name="token_id",nullable=true) private PaymentToken token;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="profile_id",nullable=false,updatable=false) private Profile profile;

 @Enumerated(EnumType.STRING) @Column(name="operation_type",nullable=false,updatable=false) private PaymentOperationType operationType;
  @Column(name="amount",nullable=false,precision=12,scale=2) private BigDecimal amount;

 @Column(nullable=false,length=3,updatable=false) private String currency;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private PaymentStatus status;

 @Column(name="provider_operation_ref",unique=true) private String providerOperationRef;
 @Column(name="idempotency_key",nullable=false,unique=true,updatable=false) private String idempotencyKey;
 @Column(name="request_hash",nullable=false,length=64,updatable=false) private String requestHash;
 @Column(name="first_seen_at",nullable=false,updatable=false) private Instant firstSeenAt;
 @Column(name="last_seen_at",nullable=false) private Instant lastSeenAt;
 @Column(name="result_reference",nullable=false,length=8192) private String resultReference;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="approved_at") private Instant approvedAt;
 protected DigitalPayment() {}
 public DigitalPayment(Profile profile,PaymentToken token,PaymentOperationType type,BigDecimal amount,String key,String hash,Instant now) {
  this.profile=Objects.requireNonNull(profile);this.token=token;operationType=Objects.requireNonNull(type);this.amount=positive(amount);
  currency="PEN";status=PaymentStatus.INITIATED;idempotencyKey=text(key);requestHash=text(hash);firstSeenAt=lastSeenAt=createdAt=now;resultReference="PENDING";
 }
 public UUID getProfileId(){return profile.getId();}
 public UUID getTokenId(){return token==null?null:token.getId();}
 public Profile getProfile(){return profile;}
 public void subscriptionRequest(String reference){if(operationType!=PaymentOperationType.SUBSCRIPTION||status!=PaymentStatus.INITIATED||reference==null||reference.length()>8192)throw new IllegalArgumentException("Invalid subscription request.");resultReference=reference;}
 public void subscriptionAdjustmentRequest(String reference){if(operationType!=PaymentOperationType.SUBSCRIPTION_ADJUSTMENT||status!=PaymentStatus.INITIATED||reference==null||reference.length()>8192)throw new IllegalArgumentException("Invalid subscription adjustment request.");resultReference=reference;}
 public void reservationChangeRequest(UUID changeId){
  if(operationType!=PaymentOperationType.RESERVATION_ADJUSTMENT||status!=PaymentStatus.INITIATED||changeId==null)
   throw new IllegalArgumentException("Invalid reservation change request.");
  resultReference="reservation-change:"+changeId;
 }
 public void replaceToken(PaymentToken next){if(status!=PaymentStatus.REJECTED||next==null)throw new IllegalArgumentException("A different token can be used only for a declined payment.");token=next;}
 public void seen(Instant now){lastSeenAt=now;}
 public void pending(){status=PaymentStatus.PENDING;}
 public void result(PaymentStatus next,String providerRef,String result,Instant now){
  if(status==PaymentStatus.APPROVED && next!=PaymentStatus.APPROVED)throw new IllegalArgumentException("An approved payment cannot transition to an earlier state.");
  status=next;if(providerRef!=null)providerOperationRef=text(providerRef);
  if(result==null||result.isBlank()||result.length()>8192)throw new IllegalArgumentException("Invalid reference.");resultReference=result;lastSeenAt=now;if(next==PaymentStatus.APPROVED&&approvedAt==null)approvedAt=now;
 }

}
