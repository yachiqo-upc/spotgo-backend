package com.yachiqo.spotgo.billing.interfaces.rest.transform;

import com.yachiqo.spotgo.billing.domain.model.aggregates.Subscription;
import com.yachiqo.spotgo.billing.interfaces.rest.resources.SubscriptionResource;

public class SubscriptionResourceFromEntityAssembler {

    public static SubscriptionResource toResourceFromEntity(Subscription entity) {
        return new SubscriptionResource(
                entity.getId(),
                entity.getClientId(),
                entity.getPlanId(),
                entity.getStatus().name().toLowerCase(),
                entity.getRenewsOn(),
                entity.getPricePerMonth(),
                entity.getSessions(),
                entity.getSavedThisMonth(),
                entity.getSavingsMonth(),
                entity.getMemberSince(),
                entity.getAutoRenewal(),
                entity.getPaymentMethodLastFour(),
                entity.getPaymentMethodExpiry()
        );
    }
}
