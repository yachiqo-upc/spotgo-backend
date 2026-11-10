package com.yachiqo.spotgo.profiles.interfaces.rest.resources;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
public record CreateVehicleResource(
    @Schema(description = "Optional unique plate. Normalized to uppercase; an empty or missing value is stored as NULL.", example = "DEM-104") String plate,
    @Schema(description = "Required vehicle make.", example = "Toyota") @NotBlank String make,
    @Schema(description = "Required vehicle model.", example = "Yaris") @NotBlank String model,
    @Schema(description = "Required vehicle color.", example = "Blue") @NotBlank String color) {}
