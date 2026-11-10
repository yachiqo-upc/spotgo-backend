package com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.billing.domain.model.aggregates.Refund;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface RefundRepository extends JpaRepository<Refund,UUID> {
 @Query("select r from Refund r where r.payment.id=:id order by r.requestedAt") List<Refund> byPayment(@Param("id") UUID id);
@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from Refund r where r.id=:id") Optional<Refund> lock(@Param("id") UUID id);
}
