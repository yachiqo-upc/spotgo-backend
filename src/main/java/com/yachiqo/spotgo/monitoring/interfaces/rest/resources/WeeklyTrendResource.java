package com.yachiqo.spotgo.monitoring.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

public record WeeklyTrendResource(
        @Schema(description = "Unique identifier of the data point", example = "1")
        String id,

        @Schema(description = "Parking identifier", example = "1")
        String parkingId,

        @Schema(description = "Day of the week", example = "Mon",
                allowableValues = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"})
        String day,

        @Schema(description = "Trend value for that day", example = "0.6")
        Double value
) {}
