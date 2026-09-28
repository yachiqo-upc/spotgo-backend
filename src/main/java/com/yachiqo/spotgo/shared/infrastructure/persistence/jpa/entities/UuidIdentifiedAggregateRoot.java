package com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.util.UUID;

@Getter
@MappedSuperclass
public abstract class UuidIdentifiedAggregateRoot<T extends UuidIdentifiedAggregateRoot<T>>
        extends AbstractAggregateRoot<T> {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    protected String id;

    public void setId(String id) {
        this.id = id;
    }

    @PrePersist
    protected void ensureId() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
    }
}
