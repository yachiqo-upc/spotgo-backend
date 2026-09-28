package com.yachiqo.spotgo.billing.application.internal.queryservices;

import com.yachiqo.spotgo.billing.application.queryservices.SubscriptionQueryService;
import com.yachiqo.spotgo.billing.domain.model.aggregates.Subscription;
import com.yachiqo.spotgo.billing.domain.model.queries.GetAllSubscriptionsQuery;
import com.yachiqo.spotgo.billing.domain.model.queries.GetSubscriptionByIdQuery;
import com.yachiqo.spotgo.billing.domain.repositories.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class SubscriptionQueryServiceImpl implements SubscriptionQueryService {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionQueryServiceImpl(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    public Optional<Subscription> handle(GetSubscriptionByIdQuery query) {
        return subscriptionRepository.findById(query.subscriptionId());
    }

    @Override
    public List<Subscription> handle(GetAllSubscriptionsQuery query) {
        return subscriptionRepository.findAll();
    }
}
