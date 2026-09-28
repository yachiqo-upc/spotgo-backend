package com.yachiqo.spotgo.billing.interfaces.rest.transform;

import com.yachiqo.spotgo.billing.domain.model.commands.UpdateSubscriptionCommand;
import com.yachiqo.spotgo.billing.interfaces.rest.resources.UpdateSubscriptionResource;

public class UpdateSubscriptionCommandFromResourceAssembler {

    public static UpdateSubscriptionCommand toCommandFromResource(String subscriptionId,
                                                                   UpdateSubscriptionResource resource) {
        return new UpdateSubscriptionCommand(
                subscriptionId,
                resource.planId(),
                resource.autoRenewal(),
                resource.paymentMethodLastFour(),
                resource.paymentMethodExpiry()
        );
    }
}
