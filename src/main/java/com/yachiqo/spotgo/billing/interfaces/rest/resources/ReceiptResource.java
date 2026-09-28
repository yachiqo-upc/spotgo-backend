package com.yachiqo.spotgo.billing.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReceiptResource(
        @Schema(description = "Unique identifier of the receipt", example = "1")
        String id,

        @Schema(description = "Client identifier", example = "1")
        String clientId,

        @Schema(description = "Reservation identifier", example = "7c1f24f2-...")
        String reservationId,

        @Schema(description = "Invoice number", example = "INV-2026-0412")
        String invoiceNumber,

        @Schema(description = "Parking location name", example = "SpotGo - Centro")
        String locationName,

        @Schema(description = "Date of the parking session", example = "2026-04-12")
        String date,

        @Schema(description = "Duration in hours", example = "2")
        Integer durationHours,

        @Schema(description = "Duration remaining minutes", example = "30")
        Integer durationMinutes,

        @Schema(description = "Payment method used", example = "Visa •• 4242")
        String paymentMethod,

        @Schema(description = "Total amount charged in S/.", example = "15.50")
        Double amount,

        @Schema(description = "Receipt status: PAID, REFUNDED or PENDING", example = "PAID")
        String status
) {
}
