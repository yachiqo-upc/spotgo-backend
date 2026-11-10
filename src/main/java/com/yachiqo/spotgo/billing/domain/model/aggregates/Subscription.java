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

@Entity @Table(name="subscriptions") @Getter
@Check(constraints="((profile_id is not null and tenant_id is null) or (profile_id is null and tenant_id is not null)) and (end_at is null or end_at >= start_at)")
public class Subscription {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="subscription_id",nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=true) @JoinColumn(name="profile_id",nullable=true,updatable=false) private Profile profile;
 @ManyToOne(fetch=FetchType.LAZY,optional=true) @JoinColumn(name="tenant_id",nullable=true,updatable=false) private Tenant tenant;

 @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="payment_id",nullable=false,unique=true,updatable=false) private DigitalPayment payment;
 @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb",nullable=false) private Map<String,Object> plan;
 @Column(name="start_at",nullable=false,updatable=false) private Instant startAt;
 @Column(name="end_at") private Instant endAt;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private SubscriptionStatus status;

 protected Subscription() {}
 public Subscription(Profile profile,Tenant tenant,Map<String,Object> plan,DigitalPayment payment,Instant now) {
  if((profile==null)==(tenant==null))throw new IllegalArgumentException("A subscription must have exactly one owner.");
  if(payment==null||payment.getStatus()!=PaymentStatus.APPROVED||payment.getOperationType()!=PaymentOperationType.SUBSCRIPTION)throw new IllegalArgumentException("A subscription requires an approved subscription payment.");this.payment=payment;this.profile=profile;this.tenant=tenant;this.plan=new LinkedHashMap<>(plan);startAt=now;status=SubscriptionStatus.ACTIVE;
 }
 public UUID getProfileId(){return profile==null?null:profile.getId();}
 public UUID getTenantId(){return tenant==null?null:tenant.getId();}
 public UUID getPaymentId(){return payment==null?null:payment.getId();}
 public void updatePlan(Map<String,Object> next){if(tenant==null||endAt!=null)throw new IllegalArgumentException("Only the plan for an active tenant subscription can be updated.");plan=new LinkedHashMap<>(Objects.requireNonNull(next));}
 public void pause(){if(endAt!=null)throw new IllegalArgumentException("The subscription period is closed.");status=SubscriptionStatus.PAUSED;}
 public void pastDue(){if(endAt!=null)throw new IllegalArgumentException("The subscription period is closed.");status=SubscriptionStatus.PAST_DUE;}
 public void close(Instant now){if(now.isBefore(startAt))throw new IllegalArgumentException("The end time is before the start time.");endAt=now;status=SubscriptionStatus.CANCELLED;}
 public void expire(Instant now){if(now.isBefore(startAt))throw new IllegalArgumentException("The end time is before the start time.");endAt=now;status=SubscriptionStatus.EXPIRED;}
 public boolean activeAt(Instant now){return status==SubscriptionStatus.ACTIVE && !now.isBefore(startAt) && (endAt==null||now.isBefore(endAt));}

}
