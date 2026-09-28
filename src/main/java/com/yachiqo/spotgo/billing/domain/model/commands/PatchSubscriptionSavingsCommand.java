package com.yachiqo.spotgo.billing.domain.model.commands;

public record PatchSubscriptionSavingsCommand(
        String subscriptionId,
        Double savedThisMonth,
        String savingsMonth
) {}
