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

@Check(constraints="interval_end > interval_start and generated_at >= interval_end")
@Entity @Table(name="occupancy_reports") @Getter
public class OccupancyReport {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="report_id",nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="tenant_id",nullable=false,updatable=false) private Tenant tenant; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="zone_id",nullable=false,updatable=false) private ParkingZone zone;
 @Column(name="interval_start",nullable=false) private Instant intervalStart; @Column(name="interval_end",nullable=false) private Instant intervalEnd;
 @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb",nullable=false,updatable=false) private Map<String,Object> totals;
 @Column(name="generated_at",nullable=false) private Instant generatedAt; @Enumerated(EnumType.STRING) @Column(name="status",nullable=false) private ReportStatus status;
 protected OccupancyReport(){}
 
 public OccupancyReport(ParkingZone zone,Instant from,Instant to,Map<String,Object> totals,Instant now){
  if(from==null||to==null||!from.isBefore(to)||to.isAfter(now))throw new IllegalArgumentException("Invalid report interval.");
  this.zone=Objects.requireNonNull(zone);tenant=zone.getTenant();intervalStart=from;intervalEnd=to;this.totals=new LinkedHashMap<>(totals);generatedAt=instant(now);status=ReportStatus.GENERATED;
 }
 public UUID getTenantId(){return tenant.getId();} public UUID getZoneId(){return zone.getId();}

}
