package com.yachiqo.spotgo.profiles.interfaces.rest.resources;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.VehicleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
public record UpdateVehicleResource(
    @Schema(description = "Omit the field to keep the current plate. Send an empty string to remove it. The plate cannot duplicate another vehicle's plate.", example = "DEM-104") String plate,
    @Schema(description = "New make; omit to keep the current value.", example = "Toyota") String make,
    @Schema(description = "New model; omit to keep the current value.", example = "Yaris") String model,
    @Schema(description = "New color; omit to keep the current value.", example = "Negro") String color,
    @Schema(description = "Record status: ACTIVE or INACTIVE. Omit the field to keep the current status.", example = "ACTIVE") VehicleStatus status) {}
