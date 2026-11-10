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

@Check(constraints="recovered_at is null or recovered_at >= observed_at")
@Entity @Table(name="sensor_health_events") @Getter
public class SensorHealthEvent {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="health_event_id",nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="sensor_id",nullable=false,updatable=false) private OccupancySensor sensor; @Enumerated(EnumType.STRING) @Column(name="health",nullable=false) private SensorHealth health;
 @Column(nullable=false) private String reason;
 @Column(name="observed_at",nullable=false) private Instant observedAt; @Column(name="recovered_at",nullable=true) private Instant recoveredAt;
 protected SensorHealthEvent(){}
 
 public SensorHealthEvent(OccupancySensor sensor,SensorHealth health,String reason,Instant observed){
  this.sensor=Objects.requireNonNull(sensor);this.health=Objects.requireNonNull(health);this.reason=text(reason);observedAt=instant(observed);if(health==SensorHealth.HEALTHY)recoveredAt=observed;
 }
 public UUID getSensorId(){return sensor.getId();}
 public void recover(Instant now){if(now.isBefore(observedAt))throw new IllegalArgumentException("Recovery time is earlier than the health event.");if(recoveredAt==null)recoveredAt=now;}

}
