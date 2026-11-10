package com.yachiqo.spotgo.parking.domain.model.aggregates;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.*;
import java.math.BigDecimal;
import static com.yachiqo.spotgo.parking.domain.model.valueobjects.ParkingValues.*;

@Entity @Table(name="reservations") @Getter
public class Reservation {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="reservation_id", nullable=false, updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="tenant_id",nullable=false,updatable=false) private Tenant tenant;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="zone_id",nullable=false,updatable=false) private ParkingZone zone;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="spot_id",nullable=false,updatable=false) private ParkingSpot spot;

 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="profile_id",nullable=false,updatable=false)
 private com.yachiqo.spotgo.profiles.domain.model.aggregates.Profile profile;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="vehicle_id",nullable=false,updatable=false)
 private com.yachiqo.spotgo.profiles.domain.model.aggregates.Vehicle vehicle;
 // UUID exposed by the API; the read-only association enforces the payment FK.
 @Column(name="payment_id",unique=true) private UUID paymentId;
 @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="payment_id",insertable=false,updatable=false,unique=true)
 private com.yachiqo.spotgo.billing.domain.model.aggregates.DigitalPayment payment;
 @Column(name="start_at",nullable=false) private Instant startAt;
 @Column(name="end_at",nullable=false) private Instant endAt;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal amount;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private ReservationStatus status;
 @Enumerated(EnumType.STRING) @Column(name="lock_status",nullable=false) private LockStatus lockStatus;
 @Column(name="locked_at") private Instant lockedAt;
 @Column(name="lock_expires_at") private Instant lockExpiresAt;
  @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @PrePersist void created() { createdAt=Instant.now(); updatedAt=createdAt; }
 @PreUpdate void updated() { updatedAt=Instant.now(); }

 protected Reservation() {}
 public Reservation(ParkingSpot spot,com.yachiqo.spotgo.profiles.domain.model.aggregates.Profile profile,
  com.yachiqo.spotgo.profiles.domain.model.aggregates.Vehicle vehicle,Instant start,Instant end,BigDecimal amount,Instant now,int lockMinutes) {
  interval(start,end);this.spot=spot;this.zone=spot.getZone();this.tenant=zone.getTenant();this.profile=profile;this.vehicle=vehicle;
  this.startAt=start;this.endAt=end;this.amount=Objects.requireNonNull(amount);this.status=ReservationStatus.PENDING_PAYMENT;
  this.lockStatus=LockStatus.ACTIVE;this.lockedAt=now;this.lockExpiresAt=now.plusSeconds(lockMinutes*60L);
 }
 public UUID getTenantId() { return tenant.getId(); }
 public UUID getZoneId() { return zone.getId(); }
 public UUID getSpotId() { return spot.getId(); }
 public UUID getProfileId() { return profile.getId(); }
 public UUID getVehicleId() { return vehicle.getId(); }
 public boolean blocks(Instant now) {
  return status==ReservationStatus.RESERVED || status==ReservationStatus.ACTIVE || status==ReservationStatus.OVERSTAYED ||
   (status==ReservationStatus.PENDING_PAYMENT && lockStatus==LockStatus.ACTIVE && lockExpiresAt!=null && now.isBefore(lockExpiresAt));
 }
 public void expire(Instant now) {
  if(status==ReservationStatus.PENDING_PAYMENT && !now.isBefore(lockExpiresAt)) {
   status=ReservationStatus.CANCELLED;lockStatus=LockStatus.EXPIRED;
  }
 }
 public void attachPayment(UUID payment) {
  if(status!=ReservationStatus.PENDING_PAYMENT || (paymentId!=null&&!paymentId.equals(payment))) throw new IllegalArgumentException("La reserva ya tiene una operacion de pago");
  paymentId=Objects.requireNonNull(payment);
 }
 public void confirm(UUID payment,Instant now) {
  if(status!=ReservationStatus.PENDING_PAYMENT || !blocks(now)) throw new IllegalArgumentException("El bloqueo de la reserva ya no esta vigente");
  paymentId=Objects.requireNonNull(payment);status=ReservationStatus.RESERVED;lockStatus=LockStatus.CONFIRMED;
 }
 public void cancel() {
  if(status!=ReservationStatus.PENDING_PAYMENT && status!=ReservationStatus.RESERVED) throw new IllegalArgumentException("Solo puedes cancelar una reserva pendiente o confirmada");
  status=ReservationStatus.CANCELLED;lockStatus=LockStatus.RELEASED;
 }
 public void start() { if(status!=ReservationStatus.RESERVED) throw new IllegalArgumentException("La reserva debe estar confirmada");status=ReservationStatus.ACTIVE; }
 public void complete(boolean overstay) { status=overstay?ReservationStatus.OVERSTAYED:ReservationStatus.COMPLETED; }
 public void noShow() { if(status!=ReservationStatus.RESERVED) throw new IllegalArgumentException("Estado incompatible");status=ReservationStatus.NO_SHOW; }

}
