package com.yachiqo.spotgo.parking.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// frontend sends { "status": "..." } in the body, not a query param
public record UpdateClientReportStatusResource(
        @Schema(description = "New report status", example = "resolved",
                allowableValues = {"submitted", "resolved"})
        @NotBlank
        String status
) {}
