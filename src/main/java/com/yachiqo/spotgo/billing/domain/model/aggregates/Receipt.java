package com.yachiqo.spotgo.billing.domain.model.aggregates;

import com.yachiqo.spotgo.billing.domain.model.commands.CreateReceiptCommand;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.ReceiptStatus;
import com.yachiqo.spotgo.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import lombok.Getter;
import lombok.Setter;

public class Receipt extends AbstractDomainAggregateRoot<Receipt> {

    @Getter
    @Setter
    private String id;

    @Getter
    private String clientId;

    @Getter
    private String reservationId;

    @Getter
    private String invoiceNumber;

    @Getter
    private String locationName;

    @Getter
    private String date;

    @Getter
    private Integer durationHours;

    @Getter
    private Integer durationMinutes;

    @Getter
    private String paymentMethod;

    @Getter
    private Double amount;

    @Getter
    private ReceiptStatus status;

    public Receipt(String id, String clientId, String reservationId, String invoiceNumber, String locationName,
                   String date, Integer durationHours, Integer durationMinutes,
                   String paymentMethod, Double amount, ReceiptStatus status) {
        this.id = id;
        this.clientId = clientId;
        this.reservationId = reservationId;
        this.invoiceNumber = invoiceNumber;
        this.locationName = locationName;
        this.date = date;
        this.durationHours = durationHours;
        this.durationMinutes = durationMinutes;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.status = status;
    }

    public Receipt(String clientId, String reservationId, String invoiceNumber, String locationName,
                   String date, Integer durationHours, Integer durationMinutes,
                   String paymentMethod, Double amount, ReceiptStatus status) {
        this(null, clientId, reservationId, invoiceNumber, locationName, date, durationHours,
                durationMinutes, paymentMethod, amount, status);
    }

    public Receipt(CreateReceiptCommand command) {
        this(command.clientId(), command.reservationId(), command.invoiceNumber(), command.locationName(),
                command.date(), command.durationHours(), command.durationMinutes(),
                command.paymentMethod(), command.amount(),
                ReceiptStatus.PENDING);
    }
}
