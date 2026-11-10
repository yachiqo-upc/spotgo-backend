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

@Entity @Table(name="parking_spots", uniqueConstraints=@UniqueConstraint(name="uk_spot_zone_code",columnNames={"zone_id","code"})) @Getter
public class ParkingSpot {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="spot_id", nullable=false, updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="zone_id",nullable=false,updatable=false) private ParkingZone zone;

 @Column(nullable=false) private String code;
 @Column(name="spot_type",nullable=false) private String spotType;
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="features",columnDefinition="jsonb",nullable=false) private Map<String,Object> features;

 @Enumerated(EnumType.STRING) @Column(name="operational_status",nullable=false) private OperationalStatus operationalStatus;
 protected ParkingSpot() {}
 public ParkingSpot(ParkingZone zone,String code,String type,Map<String,Object> features) {
  this.zone=Objects.requireNonNull(zone);update(code,type,features,OperationalStatus.ACTIVE);
 }
 public void update(String code,String type,Map<String,Object> features,OperationalStatus status) {
  this.code=text(code).toUpperCase(Locale.ROOT);this.spotType=text(type).toUpperCase(Locale.ROOT);
  this.features=object(features);this.operationalStatus=Objects.requireNonNull(status);
 }
 public void setOperationalStatus(OperationalStatus status){this.operationalStatus=Objects.requireNonNull(status);}
 public UUID getZoneId() { return zone.getId(); }
 public UUID getTenantId() { return zone.getTenantId(); }

}
