package com.yachiqo.spotgo.billing.domain.repositories;

import com.yachiqo.spotgo.billing.domain.model.aggregates.Receipt;

import java.util.List;
import java.util.Optional;

public interface ReceiptRepository {

    Optional<Receipt> findById(String id);

    List<Receipt> findAll();

    List<Receipt> findAllByClientId(String clientId);

    List<Receipt> findAllByReservationId(String reservationId);

    Receipt save(Receipt receipt);

    void deleteById(String id);
}
