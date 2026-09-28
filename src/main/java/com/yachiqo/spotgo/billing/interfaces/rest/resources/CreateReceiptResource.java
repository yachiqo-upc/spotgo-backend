package com.yachiqo.spotgo.billing.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateReceiptResource(
        @NotNull(message = "{validation.not-null}")
        @Schema(description = "Client identifier", example = "1")
        String clientId,

        @NotBlank(message = "{validation.not-blank}")
        @Schema(description = "Reservation identifier", example = "7c1f24f2-...")
        String reservationId,

        @NotBlank(message = "{validation.not-blank}")
        @Schema(description = "Invoice number", example = "INV-2026-0412")
        String invoiceNumber,

        @NotBlank(message = "{validation.not-blank}")
        @Schema(description = "Parking location name", example = "SpotGo - Centro")
        String locationName,

        @NotBlank(message = "{validation.not-blank}")
        @Schema(description = "Date of the parking session", example = "2026-04-12")
        String date,

        @NotNull(message = "{validation.not-null}")
        Integer durationHours,

        @NotNull(message = "{validation.not-null}")
        Integer durationMinutes,

        @NotBlank(message = "{validation.not-blank}")
        @Schema(description = "Payment method used", example = "Visa •• 4242")
        String paymentMethod,

        @NotNull(message = "{validation.not-null}")
        @Positive
        @Schema(description = "Total amount in S/.", example = "15.50")
        Double amount
) {
}
