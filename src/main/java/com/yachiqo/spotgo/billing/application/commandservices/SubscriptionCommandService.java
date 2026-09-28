package com.yachiqo.spotgo.billing.application.commandservices;

import com.yachiqo.spotgo.billing.domain.model.aggregates.Subscription;
import com.yachiqo.spotgo.billing.domain.model.commands.CreateSubscriptionCommand;
import com.yachiqo.spotgo.billing.domain.model.commands.PatchSubscriptionSavingsCommand;
import com.yachiqo.spotgo.billing.domain.model.commands.UpdateSubscriptionCommand;
import com.yachiqo.spotgo.shared.application.result.ApplicationError;
import com.yachiqo.spotgo.shared.application.result.Result;

public interface SubscriptionCommandService {

    Result<Subscription, ApplicationError> handle(CreateSubscriptionCommand command);

    Result<Subscription, ApplicationError> handle(UpdateSubscriptionCommand command);

    Result<Subscription, ApplicationError> handle(PatchSubscriptionSavingsCommand command);
}
