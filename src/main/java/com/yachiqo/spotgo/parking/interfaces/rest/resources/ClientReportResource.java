package com.yachiqo.spotgo.parking.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

public record ClientReportResource(
        @Schema(description = "Unique identifier of the report", example = "1")
        String id,

        @Schema(description = "Sequential report code shown in the frontend", example = "RPT-00001")
        String code,

        @Schema(description = "Client identifier", example = "3")
        String clientId,

        @Schema(description = "Parking identifier", example = "1")
        String parkingId,

        @Schema(description = "Reservation this report refers to", example = "5")
        String reservationId,

        @Schema(description = "Report type", example = "safety-concern",
                allowableValues = {"safety-concern", "maintenance-issue", "billing-dispute", "other"})
        String type,

        @Schema(description = "Date the report was filed", example = "2026-06-20")
        String date,

        @Schema(description = "Report status", example = "submitted",
                allowableValues = {"submitted", "resolved"})
        String status
) {}
