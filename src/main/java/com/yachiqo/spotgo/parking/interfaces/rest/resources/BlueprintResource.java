package com.yachiqo.spotgo.parking.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

public record BlueprintResource(
        @Schema(description = "Unique identifier of the blueprint", example = "1")
        String id,

        @Schema(description = "Administrator identifier", example = "1")
        String adminId,

        @Schema(description = "Parking identifier", example = "1")
        String parkingId,

        @Schema(description = "Blueprint name", example = "Croquis Nivel 1")
        String name,

        @Schema(description = "URL or data URI of the blueprint image")
        String dataUrl
) {}
