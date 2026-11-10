package com.yachiqo.spotgo.monitoring.domain.model.aggregates;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.*;
import java.time.Instant;
import java.math.*;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.*;
import com.yachiqo.spotgo.parking.domain.model.aggregates.*;
import static com.yachiqo.spotgo.monitoring.domain.model.valueobjects.MonitoringValues.*;

@Entity @Table(name="occupancy_conflicts") @Getter
public class OccupancyConflict {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="conflict_id",nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="spot_id",nullable=false,updatable=false) private ParkingSpot spot; @ManyToOne(fetch=FetchType.LAZY,optional=true) @JoinColumn(name="reservation_id",nullable=true,updatable=false) private Reservation reservation;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="reading_id",nullable=false,updatable=false) private OccupancyReading reading; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="tenant_id",nullable=false,updatable=false) private Tenant tenant;
 @Enumerated(EnumType.STRING) @Column(name="conflict_type",nullable=false) private ConflictType conflictType; @Enumerated(EnumType.STRING) @Column(name="severity",nullable=false) private Severity severity;
 @Column(name="detected_at",nullable=false) private Instant detectedAt; @Enumerated(EnumType.STRING) @Column(name="status",nullable=false) private ConflictStatus status;
 protected OccupancyConflict(){}
 
 public OccupancyConflict(OccupancyReading reading,Reservation reservation,ConflictType type,Instant now){
  this.reading=Objects.requireNonNull(reading);spot=reading.getSpot();tenant=spot.getZone().getTenant();
  if(reservation!=null&&!reservation.getSpotId().equals(spot.getId()))throw new IllegalArgumentException("Reserva de otro espacio");
  this.reservation=reservation;conflictType=Objects.requireNonNull(type);severity=Severity.HIGH;detectedAt=instant(now);status=ConflictStatus.OPEN;
 }
 public UUID getTenantId(){return tenant.getId();} public UUID getSpotId(){return spot.getId();} public UUID getReadingId(){return reading.getId();} public UUID getReservationId(){return reservation==null?null:reservation.getId();}
 public void acknowledge(){if(status==ConflictStatus.OPEN)status=ConflictStatus.ACKNOWLEDGED;}
 public void resolve(){status=ConflictStatus.RESOLVED;}

}
