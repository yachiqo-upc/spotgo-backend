package com.yachiqo.spotgo.profiles.interfaces.rest.resources;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.DriverClassification;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.DriverProfileType;
import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
public record DriverClassificationResource(
    @Schema(description = "UUID de esta clasificacion.", example = "00000000-0000-4000-8000-000000000061") UUID id,
    @Schema(description = "UUID del perfil del conductor clasificado.", example = "00000000-0000-4000-8000-000000000012") UUID profileId,
    @Schema(description = "UUID del tenant. Temporalmente corresponde al estacionamiento del modulo antiguo.", example = "00000000-0000-4000-8000-000000000021") UUID tenantId,
    @Schema(description = "TAXI: taxista; EMPLOYEE: empleado; PRIVATE: particular. No cambia el rol de IAM.", example = "EMPLOYEE") DriverProfileType profileType,
    @Schema(description = "UUID del perfil del administrador que realizo la asignacion.", example = "00000000-0000-4000-8000-000000000011") UUID assignedBy,
    @Schema(description = "Creacion de la clasificacion en UTC.", example = "2026-11-09T07:00:00Z") Instant createdAt,
    @Schema(description = "Ultima modificacion en UTC.", example = "2026-11-09T07:00:00Z") Instant updatedAt) {
    public static DriverClassificationResource from(DriverClassification c) {
        return new DriverClassificationResource(c.getId(), c.getProfileId(), c.getTenantId(), c.getProfileType(),
            c.getAssignedById(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
