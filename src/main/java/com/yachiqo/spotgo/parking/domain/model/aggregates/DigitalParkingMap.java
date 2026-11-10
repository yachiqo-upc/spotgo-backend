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

@Entity @Table(name="digital_parking_maps",uniqueConstraints=@UniqueConstraint(columnNames={"floor_plan_id","version"})) @Getter
public class DigitalParkingMap {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="map_id", nullable=false, updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="floor_plan_id",nullable=false,updatable=false) private FloorPlan floorPlan;

 @Column(nullable=false) private int version;
 @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb",nullable=false) private List<Map<String,Object>> elements;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private PublicationStatus status;
 @Column(name="generated_at",nullable=false,updatable=false) private Instant generatedAt;
 protected DigitalParkingMap() {}
 public DigitalParkingMap(FloorPlan plan,int version,List<Map<String,Object>> elements,Instant now) {
  if(version < 1) throw new IllegalArgumentException("version must be positive.");
  this.floorPlan=Objects.requireNonNull(plan);this.version=version;this.elements=new ArrayList<>(Objects.requireNonNull(elements));
  this.status=PublicationStatus.DRAFT;this.generatedAt=now;
 }
 public void publish() { status=PublicationStatus.PUBLISHED; }
 public void archive() { status=PublicationStatus.ARCHIVED; }
 public UUID getFloorPlanId() { return floorPlan.getId(); }

}
