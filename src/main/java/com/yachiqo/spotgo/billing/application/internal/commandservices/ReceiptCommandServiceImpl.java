package com.yachiqo.spotgo.billing.application.internal.commandservices;

import com.yachiqo.spotgo.billing.application.commandservices.ReceiptCommandService;
import com.yachiqo.spotgo.billing.domain.model.aggregates.Receipt;
import com.yachiqo.spotgo.billing.domain.model.commands.CreateReceiptCommand;
import com.yachiqo.spotgo.billing.domain.model.commands.DeleteReceiptCommand;
import com.yachiqo.spotgo.billing.domain.repositories.ReceiptRepository;
import com.yachiqo.spotgo.shared.application.result.ApplicationError;
import com.yachiqo.spotgo.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReceiptCommandServiceImpl implements ReceiptCommandService {

    private final ReceiptRepository receiptRepository;

    public ReceiptCommandServiceImpl(ReceiptRepository receiptRepository) {
        this.receiptRepository = receiptRepository;
    }

    @Override
    public Result<Receipt, ApplicationError> handle(CreateReceiptCommand command) {
        try {
            var receipt = new Receipt(command);
            var savedReceipt = receiptRepository.save(receipt);
            return Result.success(savedReceipt);
        } catch (IllegalArgumentException e) {
            return Result.failure(ApplicationError.validationError("Receipt", e.getMessage()));
        } catch (Exception e) {
            return Result.failure(ApplicationError.unexpected("Receipt creation", e.getMessage()));
        }
    }

    @Override
    public Result<Void, ApplicationError> handle(DeleteReceiptCommand command) {
        try {
            var existing = receiptRepository.findById(command.receiptId());
            if (existing.isEmpty()) {
                return Result.failure(ApplicationError.notFound(
                        "Receipt", "Receipt with ID %s not found".formatted(command.receiptId())));
            }
            receiptRepository.deleteById(command.receiptId());
            return Result.success(null);
        } catch (Exception e) {
            return Result.failure(ApplicationError.unexpected("Receipt deletion", e.getMessage()));
        }
    }
}
