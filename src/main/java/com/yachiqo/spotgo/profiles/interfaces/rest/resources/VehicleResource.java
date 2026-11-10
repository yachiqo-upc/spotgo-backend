package com.yachiqo.spotgo.profiles.interfaces.rest.resources;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Vehicle;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.VehicleStatus;
import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
public record VehicleResource(
    @Schema(description = "UUID del vehiculo.", example = "00000000-0000-4000-8000-000000000051") UUID id,
    @Schema(description = "UUID del perfil propietario, no de su cuenta.", example = "00000000-0000-4000-8000-000000000012") UUID profileId,
    @Schema(description = "Placa unica o NULL cuando no esta registrada.", example = "DEM-101") String plate,
    @Schema(description = "Marca.", example = "Toyota") String make,
    @Schema(description = "Modelo.", example = "Yaris") String model,
    @Schema(description = "Color.", example = "Azul") String color,
    @Schema(description = "ACTIVE: habilitado. INACTIVE: desactivado; el registro se conserva.", example = "ACTIVE") VehicleStatus status,
    @Schema(description = "Creacion de la fila en UTC.", example = "2026-11-09T07:00:00Z") Instant createdAt,
    @Schema(description = "Ultima modificacion en UTC.", example = "2026-11-09T07:00:00Z") Instant updatedAt) {
    public static VehicleResource from(Vehicle v) {
        return new VehicleResource(v.getId(), v.getProfileId(), v.getPlate(), v.getMake(), v.getModel(),
            v.getColor(), v.getStatus(), v.getCreatedAt(), v.getUpdatedAt());
    }
}
