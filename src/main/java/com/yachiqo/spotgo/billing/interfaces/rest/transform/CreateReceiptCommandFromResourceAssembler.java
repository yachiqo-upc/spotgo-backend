package com.yachiqo.spotgo.billing.interfaces.rest.transform;

import com.yachiqo.spotgo.billing.domain.model.commands.CreateReceiptCommand;
import com.yachiqo.spotgo.billing.interfaces.rest.resources.CreateReceiptResource;

public class CreateReceiptCommandFromResourceAssembler {

    public static CreateReceiptCommand toCommandFromResource(CreateReceiptResource resource) {
        return new CreateReceiptCommand(
                resource.clientId(),
                resource.reservationId(),
                resource.invoiceNumber(),
                resource.locationName(),
                resource.date(),
                resource.durationHours(),
                resource.durationMinutes(),
                resource.paymentMethod(),
                resource.amount()
        );
    }
}
