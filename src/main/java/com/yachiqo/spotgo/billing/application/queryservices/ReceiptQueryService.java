package com.yachiqo.spotgo.billing.application.queryservices;

import com.yachiqo.spotgo.billing.domain.model.aggregates.Receipt;
import com.yachiqo.spotgo.billing.domain.model.queries.GetAllReceiptsQuery;
import com.yachiqo.spotgo.billing.domain.model.queries.GetReceiptByIdQuery;
import com.yachiqo.spotgo.billing.domain.model.queries.GetReceiptsByReservationIdQuery;

import java.util.List;
import java.util.Optional;

public interface ReceiptQueryService {

    Optional<Receipt> handle(GetReceiptByIdQuery query);

    List<Receipt> handle(GetAllReceiptsQuery query);

    List<Receipt> handle(GetReceiptsByReservationIdQuery query);
}
