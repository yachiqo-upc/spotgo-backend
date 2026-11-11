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

@Check(constraints="(health_event_id is not null and conflict_id is null) or (health_event_id is null and conflict_id is not null)")
@Entity @Table(name="occupancy_alerts") @Getter
public class OccupancyAlert {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="alert_id",nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="tenant_id",nullable=false,updatable=false) private Tenant tenant; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="spot_id",nullable=false,updatable=false) private ParkingSpot spot;
 @ManyToOne(fetch=FetchType.LAZY,optional=true) @JoinColumn(name="health_event_id",nullable=true,updatable=false) private SensorHealthEvent healthEvent; @ManyToOne(fetch=FetchType.LAZY,optional=true) @JoinColumn(name="conflict_id",nullable=true,updatable=false) private OccupancyConflict conflict;
 @Enumerated(EnumType.STRING) @Column(name="alert_type",nullable=false) private AlertType alertType; @Enumerated(EnumType.STRING) @Column(name="severity",nullable=false) private Severity severity;
 @Column(name="evidence_ref",nullable=true) private String evidenceRef;
 @Column(name="created_at",nullable=false) private Instant createdAt; @Column(name="acknowledged_at",nullable=true) private Instant acknowledgedAt; @Column(name="closed_at",nullable=true) private Instant closedAt;
 @Enumerated(EnumType.STRING) @Column(name="resolution_classification",length=40) private AlertResolutionClassification resolutionClassification;
 @Column(name="resolution_action",length=1000) private String resolutionAction;
 @Enumerated(EnumType.STRING) @Column(name="status",nullable=false) private AlertStatus status;
 protected OccupancyAlert(){}
 
 public OccupancyAlert(SensorHealthEvent event,OccupancyConflict conflict,Instant now){
  if((event==null)==(conflict==null))throw new IllegalArgumentException("Exactly one alert source is required.");
  healthEvent=event;this.conflict=conflict;spot=event!=null?event.getSensor().getSpot():conflict.getSpot();tenant=spot.getZone().getTenant();
  alertType=event!=null?AlertType.SENSOR_FAILURE:AlertType.OCCUPANCY_CONFLICT;severity=event!=null?Severity.HIGH:conflict.getSeverity();
  createdAt=instant(now);status=AlertStatus.OPEN;
 }
 public UUID getTenantId(){return tenant.getId();} public UUID getSpotId(){return spot.getId();}
 public UUID getHealthEventId(){return healthEvent==null?null:healthEvent.getId();} public UUID getConflictId(){return conflict==null?null:conflict.getId();}
 public void attachEvidenceFile(String fileName){
  if(id==null||fileName==null||!fileName.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)"))throw new IllegalArgumentException("Invalid internal image reference.");
  evidenceRef="/api/v1/occupancy-alerts/"+id+"/evidence/"+fileName;
 }
 public String evidenceFileName(){
  String prefix="/api/v1/occupancy-alerts/"+id+"/evidence/";
  return evidenceRef!=null&&evidenceRef.startsWith(prefix)?evidenceRef.substring(prefix.length()):null;
 }
 public void acknowledge(Instant now){if(status==AlertStatus.OPEN){acknowledgedAt=instant(now);status=AlertStatus.ACKNOWLEDGED;}}
 public void close(Instant now,AlertResolutionClassification classification,String action){
  if(status==AlertStatus.CLOSED)return;
  if(classification==null)throw new IllegalArgumentException("A resolution classification is required.");
  if(action==null||action.isBlank()||action.length()>1000)throw new IllegalArgumentException("A resolution action between 1 and 1000 characters is required.");
  resolutionClassification=classification;resolutionAction=action.trim();closedAt=instant(now);status=AlertStatus.CLOSED;
 }

}
