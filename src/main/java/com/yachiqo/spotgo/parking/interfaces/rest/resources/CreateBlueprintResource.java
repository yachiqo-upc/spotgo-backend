package com.yachiqo.spotgo.parking.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateBlueprintResource(
        @Schema(description = "Administrator identifier", example = "1")
        @NotBlank
        String adminId,

        @Schema(description = "Parking identifier", example = "1")
        @NotBlank
        String parkingId,

        @Schema(description = "Blueprint name", example = "Croquis Nivel 1")
        @NotBlank
        String name,

        @Schema(description = "URL or data URI of the blueprint image")
        @NotBlank
        String dataUrl
) {}
