package com.yachiqo.spotgo.billing.interfaces.rest.transform;

import com.yachiqo.spotgo.billing.domain.model.aggregates.Receipt;
import com.yachiqo.spotgo.billing.interfaces.rest.resources.ReceiptResource;

public class ReceiptResourceFromEntityAssembler {

    public static ReceiptResource toResourceFromEntity(Receipt entity) {
        return new ReceiptResource(
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
                entity.getStatus().name().toLowerCase()
        );
    }
}
