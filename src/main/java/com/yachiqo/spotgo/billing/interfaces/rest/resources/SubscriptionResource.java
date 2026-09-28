package com.yachiqo.spotgo.billing.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

public record SubscriptionResource(
        @Schema(description = "Unique identifier", example = "1")
        String id,

        @Schema(description = "Client identifier", example = "1")
        String clientId,

        @Schema(description = "Plan identifier", example = "2")
        String planId,

        @Schema(description = "Subscription status: ACTIVE, INACTIVE or CANCELLED", example = "ACTIVE")
        String status,

        @Schema(description = "Next renewal date", example = "2026-07-16")
        String renewsOn,

        @Schema(description = "Monthly price in S/.", example = "29.99")
        Double pricePerMonth,

        @Schema(description = "Sessions used this month", example = "5")
        Integer sessions,

        @Schema(description = "Amount saved this month in S/.", example = "10.00")
        Double savedThisMonth,

        @Schema(description = "Month label for savings", example = "June 2026")
        String savingsMonth,

        @Schema(description = "Member since date", example = "2025-01-01")
        String memberSince,

        @Schema(description = "Auto-renewal enabled", example = "true")
        Boolean autoRenewal,

        @Schema(description = "Last four digits of payment method", example = "4242")
        String paymentMethodLastFour,

        @Schema(description = "Payment method expiry", example = "12/28")
        String paymentMethodExpiry
) {
}
