package com.yachiqo.spotgo.billing.domain.model.commands;

public record UpdateSubscriptionCommand(
        String subscriptionId,
        String planId,
        Boolean autoRenewal,
        String paymentMethodLastFour,
        String paymentMethodExpiry
) {
}
