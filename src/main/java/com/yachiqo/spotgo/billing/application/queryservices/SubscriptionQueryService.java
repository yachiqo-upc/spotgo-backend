package com.yachiqo.spotgo.billing.application.queryservices;

import com.yachiqo.spotgo.billing.domain.model.aggregates.Subscription;
import com.yachiqo.spotgo.billing.domain.model.queries.GetAllSubscriptionsQuery;
import com.yachiqo.spotgo.billing.domain.model.queries.GetSubscriptionByIdQuery;

import java.util.List;
import java.util.Optional;

public interface SubscriptionQueryService {

    Optional<Subscription> handle(GetSubscriptionByIdQuery query);

    List<Subscription> handle(GetAllSubscriptionsQuery query);
}
