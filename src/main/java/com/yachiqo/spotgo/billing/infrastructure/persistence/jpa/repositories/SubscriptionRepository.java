package com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.billing.domain.model.aggregates.Subscription;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface SubscriptionRepository extends JpaRepository<Subscription,UUID> {
 @Query("select s from Subscription s where s.profile.id=:id order by s.startAt desc") List<Subscription> byProfile(@Param("id") UUID id);
@Query("select s from Subscription s where s.tenant.id=:id order by s.startAt desc") List<Subscription> byTenant(@Param("id") UUID id);
@Query("select s from Subscription s where s.payment.id=:id") Optional<Subscription> byPayment(@Param("id") UUID id);
@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from Subscription s where s.id=:id") Optional<Subscription> lock(@Param("id") UUID id);
}
