package com.yachiqo.spotgo.shared.domain.model.aggregates;

import org.springframework.data.domain.AbstractAggregateRoot;

import java.util.Collection;

public abstract class AbstractDomainAggregateRoot<T extends AbstractDomainAggregateRoot<T>>
        extends AbstractAggregateRoot<T> {
    /**
     * Registers a domain event to be published after this aggregate is saved
     * @param event the domain event to register
     */
    protected void registerDomainEvent(Object event) {
        super.registerEvent(event);
    }

    /**
     * Returns all domain events registered on this aggregate since the last publication.
     * Exposed as {@code public} so repository adapters can retrieve and publish them
     * after the aggregate has been persisted
     * @return the registered domain events
     */
    @Override
    public Collection<Object> domainEvents() {
        return super.domainEvents();
    }

    /**
     * Clears all registered domain events
     */
    @Override
    public void clearDomainEvents() {
        super.clearDomainEvents();
    }



}
