package com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.billing.domain.model.aggregates.DigitalPayment;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface DigitalPaymentRepository extends JpaRepository<DigitalPayment,UUID> {
 Optional<DigitalPayment> findByIdempotencyKey(String key);
@Query("select p from DigitalPayment p where p.profile.id=:id order by p.createdAt desc") List<DigitalPayment> byProfile(@Param("id") UUID id);
@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from DigitalPayment p where p.id=:id") Optional<DigitalPayment> lock(@Param("id") UUID id);
}
