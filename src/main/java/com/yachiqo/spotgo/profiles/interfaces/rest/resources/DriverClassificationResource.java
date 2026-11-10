package com.yachiqo.spotgo.profiles.interfaces.rest.resources;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.DriverClassification;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.DriverProfileType;
import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
public record DriverClassificationResource(
    @Schema(description = "UUID of this classification.", example = "00000000-0000-4000-8000-000000000061") UUID id,
    @Schema(description = "UUID of the classified driver's profile.", example = "00000000-0000-4000-8000-000000000012") UUID profileId,
    @Schema(description = "Tenant UUID. This currently corresponds to the parking facility identifier from the legacy module.", example = "00000000-0000-4000-8000-000000000021") UUID tenantId,
    @Schema(description = "TAXI: taxi driver; EMPLOYEE: employee; PRIVATE: private driver. This does not change the IAM role.", example = "EMPLOYEE") DriverProfileType profileType,
    @Schema(description = "UUID of the administrator profile that made the assignment.", example = "00000000-0000-4000-8000-000000000011") UUID assignedBy,
    @Schema(description = "Classification creation time in UTC.", example = "2026-11-09T07:00:00Z") Instant createdAt,
    @Schema(description = "Last updated at in UTC.", example = "2026-11-09T07:00:00Z") Instant updatedAt) {
    public static DriverClassificationResource from(DriverClassification c) {
        return new DriverClassificationResource(c.getId(), c.getProfileId(), c.getTenantId(), c.getProfileType(),
            c.getAssignedById(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
