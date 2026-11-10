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

@Entity @Table(name="outstanding_balances") @Getter
@Check(constraints="amount >= 0")
public class OutstandingBalance {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="balance_id",nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="profile_id",nullable=false,updatable=false) private Profile profile;

 @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="charge_id",nullable=false,unique=true,updatable=false) private AdditionalCharge charge;
  @Column(name="amount",nullable=false,precision=12,scale=2) private BigDecimal amount;

 @Column(name="due_at",nullable=false) private Instant dueAt;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private OutstandingBalanceStatus status;

 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="regularized_at") private Instant regularizedAt;
 protected OutstandingBalance() {}
 public OutstandingBalance(AdditionalCharge charge,Instant due,Instant now){
  this.charge=Objects.requireNonNull(charge);profile=charge.getReservation().getProfile();amount=charge.getAmount();dueAt=due;
  status=OutstandingBalanceStatus.OPEN;createdAt=now;
 }
 public UUID getProfileId(){return profile.getId();}
 public UUID getChargeId(){return charge.getId();}
 public void regularize(Instant now){amount=BigDecimal.ZERO.setScale(2);status=OutstandingBalanceStatus.REGULARIZED;regularizedAt=now;}

}
