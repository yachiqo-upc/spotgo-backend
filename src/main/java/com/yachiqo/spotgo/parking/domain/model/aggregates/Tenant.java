package com.yachiqo.spotgo.parking.domain.model.aggregates;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.*;
import com.yachiqo.spotgo.billing.domain.model.aggregates.PaymentToken;
import java.time.Instant;
import java.util.*;
import java.math.BigDecimal;
import static com.yachiqo.spotgo.parking.domain.model.valueobjects.ParkingValues.*;

@Entity @Table(name="tenants") @Getter
public class Tenant {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="tenant_id", nullable=false, updatable=false) private UUID id;

 @Column(nullable=false) private String name;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private TenantStatus status;
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="coordinates",columnDefinition="jsonb",nullable=false) private Map<String,Object> coordinates;

 @Column(nullable=false) private String address;
 @Column(name="token_id") private UUID defaultPaymentTokenId;
 @ManyToOne(fetch=FetchType.LAZY)
 @JoinColumn(name="token_id",insertable=false,updatable=false,
  foreignKey=@ForeignKey(name="fk_tenants_default_payment_token"))
 private PaymentToken defaultPaymentToken;
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="contact_data",columnDefinition="jsonb",nullable=false) private Map<String,Object> contactData;

  @JdbcTypeCode(SqlTypes.JSON) @Column(name="rules",columnDefinition="jsonb",nullable=false) private Map<String,Object> rules;

  @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @PrePersist void created() { createdAt=Instant.now(); updatedAt=createdAt; }
 @PreUpdate void updated() { updatedAt=Instant.now(); }

 protected Tenant() {}
 public Tenant(String name,String address,Map<String,Object> coordinates,Map<String,Object> contactData,Map<String,Object> rules) {
  update(name,address,coordinates,contactData,rules,TenantStatus.ACTIVE);
 }
 public void update(String name,String address,Map<String,Object> coordinates,Map<String,Object> contactData,Map<String,Object> rules,TenantStatus status) {
  this.name=text(name);this.address=text(address);this.coordinates=object(coordinates);this.contactData=object(contactData);
  this.rules=object(rules);this.status=Objects.requireNonNull(status);
 }
 public void setDefaultPaymentTokenId(UUID tokenId){defaultPaymentTokenId=tokenId;}

}
