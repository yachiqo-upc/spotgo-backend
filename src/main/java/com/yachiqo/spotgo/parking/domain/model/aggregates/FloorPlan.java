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

@Entity @Table(name="floor_plans",uniqueConstraints=@UniqueConstraint(columnNames={"zone_id","version"})) @Getter
public class FloorPlan {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="floor_plan_id", nullable=false, updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="zone_id",nullable=false,updatable=false) private ParkingZone zone;

 @Column(nullable=false) private int version;
 @Column(name="file_reference",nullable=false) private String fileReference;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private PublicationStatus status;
 @Column(name="published_at") private Instant publishedAt;
 protected FloorPlan() {}
 public FloorPlan(ParkingZone zone,int version,String reference) {
  if(version < 1) throw new IllegalArgumentException("version must be positive.");
  this.zone=Objects.requireNonNull(zone);this.version=version;this.fileReference=text(reference);this.status=PublicationStatus.DRAFT;
 }
 public void publish(Instant now) { status=PublicationStatus.PUBLISHED;publishedAt=now; }
 public void archive() { status=PublicationStatus.ARCHIVED; }
 public UUID getZoneId() { return zone.getId(); }

}
