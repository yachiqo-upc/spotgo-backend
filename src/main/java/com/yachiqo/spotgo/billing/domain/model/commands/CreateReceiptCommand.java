package com.yachiqo.spotgo.billing.domain.model.commands;

public record CreateReceiptCommand(
        String clientId,
        String reservationId,
        String invoiceNumber,
        String locationName,
        String date,
        Integer durationHours,
        Integer durationMinutes,
        String paymentMethod,
        Double amount
) {
}
