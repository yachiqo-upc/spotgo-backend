package com.yachiqo.spotgo.billing.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;

public record PatchSubscriptionSavingsResource(
        @NotNull Double savedThisMonth,
        @NotNull String savingsMonth
) {}
