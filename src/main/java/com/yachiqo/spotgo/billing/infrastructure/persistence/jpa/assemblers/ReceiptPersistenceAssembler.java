package com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.assemblers;

import com.yachiqo.spotgo.billing.domain.model.aggregates.Receipt;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.ReceiptStatus;
import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.entities.ReceiptPersistenceEntity;

public final class ReceiptPersistenceAssembler {

    private ReceiptPersistenceAssembler() {
    }

    public static Receipt toDomainFromPersistence(ReceiptPersistenceEntity entity) {
        return new Receipt(
                entity.getId(),
                entity.getClientId(),
                entity.getReservationId(),
                entity.getInvoiceNumber(),
                entity.getLocationName(),
                entity.getDate(),
                entity.getDurationHours(),
                entity.getDurationMinutes(),
                entity.getPaymentMethod(),
                entity.getAmount(),
                ReceiptStatus.valueOf(entity.getStatus().toUpperCase())
        );
    }

    public static ReceiptPersistenceEntity toPersistenceFromDomain(Receipt domain) {
        ReceiptPersistenceEntity entity = new ReceiptPersistenceEntity();
        entity.setId(domain.getId());
        entity.setClientId(domain.getClientId());
        entity.setReservationId(domain.getReservationId());
        entity.setInvoiceNumber(domain.getInvoiceNumber());
        entity.setLocationName(domain.getLocationName());
        entity.setDate(domain.getDate());
        entity.setDurationHours(domain.getDurationHours());
        entity.setDurationMinutes(domain.getDurationMinutes());
        entity.setPaymentMethod(domain.getPaymentMethod());
        entity.setAmount(domain.getAmount());
        entity.setStatus(domain.getStatus().name());
        return entity;
    }
}
