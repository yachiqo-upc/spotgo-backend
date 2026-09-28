package com.yachiqo.spotgo.parking.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDetectedSpotResource(
        @Schema(description = "Spot code shown in the frontend", example = "63")
        Integer code,

        @Schema(description = "Blueprint identifier this spot belongs to", example = "1")
        @NotBlank
        String blueprintId,

        @Schema(description = "Parking identifier", example = "1")
        @NotBlank
        String parkingId,

        @Schema(description = "Row position on the blueprint grid", example = "0")
        Integer row,

        @Schema(description = "Column position on the blueprint grid", example = "0")
        Integer col,

        @Schema(description = "X position as a percentage of the blueprint width", example = "5.84")
        @JsonProperty("x_pct") Double xPct,

        @Schema(description = "Y position as a percentage of the blueprint height", example = "8.05")
        @JsonProperty("y_pct") Double yPct,

        @Schema(description = "Width as a percentage of the blueprint width", example = "7.73")
        @JsonProperty("w_pct") Double wPct,

        @Schema(description = "Height as a percentage of the blueprint height", example = "16.33")
        @JsonProperty("h_pct") Double hPct,

        @Schema(description = "Spot status", example = "available",
                allowableValues = {"available", "occupied", "reserved", "maintenance"})
        @NotBlank
        String status
) {}
