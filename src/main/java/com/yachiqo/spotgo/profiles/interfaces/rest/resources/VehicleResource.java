package com.yachiqo.spotgo.profiles.interfaces.rest.resources;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Vehicle;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.VehicleStatus;
import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
public record VehicleResource(
    @Schema(description = "Vehicle UUID.", example = "00000000-0000-4000-8000-000000000051") UUID id,
    @Schema(description = "UUID of the owning profile, not its account.", example = "00000000-0000-4000-8000-000000000012") UUID profileId,
    @Schema(description = "Unique plate, or NULL if no plate is registered.", example = "DEM-101") String plate,
    @Schema(description = "Make.", example = "Toyota") String make,
    @Schema(description = "Model.", example = "Yaris") String model,
    @Schema(description = "Color.", example = "Blue") String color,
    @Schema(description = "ACTIVE: enabled. INACTIVE: disabled; the record is retained.", example = "ACTIVE") VehicleStatus status,
    @Schema(description = "Row creation time in UTC.", example = "2026-11-09T07:00:00Z") Instant createdAt,
    @Schema(description = "Last updated at in UTC.", example = "2026-11-09T07:00:00Z") Instant updatedAt) {
    public static VehicleResource from(Vehicle v) {
        return new VehicleResource(v.getId(), v.getProfileId(), v.getPlate(), v.getMake(), v.getModel(),
            v.getColor(), v.getStatus(), v.getCreatedAt(), v.getUpdatedAt());
    }
}
