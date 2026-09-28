package com.yachiqo.spotgo.billing.application.internal.queryservices;

import com.yachiqo.spotgo.billing.application.queryservices.ReceiptQueryService;
import com.yachiqo.spotgo.billing.domain.model.aggregates.Receipt;
import com.yachiqo.spotgo.billing.domain.model.queries.GetAllReceiptsQuery;
import com.yachiqo.spotgo.billing.domain.model.queries.GetReceiptByIdQuery;
import com.yachiqo.spotgo.billing.domain.model.queries.GetReceiptsByReservationIdQuery;
import com.yachiqo.spotgo.billing.domain.repositories.ReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ReceiptQueryServiceImpl implements ReceiptQueryService {

    private final ReceiptRepository receiptRepository;

    public ReceiptQueryServiceImpl(ReceiptRepository receiptRepository) {
        this.receiptRepository = receiptRepository;
    }

    @Override
    public Optional<Receipt> handle(GetReceiptByIdQuery query) {
        return receiptRepository.findById(query.receiptId());
    }

    @Override
    public List<Receipt> handle(GetAllReceiptsQuery query) {
        return receiptRepository.findAll();
    }

    @Override
    public List<Receipt> handle(GetReceiptsByReservationIdQuery query) {
        return receiptRepository.findAllByReservationId(query.reservationId());
    }
}
