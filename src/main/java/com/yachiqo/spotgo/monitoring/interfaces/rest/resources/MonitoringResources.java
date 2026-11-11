package com.yachiqo.spotgo.monitoring.interfaces.rest.resources;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.*;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
public final class MonitoringResources {
 private MonitoringResources(){}
 public record FirstReading(@NotNull OccupancyStatus occupancyStatus,@NotNull Instant occurredAt,@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal quality){}
 public record SensorBody(@NotNull UUID spotId,@NotNull SensorProtocol protocol,@NotNull Map<String,Object> configuration,@NotNull Instant installedAt,@NotNull @Valid FirstReading firstReading){}
 public record ReadingBody(@NotNull UUID sensorId,@NotNull OccupancyStatus occupancyStatus,@NotNull Instant occurredAt,@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal quality){}
 public record HealthBody(@NotNull UUID sensorId,@NotNull SensorHealth health,@NotBlank @Size(max=255) String reason){}
 public record AlertResolutionBody(@NotNull AlertResolutionClassification resolutionClassification,@NotBlank @Size(max=1000) String resolutionAction){}
 public record SnapshotBody(@NotNull UUID tenantId,@Schema(description="NULL summarizes the entire tenant.") UUID zoneId){}
 public record ReportBody(@NotNull UUID zoneId,@NotNull Instant intervalStart,@NotNull Instant intervalEnd){}
 public record SensorView(UUID sensorId,UUID tenantId,UUID zoneId,UUID spotId,SensorProtocol protocol,SensorStatus status,Instant lastSeenAt,Map<String,Object> configuration,Instant installedAt){
  public static SensorView of(OccupancySensor s){return new SensorView(s.getId(),s.getTenantId(),s.getZoneId(),s.getSpotId(),s.getProtocol(),s.getStatus(),s.getLastSeenAt(),s.getConfiguration(),s.getInstalledAt());}
 }
 public record ReadingView(UUID readingId,UUID sensorId,UUID spotId,OccupancyStatus occupancyStatus,Instant occurredAt,Instant receivedAt,BigDecimal quality,String payloadHash){
  public static ReadingView of(OccupancyReading r){return new ReadingView(r.getId(),r.getSensorId(),r.getSpotId(),r.getOccupancyStatus(),r.getOccurredAt(),r.getReceivedAt(),r.getQuality(),r.getPayloadHash());}
 }
 public record HealthView(UUID healthEventId,UUID sensorId,SensorHealth health,String reason,Instant observedAt,Instant recoveredAt){
  public static HealthView of(SensorHealthEvent e){return new HealthView(e.getId(),e.getSensorId(),e.getHealth(),e.getReason(),e.getObservedAt(),e.getRecoveredAt());}
 }
 public record ConflictView(UUID conflictId,UUID spotId,UUID reservationId,UUID readingId,UUID tenantId,ConflictType conflictType,Severity severity,Instant detectedAt,ConflictStatus status){
  public static ConflictView of(OccupancyConflict c){return new ConflictView(c.getId(),c.getSpotId(),c.getReservationId(),c.getReadingId(),c.getTenantId(),c.getConflictType(),c.getSeverity(),c.getDetectedAt(),c.getStatus());}
 }
 public record AlertView(UUID alertId,UUID tenantId,UUID healthEventId,UUID conflictId,UUID spotId,AlertType alertType,Severity severity,String evidenceRef,Instant createdAt,Instant acknowledgedAt,Instant closedAt,AlertResolutionClassification resolutionClassification,String resolutionAction,AlertStatus status){
  public static AlertView of(OccupancyAlert a){return new AlertView(a.getId(),a.getTenantId(),a.getHealthEventId(),a.getConflictId(),a.getSpotId(),a.getAlertType(),a.getSeverity(),a.getEvidenceRef(),a.getCreatedAt(),a.getAcknowledgedAt(),a.getClosedAt(),a.getResolutionClassification(),a.getResolutionAction(),a.getStatus());}
 }
 public record CapacityAlertView(UUID capacityAlertId,UUID tenantId,UUID zoneId,UUID snapshotId,BigDecimal thresholdPercentage,BigDecimal occupancyPercentage,Severity severity,AlertStatus status,Instant createdAt,Instant acknowledgedAt,Instant resolvedAt){
  public static CapacityAlertView of(CapacityAlert a){return new CapacityAlertView(a.getId(),a.getTenantId(),a.getZoneId(),a.getSnapshotId(),a.getThresholdPercentage(),a.getOccupancyPercentage(),a.getSeverity(),a.getStatus(),a.getCreatedAt(),a.getAcknowledgedAt(),a.getResolvedAt());}
 }
 public record SnapshotView(UUID snapshotId,UUID tenantId,UUID zoneId,int occupiedCount,int totalCount,BigDecimal percentage,Instant observedAt){
  public static SnapshotView of(CapacitySnapshot s){return new SnapshotView(s.getId(),s.getTenantId(),s.getZoneId(),s.getOccupiedCount(),s.getTotalCount(),s.getPercentage(),s.getObservedAt());}
 }
 public record ReportView(UUID reportId,UUID tenantId,UUID zoneId,Instant intervalStart,Instant intervalEnd,Map<String,Object> totals,Instant generatedAt,ReportStatus status){
  public static ReportView of(OccupancyReport r){return new ReportView(r.getId(),r.getTenantId(),r.getZoneId(),r.getIntervalStart(),r.getIntervalEnd(),r.getTotals(),r.getGeneratedAt(),r.getStatus());}
 }
}
