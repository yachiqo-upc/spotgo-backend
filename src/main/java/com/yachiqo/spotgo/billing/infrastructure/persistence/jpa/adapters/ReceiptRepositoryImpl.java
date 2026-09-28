package com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.adapters;

import com.yachiqo.spotgo.billing.domain.model.aggregates.Receipt;
import com.yachiqo.spotgo.billing.domain.repositories.ReceiptRepository;
import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.assemblers.ReceiptPersistenceAssembler;
import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories.ReceiptPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ReceiptRepositoryImpl implements ReceiptRepository {

    private final ReceiptPersistenceRepository receiptPersistenceRepository;

    public ReceiptRepositoryImpl(ReceiptPersistenceRepository receiptPersistenceRepository) {
        this.receiptPersistenceRepository = receiptPersistenceRepository;
    }

    @Override
    public Optional<Receipt> findById(String id) {
        return receiptPersistenceRepository.findById(id)
                .map(ReceiptPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public List<Receipt> findAll() {
        return receiptPersistenceRepository.findAll().stream()
                .map(ReceiptPersistenceAssembler::toDomainFromPersistence)
                .toList();
    }

    @Override
    public List<Receipt> findAllByClientId(String clientId) {
        return receiptPersistenceRepository.findAllByClientId(clientId).stream()
                .map(ReceiptPersistenceAssembler::toDomainFromPersistence)
                .toList();
    }

    @Override
    public List<Receipt> findAllByReservationId(String reservationId) {
        return receiptPersistenceRepository.findAllByReservationId(reservationId).stream()
                .map(ReceiptPersistenceAssembler::toDomainFromPersistence)
                .toList();
    }

    @Override
    public void deleteById(String id) {
        receiptPersistenceRepository.deleteById(id);
    }

    @Override
    public Receipt save(Receipt receipt) {
        var entity = ReceiptPersistenceAssembler.toPersistenceFromDomain(receipt);
        var saved = receiptPersistenceRepository.save(entity);
        return ReceiptPersistenceAssembler.toDomainFromPersistence(saved);
    }
}
