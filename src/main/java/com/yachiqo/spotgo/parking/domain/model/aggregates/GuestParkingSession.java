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

@Entity @Table(name="guest_parking_sessions") @Getter
public class GuestParkingSession {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="guest_session_id", nullable=false, updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="tenant_id",nullable=false,updatable=false) private Tenant tenant;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="zone_id",nullable=false,updatable=false) private ParkingZone zone;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="spot_id",nullable=false,updatable=false) private ParkingSpot spot;

 @Column(name="manual_plate",nullable=false) private String manualPlate;
 @Column(name="entry_at",nullable=false,updatable=false) private Instant entryAt;
 @Column(name="exit_at") private Instant exitAt;
 @Column(name="calculated_amount",nullable=false,precision=12,scale=2) private BigDecimal calculatedAmount;
 @Enumerated(EnumType.STRING) @Column(name="physical_payment_method",nullable=false) private PhysicalPaymentMethod physicalPaymentMethod;
 @Column(name="physical_payment_confirmed",nullable=false) private boolean physicalPaymentConfirmed;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private GuestSessionStatus status;
 protected GuestParkingSession() {}
 public GuestParkingSession(ParkingSpot spot,String plate,PhysicalPaymentMethod method,Instant now) {
  this.spot=spot;this.zone=spot.getZone();this.tenant=zone.getTenant();manualPlate=text(plate).toUpperCase(Locale.ROOT);
  physicalPaymentMethod=Objects.requireNonNull(method);entryAt=now;calculatedAmount=BigDecimal.ZERO;status=GuestSessionStatus.OPEN;
 }
 public void close(Instant now,BigDecimal amount,boolean paid) {
  if(exitAt!=null) throw new IllegalArgumentException("La salida ya fue registrada");
  if(now.isBefore(entryAt)) throw new IllegalArgumentException("Salida anterior a entrada");
  exitAt=now;calculatedAmount=amount;physicalPaymentConfirmed=paid;status=paid?GuestSessionStatus.CLOSED:GuestSessionStatus.PENDING_REVIEW;
 }
 public void confirmPayment() {
  if(exitAt==null || physicalPaymentConfirmed) throw new IllegalArgumentException("No hay un pago fisico pendiente de confirmar");
  physicalPaymentConfirmed=true;status=GuestSessionStatus.CLOSED;
 }
 public UUID getTenantId() { return tenant.getId(); }
 public UUID getZoneId() { return zone.getId(); }
 public UUID getSpotId() { return spot.getId(); }

}
