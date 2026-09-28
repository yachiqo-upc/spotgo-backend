package com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.assemblers;

import com.yachiqo.spotgo.billing.domain.model.aggregates.Subscription;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.SubscriptionStatus;
import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.entities.SubscriptionPersistenceEntity;

public final class SubscriptionPersistenceAssembler {

    private SubscriptionPersistenceAssembler() {
    }

    public static Subscription toDomainFromPersistence(SubscriptionPersistenceEntity entity) {
        return new Subscription(
                entity.getId(),
                entity.getClientId(),
                entity.getPlanId(),
                SubscriptionStatus.valueOf(entity.getStatus().toUpperCase()),
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

    public static SubscriptionPersistenceEntity toPersistenceFromDomain(Subscription domain) {
        SubscriptionPersistenceEntity entity = new SubscriptionPersistenceEntity();
        entity.setId(domain.getId());
        entity.setClientId(domain.getClientId());
        entity.setPlanId(domain.getPlanId());
        entity.setStatus(domain.getStatus().name());
        entity.setRenewsOn(domain.getRenewsOn());
        entity.setPricePerMonth(domain.getPricePerMonth());
        entity.setSessions(domain.getSessions());
        entity.setSavedThisMonth(domain.getSavedThisMonth());
        entity.setSavingsMonth(domain.getSavingsMonth());
        entity.setMemberSince(domain.getMemberSince());
        entity.setAutoRenewal(domain.getAutoRenewal());
        entity.setPaymentMethodLastFour(domain.getPaymentMethodLastFour());
        entity.setPaymentMethodExpiry(domain.getPaymentMethodExpiry());
        return entity;
    }
}
