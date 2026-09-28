package com.yachiqo.spotgo.parking.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record CreateReservationResource(
        @Schema(description = "Parking identifier", example = "1")
        @NotBlank
        String parkingId,

        @Schema(description = "Reserved spot", example = "B5")
        @NotBlank
        String spot,

        @Schema(description = "Reservation start date and time")
        @NotNull
        OffsetDateTime startDate,

        @Schema(description = "Reservation end date and time")
        @NotNull
        OffsetDateTime endDate
) {}
