package com.yachiqo.spotgo.billing.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSubscriptionResource(
        @NotNull(message = "{validation.not-null}")
        @Schema(description = "Plan identifier", example = "2")
        String planId,

        @NotNull(message = "{validation.not-null}")
        @Schema(description = "Auto-renewal enabled", example = "true")
        Boolean autoRenewal,

        @Schema(description = "Last four digits of payment method", example = "4242")
        String paymentMethodLastFour,

        @Schema(description = "Payment method expiry", example = "12/28")
        String paymentMethodExpiry
) {
}
