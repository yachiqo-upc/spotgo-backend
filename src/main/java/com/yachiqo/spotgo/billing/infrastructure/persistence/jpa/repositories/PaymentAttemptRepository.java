package com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.billing.domain.model.aggregates.PaymentAttempt;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt,UUID> {
 @Query("select a from PaymentAttempt a where a.payment.id=:id order by a.attemptNumber") List<PaymentAttempt> byPayment(@Param("id") UUID id);
}
