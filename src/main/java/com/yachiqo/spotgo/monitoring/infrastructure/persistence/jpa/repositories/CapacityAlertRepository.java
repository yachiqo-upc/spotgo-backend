package com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories;

import com.yachiqo.spotgo.monitoring.domain.model.aggregates.CapacityAlert;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.AlertStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CapacityAlertRepository extends JpaRepository<CapacityAlert, UUID> {
    @Query("select a from CapacityAlert a where a.tenant.id = :tenantId order by a.createdAt desc")
    List<CapacityAlert> byTenant(@Param("tenantId") UUID tenantId);

    @Query("select a from CapacityAlert a where a.tenant.id = :tenantId " +
            "and ((:zoneId is null and a.zone is null) or a.zone.id = :zoneId) " +
            "and a.status <> :closedStatus order by a.createdAt desc")
    List<CapacityAlert> activeForScope(@Param("tenantId") UUID tenantId,
                                       @Param("zoneId") UUID zoneId,
                                       @Param("closedStatus") AlertStatus closedStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CapacityAlert a where a.id = :id")
    Optional<CapacityAlert> lock(@Param("id") UUID id);
}
