package com.yachiqo.spotgo.parking.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateParkingResource(
        @Schema(description = "Administrator identifier", example = "1")
        @NotBlank
        String adminId,

        @Schema(description = "Parking name", example = "Parking Central Lima")
        @NotBlank
        String name,

        @Schema(description = "Parking address", example = "Av. Javier Prado Este 123, San Isidro")
        @NotBlank
        String address,

        @Schema(description = "City where the parking is located", example = "Lima")
        @NotBlank
        String city,

        @Schema(description = "Total number of spaces", example = "28")
        @NotNull
        @Positive
        Integer totalSpaces,

        @Schema(description = "Currently available spaces", example = "28")
        Integer availableSpaces,

        @Schema(description = "Number of floors", example = "1")
        Integer totalFloors,

        @Schema(description = "Average occupancy percentage", example = "84.2")
        Double averageOccupancy,

        @Schema(description = "Occupancy trend versus previous period, in percent", example = "5.3")
        Double occupancyTrendPercent,

        @Schema(description = "Hour with the highest occupancy", example = "14:00")
        String peakHour,

        @Schema(description = "Total revenue generated", example = "4200.0")
        Double totalRevenue,

        @Schema(description = "Current system status", example = "active")
        String systemStatus,

        @Schema(description = "Average client rating", example = "4.5")
        Double rating,

        @Schema(description = "Price per hour in S/.", example = "3.0")
        Double pricePerHour,

        @Schema(description = "Revenue trend versus previous period, in percent", example = "8.7")
        Double revenueTrendPercent,

        @Schema(description = "Total capacity including reserved zones", example = "120")
        Integer totalCapacity,

        @Schema(description = "Operational efficiency index", example = "92.5")
        Double efficiencyIndex
) {}
