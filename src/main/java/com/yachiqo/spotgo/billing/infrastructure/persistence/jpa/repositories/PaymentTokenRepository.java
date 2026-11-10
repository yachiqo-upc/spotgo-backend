package com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.billing.domain.model.aggregates.PaymentToken;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface PaymentTokenRepository extends JpaRepository<PaymentToken,UUID> {
 @Query("select t from PaymentToken t where t.profile.id=:id") List<PaymentToken> byProfile(@Param("id") UUID id);
@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from PaymentToken t where t.id=:id") Optional<PaymentToken> lock(@Param("id") UUID id);
}
