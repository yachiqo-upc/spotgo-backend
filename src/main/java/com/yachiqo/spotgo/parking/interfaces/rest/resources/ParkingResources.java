package com.yachiqo.spotgo.parking.interfaces.rest.resources;
import com.yachiqo.spotgo.parking.domain.model.aggregates.*;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.*;
import com.yachiqo.spotgo.parking.application.ParkingPaymentGateway;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.DriverProfileType;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.*;
import java.math.BigDecimal;
public final class ParkingResources {
 private ParkingResources() {}
 public record TenantBody(@NotBlank String name,@NotBlank String address,
  @NotNull @Schema(example="{\"latitude\":-12.0464,\"longitude\":-77.0428}") Map<String,Object> coordinates,
  @NotNull Map<String,Object> contactData,
  @NotNull @Schema(example="{\"tariff_rules\":{\"period_minutes\":60,\"price_per_period\":5,\"rounding\":\"CEILING\"},\"lock_duration_minutes\":10,\"overstay_tolerance_minutes\":5,\"allocation_rules\":{},\"coexistence_rules\":[]}") Map<String,Object> rules,
  @Schema(description="ACTIVE is used on creation; provide a value when updating.") TenantStatus status) {}
 public record ZoneBody(@NotBlank String name,@NotNull Map<String,Object> location,@NotBlank @Schema(example="#2563EB") String colorCode,ZoneStatus status,
  @Schema(description="Driver profile types allowed to use this zone. Omit on creation to allow all types; omit on update to preserve the current list.",example="[\"TAXI\",\"EMPLOYEE\"]") Set<DriverProfileType> allowedProfileTypes) {}
 public record SpotBody(@NotBlank String code,@NotBlank @Schema(description="Parking spot category; this is not TAXI/EMPLOYEE/PRIVATE.",example="STANDARD") String spotType,@NotNull Map<String,Object> features,OperationalStatus operationalStatus) {}
 public record PlanBody(@NotNull UUID zoneId,@Min(1) int version,@NotBlank String fileReference) {}
 public record MapBody(@NotNull UUID floorPlanId,@Min(1) int version,@NotNull List<Map<String,Object>> elements) {}
 public record ReserveBody(@NotNull UUID spotId,@NotNull UUID vehicleId,@NotNull Instant startAt,@NotNull Instant endAt) {}
 public record PaymentBody(@NotNull UUID paymentId) {}
 public record EntryBody(@NotNull UUID reservationId) {}
 public record GuestBody(@NotNull UUID spotId,@NotBlank String manualPlate,@NotNull PhysicalPaymentMethod physicalPaymentMethod) {}
 public record GuestExitBody(boolean physicalPaymentConfirmed) {}
 public record TenantView(UUID tenantId,String name,TenantStatus status,Map<String,Object> coordinates,String address,Map<String,Object> contactData,Map<String,Object> rules,Instant createdAt,Instant updatedAt) {
  public static TenantView of(Tenant t){return new TenantView(t.getId(),t.getName(),t.getStatus(),t.getCoordinates(),t.getAddress(),t.getContactData(),t.getRules(),t.getCreatedAt(),t.getUpdatedAt());}
 }
 public record ZoneView(UUID zoneId,UUID tenantId,String name,Map<String,Object> location,String colorCode,Set<DriverProfileType> allowedProfileTypes,ZoneStatus status,Instant createdAt,Instant updatedAt) {
  public static ZoneView of(ParkingZone z){return new ZoneView(z.getId(),z.getTenantId(),z.getName(),z.getLocation(),z.getColorCode(),z.getAllowedProfileTypes(),z.getStatus(),z.getCreatedAt(),z.getUpdatedAt());}
 }
 public record SpotBillingAdjustment(UUID paymentId,BigDecimal amount,String currency,String status) {
  public static SpotBillingAdjustment of(ParkingPaymentGateway.SpotBillingResult r){return r==null?null:new SpotBillingAdjustment(r.paymentId(),r.amount(),r.currency(),r.status());}
 }
 public record SpotView(UUID spotId,UUID zoneId,String code,String spotType,Map<String,Object> features,OperationalStatus operationalStatus,SpotBillingAdjustment billingAdjustment) {
  public static SpotView of(ParkingSpot s){return of(s,null);}
  public static SpotView of(ParkingSpot s,ParkingPaymentGateway.SpotBillingResult adjustment){return new SpotView(s.getId(),s.getZoneId(),s.getCode(),s.getSpotType(),s.getFeatures(),s.getOperationalStatus(),SpotBillingAdjustment.of(adjustment));}
 }
 public record PlanView(UUID floorPlanId,UUID zoneId,int version,String fileReference,PublicationStatus status,Instant publishedAt) {
  public static PlanView of(FloorPlan p){return new PlanView(p.getId(),p.getZoneId(),p.getVersion(),p.getFileReference(),p.getStatus(),p.getPublishedAt());}
 }
 public record MapView(UUID mapId,UUID floorPlanId,int version,List<Map<String,Object>> elements,PublicationStatus status,Instant generatedAt) {
  public static MapView of(DigitalParkingMap m){return new MapView(m.getId(),m.getFloorPlanId(),m.getVersion(),m.getElements(),m.getStatus(),m.getGeneratedAt());}
 }
 public record ReservationView(UUID reservationId,UUID tenantId,UUID zoneId,UUID spotId,UUID profileId,UUID vehicleId,UUID paymentId,Instant startAt,Instant endAt,BigDecimal amount,ReservationStatus status,LockStatus lockStatus,Instant lockedAt,Instant lockExpiresAt,Instant createdAt,Instant updatedAt) {
  public static ReservationView of(Reservation r){return new ReservationView(r.getId(),r.getTenantId(),r.getZoneId(),r.getSpotId(),r.getProfileId(),r.getVehicleId(),r.getPaymentId(),r.getStartAt(),r.getEndAt(),r.getAmount(),r.getStatus(),r.getLockStatus(),r.getLockedAt(),r.getLockExpiresAt(),r.getCreatedAt(),r.getUpdatedAt());}
 }
 public record SessionView(UUID sessionId,UUID reservationId,UUID spotId,UUID profileId,UUID vehicleId,Instant entryAt,Instant exitAt,ParkingSessionStatus status) {
  public static SessionView of(ParkingSession s){return new SessionView(s.getId(),s.getReservationId(),s.getSpotId(),s.getProfileId(),s.getVehicleId(),s.getEntryAt(),s.getExitAt(),s.getStatus());}
 }
 public record GuestView(UUID guestSessionId,UUID tenantId,UUID zoneId,UUID spotId,String manualPlate,Instant entryAt,Instant exitAt,BigDecimal calculatedAmount,PhysicalPaymentMethod physicalPaymentMethod,boolean physicalPaymentConfirmed,GuestSessionStatus status) {
  public static GuestView of(GuestParkingSession g){return new GuestView(g.getId(),g.getTenantId(),g.getZoneId(),g.getSpotId(),g.getManualPlate(),g.getEntryAt(),g.getExitAt(),g.getCalculatedAmount(),g.getPhysicalPaymentMethod(),g.isPhysicalPaymentConfirmed(),g.getStatus());}
 }
}
