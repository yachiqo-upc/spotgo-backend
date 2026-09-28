package com.yachiqo.spotgo.monitoring.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

public record OccupancyByHourResource(
        @Schema(description = "Unique identifier of the data point", example = "1")
        String id,

        @Schema(description = "Parking identifier", example = "1")
        String parkingId,

        @Schema(description = "Hour of the day", example = "06:00")
        String hour,

        @Schema(description = "Occupancy intensity for that hour, from 0.0 to 1.0", example = "0.2")
        Double intensity
) {}
