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

@Entity @Table(name="occupancy_sensors") @Getter
public class OccupancySensor {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="sensor_id",nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="tenant_id",nullable=false,updatable=false) private Tenant tenant; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="zone_id",nullable=false,updatable=false) private ParkingZone zone; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="spot_id",nullable=false,updatable=false) private ParkingSpot spot;
 @Enumerated(EnumType.STRING) @Column(name="protocol",nullable=false) private SensorProtocol protocol; @Enumerated(EnumType.STRING) @Column(name="status",nullable=false) private SensorStatus status;
 @Column(name="last_seen_at",nullable=false) private Instant lastSeenAt;
 @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb",nullable=false) private Map<String,Object> configuration;
 @Column(name="installed_at",nullable=false) private Instant installedAt;
 protected OccupancySensor(){}
 
 public OccupancySensor(ParkingSpot spot,SensorProtocol protocol,Map<String,Object> configuration,Instant installed,Instant received){
  this.spot=Objects.requireNonNull(spot);zone=spot.getZone();tenant=zone.getTenant();this.protocol=Objects.requireNonNull(protocol);this.configuration=new LinkedHashMap<>(Objects.requireNonNull(configuration));installedAt=instant(installed);lastSeenAt=instant(received);status=SensorStatus.ACTIVE;
 }
 public UUID getTenantId(){return tenant.getId();} public UUID getZoneId(){return zone.getId();} public UUID getSpotId(){return spot.getId();}
 public void seen(Instant received){if(received.isAfter(lastSeenAt))lastSeenAt=received;}
 public void deactivate(){status=SensorStatus.INACTIVE;}

}
