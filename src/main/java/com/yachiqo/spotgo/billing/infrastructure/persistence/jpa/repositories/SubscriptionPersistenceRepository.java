package com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories;

import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.entities.SubscriptionPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPersistenceRepository extends JpaRepository<SubscriptionPersistenceEntity, String> {
    java.util.List<SubscriptionPersistenceEntity> findAllByClientId(String clientId);
}
