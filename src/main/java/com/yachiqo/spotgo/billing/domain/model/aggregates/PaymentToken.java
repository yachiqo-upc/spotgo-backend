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

@Entity @Table(name="payment_tokens") @Getter
@Check(constraints="expiration_month between 1 and 12")
public class PaymentToken {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="token_id",nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="profile_id",nullable=false,updatable=false) private Profile profile;

 @Column(name="provider_token_ref",nullable=false,unique=true,updatable=false) private String providerTokenRef;
 @Column(nullable=false) private String brand;
 @Column(name="last_four",nullable=false,length=4) private String lastFour;
 @Column(name="expiration_month",nullable=false) private short expirationMonth;
 @Column(name="expiration_year",nullable=false) private short expirationYear;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private PaymentTokenStatus status;

 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="revoked_at") private Instant revokedAt;
 protected PaymentToken() {}
 public PaymentToken(Profile profile,String ref,String brand,String lastFour,int month,int year,Instant now) {
  if(lastFour==null||!lastFour.matches("[0-9]{4}")||month<1||month>12||year<2000||year>2200)throw new IllegalArgumentException("Invalid summarized card details.");
  this.profile=Objects.requireNonNull(profile);providerTokenRef=text(ref);this.brand=text(brand);this.lastFour=lastFour;
  expirationMonth=(short)month;expirationYear=(short)year;createdAt=now;status=PaymentTokenStatus.ACTIVE;
 }
 public UUID getProfileId(){return profile.getId();}
 public void revoke(Instant now){status=PaymentTokenStatus.REVOKED;revokedAt=now;}
 public boolean usable(Instant now){
  var expiry=java.time.YearMonth.of(expirationYear,expirationMonth);
  return status==PaymentTokenStatus.ACTIVE && !expiry.isBefore(java.time.YearMonth.from(now.atZone(java.time.ZoneOffset.UTC)));
 }

}
