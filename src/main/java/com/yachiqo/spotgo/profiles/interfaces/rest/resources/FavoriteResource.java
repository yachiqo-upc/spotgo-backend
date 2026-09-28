package com.yachiqo.spotgo.profiles.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

public record FavoriteResource(
        @Schema(description = "Favorite identifier", example = "8b66c7f8-2d73-4d72-b6e2-4cbc4047741f")
        String id,
        @Schema(description = "Client identifier", example = "3")
        String clientId,
        @Schema(description = "Parking identifier", example = "1")
        String parkingId,
        @Schema(description = "Distance in miles", example = "0.7")
        Double distanceMi,
        @Schema(description = "Last visited date label", example = "Today")
        String lastVisited) {
}
