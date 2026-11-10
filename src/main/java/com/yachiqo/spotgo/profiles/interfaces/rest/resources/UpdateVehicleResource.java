package com.yachiqo.spotgo.profiles.interfaces.rest.resources;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.VehicleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
public record UpdateVehicleResource(
    @Schema(description = "Omitir mantiene la placa actual. Enviar cadena vacia la elimina. No puede repetir otra placa.", example = "DEM-104") String plate,
    @Schema(description = "Nueva marca; omitir conserva la actual.", example = "Toyota") String make,
    @Schema(description = "Nuevo modelo; omitir conserva el actual.", example = "Yaris") String model,
    @Schema(description = "Nuevo color; omitir conserva el actual.", example = "Negro") String color,
    @Schema(description = "Habilitacion del registro: ACTIVE o INACTIVE. Omitir conserva el estado.", example = "ACTIVE") VehicleStatus status) {}
