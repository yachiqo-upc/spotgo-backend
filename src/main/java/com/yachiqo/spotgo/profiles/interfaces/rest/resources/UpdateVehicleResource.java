package com.yachiqo.spotgo.profiles.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateVehicleResource(
        @Schema(description = "Vehicle license plate", example = "ABC-123")
        String licensePlate,
        @Schema(description = "Vehicle type", example = "sedan")
        String vehicleType,
        @Schema(description = "Vehicle brand", example = "Toyota")
        String brand,
        @Schema(description = "Vehicle model", example = "Corolla")
        String model) {
}
