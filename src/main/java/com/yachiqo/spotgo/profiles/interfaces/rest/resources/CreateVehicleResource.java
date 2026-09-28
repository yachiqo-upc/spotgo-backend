package com.yachiqo.spotgo.profiles.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateVehicleResource(
        @Schema(description = "Client identifier", example = "3")
        @NotBlank String clientId,
        @Schema(description = "Vehicle license plate", example = "ABC-123")
        @NotBlank String licensePlate,
        @Schema(description = "Vehicle type", example = "sedan")
        @NotBlank String vehicleType,
        @Schema(description = "Vehicle brand", example = "Toyota")
        @NotBlank String brand,
        @Schema(description = "Vehicle model", example = "Corolla")
        @NotBlank String model) {
}
