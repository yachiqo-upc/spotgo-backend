package com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories;

import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.entities.ReceiptPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReceiptPersistenceRepository extends JpaRepository<ReceiptPersistenceEntity, String> {
    List<ReceiptPersistenceEntity> findAllByClientId(String clientId);
    List<ReceiptPersistenceEntity> findAllByReservationId(String reservationId);
}
