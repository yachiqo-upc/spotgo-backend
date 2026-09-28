package com.yachiqo.spotgo.parking.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record UpdateReservationResource(
        @Schema(description = "Updated reservation end date and time")
        OffsetDateTime endDate,
        @Schema(description = "Updated total amount", example = "12.5")
        Double amount,
        @Schema(description = "Updated base amount", example = "10.0")
        Double baseAmount,
        @Schema(description = "Updated rating", example = "5")
        Double rating,
        @Schema(description = "Updated reservation status", allowableValues = {"active", "completed", "cancelled"})
        String status
) {
}
