package com.yachiqo.spotgo.billing.interfaces.rest.transform;

import com.yachiqo.spotgo.billing.domain.model.commands.CreateSubscriptionCommand;
import com.yachiqo.spotgo.billing.interfaces.rest.resources.CreateSubscriptionResource;

public class CreateSubscriptionCommandFromResourceAssembler {

    public static CreateSubscriptionCommand toCommandFromResource(CreateSubscriptionResource resource) {
        return new CreateSubscriptionCommand(
                null,
                resource.planId(),
                resource.autoRenewal(),
                resource.paymentMethodLastFour(),
                resource.paymentMethodExpiry()
        );
    }
}
