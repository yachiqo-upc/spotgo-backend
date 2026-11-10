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

@Check(constraints="quality >= 0 and quality <= 100 and received_at >= occurred_at")
@Entity @Table(name="occupancy_readings",uniqueConstraints=@UniqueConstraint(columnNames={"sensor_id","payload_hash"})) @Getter
public class OccupancyReading {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="reading_id",nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="sensor_id",nullable=false,updatable=false) private OccupancySensor sensor; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="spot_id",nullable=false,updatable=false) private ParkingSpot spot;
 @Enumerated(EnumType.STRING) @Column(name="occupancy_status",nullable=false) private OccupancyStatus occupancyStatus;
 @Column(name="occurred_at",nullable=false) private Instant occurredAt; @Column(name="received_at",nullable=false) private Instant receivedAt;
 @Column(nullable=false,precision=5,scale=2) private BigDecimal quality;
 @Column(name="payload_hash",nullable=false,length=64,updatable=false) private String payloadHash;
 protected OccupancyReading(){}
 
 public OccupancyReading(OccupancySensor sensor,OccupancyStatus status,Instant occurred,Instant received,BigDecimal quality,String hash){
  this.sensor=Objects.requireNonNull(sensor);spot=sensor.getSpot();occupancyStatus=Objects.requireNonNull(status);occurredAt=instant(occurred);receivedAt=instant(received);
  if(quality==null||quality.signum()<0||quality.compareTo(BigDecimal.valueOf(100))>0||quality.scale()>2)throw new IllegalArgumentException("quality must be between 0 and 100 with at most two decimal places.");
  this.quality=quality.setScale(2);payloadHash=text(hash);
 }
 public UUID getSensorId(){return sensor.getId();} public UUID getSpotId(){return spot.getId();}

}
