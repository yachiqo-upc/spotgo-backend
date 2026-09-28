package com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.adapters;

import com.yachiqo.spotgo.billing.domain.model.aggregates.Subscription;
import com.yachiqo.spotgo.billing.domain.repositories.SubscriptionRepository;
import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.assemblers.SubscriptionPersistenceAssembler;
import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories.SubscriptionPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SubscriptionRepositoryImpl implements SubscriptionRepository {

    private final SubscriptionPersistenceRepository subscriptionPersistenceRepository;

    public SubscriptionRepositoryImpl(SubscriptionPersistenceRepository subscriptionPersistenceRepository) {
        this.subscriptionPersistenceRepository = subscriptionPersistenceRepository;
    }

    @Override
    public Optional<Subscription> findById(String id) {
        return subscriptionPersistenceRepository.findById(id)
                .map(SubscriptionPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public List<Subscription> findAll() {
        return subscriptionPersistenceRepository.findAll().stream()
                .map(SubscriptionPersistenceAssembler::toDomainFromPersistence)
                .toList();
    }

    @Override
    public List<Subscription> findAllByClientId(String clientId) {
        return subscriptionPersistenceRepository.findAllByClientId(clientId).stream()
                .map(SubscriptionPersistenceAssembler::toDomainFromPersistence)
                .toList();
    }

    @Override
    public Subscription save(Subscription subscription) {
        var entity = SubscriptionPersistenceAssembler.toPersistenceFromDomain(subscription);
        var saved = subscriptionPersistenceRepository.save(entity);
        return SubscriptionPersistenceAssembler.toDomainFromPersistence(saved);
    }
}
