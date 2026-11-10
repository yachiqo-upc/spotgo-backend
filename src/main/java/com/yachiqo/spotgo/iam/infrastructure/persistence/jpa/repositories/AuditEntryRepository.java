package com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.iam.domain.model.aggregates.AuditEntry;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {
    @Query("select a from AuditEntry a where a.account.id = :accountId")
    Page<AuditEntry> findAllByAccountId(@Param("accountId") UUID accountId, Pageable pageable);
}
