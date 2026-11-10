package com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.billing.domain.model.aggregates.OutstandingBalance;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface OutstandingBalanceRepository extends JpaRepository<OutstandingBalance,UUID> {
 @Query("select b from OutstandingBalance b where b.profile.id=:id order by b.createdAt desc") List<OutstandingBalance> byProfile(@Param("id") UUID id);
@Query("select b from OutstandingBalance b where b.charge.reservation.tenant.id=:id order by b.createdAt desc") List<OutstandingBalance> byTenant(@Param("id") UUID id);
@Query("select b from OutstandingBalance b where b.charge.id=:id") Optional<OutstandingBalance> byCharge(@Param("id") UUID id);
@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from OutstandingBalance b where b.id=:id") Optional<OutstandingBalance> lock(@Param("id") UUID id);
}
