package com.yachiqo.spotgo.billing.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ClientPlanResource(
        @Schema(description = "Unique identifier of the plan", example = "1")
        String id,

        @Schema(description = "Plan type", example = "MONTHLY")
        String type,

        @Schema(description = "Plan name", example = "Pro Monthly")
        String name,

        @Schema(description = "Monthly price in S/.", example = "29.99")
        Double monthlyPrice,

        @Schema(description = "Plan description")
        String description,

        @Schema(description = "Max reservations per month, null means unlimited")
        Integer reservationsPerMonth,

        @Schema(description = "Discount percentage for this plan", example = "20.0")
        Double discountPercent,

        @Schema(description = "List of features included in the plan")
        List<String> features
) {
}
