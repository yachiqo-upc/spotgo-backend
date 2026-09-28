package com.yachiqo.spotgo.profiles.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateFavoriteResource(
        @Schema(description = "Client identifier", example = "3")
        @NotBlank String clientId,
        @Schema(description = "Parking identifier", example = "1")
        @NotBlank String parkingId,
        @Schema(description = "Distance in miles", example = "0.7")
        @NotNull Double distanceMi,
        @Schema(description = "Last visited date label", example = "Today")
        @NotBlank String lastVisited) {
}
