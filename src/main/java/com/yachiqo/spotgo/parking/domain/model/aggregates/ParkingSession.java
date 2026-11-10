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

@Entity @Table(name="parking_sessions") @Getter
public class ParkingSession {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="session_id", nullable=false, updatable=false) private UUID id;
 @OneToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="reservation_id",nullable=false,updatable=false,unique=true) private Reservation reservation;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="spot_id",nullable=false,updatable=false) private ParkingSpot spot;

 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="profile_id",nullable=false,updatable=false)
 private com.yachiqo.spotgo.profiles.domain.model.aggregates.Profile profile;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="vehicle_id",nullable=false,updatable=false)
 private com.yachiqo.spotgo.profiles.domain.model.aggregates.Vehicle vehicle;
 @Column(name="entry_at",nullable=false,updatable=false) private Instant entryAt;
 @Column(name="exit_at") private Instant exitAt;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private ParkingSessionStatus status;
 protected ParkingSession() {}
 public ParkingSession(Reservation reservation,Instant now) {
  this.reservation=reservation;spot=reservation.getSpot();profile=reservation.getProfile();vehicle=reservation.getVehicle();
  entryAt=now;status=ParkingSessionStatus.ACTIVE;
 }
 public void close(Instant now) {
  if(exitAt!=null) throw new IllegalArgumentException("The session has already ended.");
  if(now.isBefore(entryAt)) throw new IllegalArgumentException("Salida anterior a entrada");
  exitAt=now;status=ParkingSessionStatus.COMPLETED;
 }
 public UUID getReservationId() { return reservation.getId(); }
 public UUID getSpotId() { return spot.getId(); }
 public UUID getProfileId() { return profile.getId(); }
 public UUID getVehicleId() { return vehicle.getId(); }

}
