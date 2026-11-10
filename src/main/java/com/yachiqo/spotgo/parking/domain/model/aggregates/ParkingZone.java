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

@Entity @Table(name="parking_zones") @Getter
public class ParkingZone {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="zone_id", nullable=false, updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="tenant_id",nullable=false,updatable=false) private Tenant tenant;

 @Column(nullable=false) private String name;
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="location",columnDefinition="jsonb",nullable=false) private Map<String,Object> location;

 @Column(name="color_code",nullable=false) private String colorCode;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private ZoneStatus status;
  @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @PrePersist void created() { createdAt=Instant.now(); updatedAt=createdAt; }
 @PreUpdate void updated() { updatedAt=Instant.now(); }

 protected ParkingZone() {}
 public ParkingZone(Tenant tenant,String name,Map<String,Object> location,String colorCode) {
  this.tenant=Objects.requireNonNull(tenant);update(name,location,colorCode,ZoneStatus.ACTIVE);
 }
 public void update(String name,Map<String,Object> location,String colorCode,ZoneStatus status) {
  this.name=text(name);this.location=object(location);this.colorCode=text(colorCode);
  if (!this.colorCode.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("colorCode debe ser #RRGGBB");
  this.status=Objects.requireNonNull(status);
 }
 public UUID getTenantId() { return tenant.getId(); }

}
