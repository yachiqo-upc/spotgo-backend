package com.yachiqo.spotgo.billing.application.commandservices;

import com.yachiqo.spotgo.billing.domain.model.aggregates.Receipt;
import com.yachiqo.spotgo.billing.domain.model.commands.CreateReceiptCommand;
import com.yachiqo.spotgo.billing.domain.model.commands.DeleteReceiptCommand;
import com.yachiqo.spotgo.shared.application.result.ApplicationError;
import com.yachiqo.spotgo.shared.application.result.Result;

public interface ReceiptCommandService {

    Result<Receipt, ApplicationError> handle(CreateReceiptCommand command);

    Result<Void, ApplicationError> handle(DeleteReceiptCommand command);
}
