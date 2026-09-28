package com.yachiqo.spotgo.parking.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateParkingResource(
        @Schema(description = "Updated total spaces", example = "120")
        Integer totalSpaces,
        @Schema(description = "Updated available spaces", example = "42")
        Integer availableSpaces,
        @Schema(description = "Updated floor count", example = "4")
        Integer totalFloors,
        @Schema(description = "Updated parking city", example = "Miraflores")
        String city,
        @Schema(description = "Updated average rating", example = "4.8")
        Double rating,
        @Schema(description = "Updated price per hour", example = "6.5")
        Double pricePerHour
) {
}
