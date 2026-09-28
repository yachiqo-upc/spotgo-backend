package com.yachiqo.spotgo.parking.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateClientReportResource(
        @Schema(description = "Client identifier", example = "3")
        @NotBlank
        String clientId,

        @Schema(description = "Parking identifier", example = "1")
        @NotBlank
        String parkingId,

        @Schema(description = "Reservation this report refers to", example = "5")
        @NotBlank
        String reservationId,

        @Schema(description = "Report type", example = "safety-concern",
                allowableValues = {"safety-concern", "maintenance-issue", "billing-dispute", "other"})
        @NotBlank
        String type,

        @Schema(description = "Date the report was filed", example = "2026-06-20")
        @NotBlank
        String date
) {}
