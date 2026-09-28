package com.yachiqo.spotgo.profiles.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

public record VehicleResource(
        @Schema(description = "Vehicle identifier", example = "8b66c7f8-2d73-4d72-b6e2-4cbc4047741f")
        String id,
        @Schema(description = "Client identifier", example = "3")
        String clientId,
        @Schema(description = "Vehicle license plate", example = "ABC-123")
        String licensePlate,
        @Schema(description = "Vehicle type", example = "sedan")
        String vehicleType,
        @Schema(description = "Vehicle brand", example = "Toyota")
        String brand,
        @Schema(description = "Vehicle model", example = "Corolla")
        String model) {
}
