package com.yachiqo.spotgo.billing.domain.model.commands;

public record CreateSubscriptionCommand(
        String clientId,
        String planId,
        Boolean autoRenewal,
        String paymentMethodLastFour,
        String paymentMethodExpiry
) {
}
