package com.yachiqo.spotgo.billing.domain.repositories;

import com.yachiqo.spotgo.billing.domain.model.aggregates.Subscription;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository {

    Optional<Subscription> findById(String id);

    List<Subscription> findAll();

    List<Subscription> findAllByClientId(String clientId);

    Subscription save(Subscription subscription);
}
