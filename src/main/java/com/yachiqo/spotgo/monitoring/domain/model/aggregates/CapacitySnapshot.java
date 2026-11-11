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

@Check(constraints="occupied_count >= 0 and total_count >= occupied_count and percentage >= 0 and percentage <= 100")
@Entity @Table(name="capacity_snapshots") @Getter
public class CapacitySnapshot {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="snapshot_id",nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="tenant_id",nullable=false,updatable=false) private Tenant tenant; @ManyToOne(fetch=FetchType.LAZY,optional=true) @JoinColumn(name="zone_id",nullable=true,updatable=false) private ParkingZone zone;
 @Column(name="occupied_count",nullable=false) private int occupiedCount;
 @Column(name="total_count",nullable=false) private int totalCount;
 @Column(nullable=false,precision=5,scale=2) private BigDecimal percentage;
 @Column(name="observed_at",nullable=false) private Instant observedAt;
 protected CapacitySnapshot(){}
 
 public CapacitySnapshot(Tenant tenant,ParkingZone zone,int occupied,int total,Instant now){
  if(occupied<0||total<0||occupied>total||(zone!=null&&!zone.getTenantId().equals(tenant.getId())))throw new IllegalArgumentException("Inconsistent capacity snapshot.");
  this.tenant=tenant;this.zone=zone;occupiedCount=occupied;totalCount=total;
  percentage=total==0?BigDecimal.ZERO.setScale(2):BigDecimal.valueOf(occupied).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total),2,RoundingMode.HALF_UP);observedAt=instant(now);
 }
 public UUID getTenantId(){return tenant.getId();} public UUID getZoneId(){return zone==null?null:zone.getId();}

}
