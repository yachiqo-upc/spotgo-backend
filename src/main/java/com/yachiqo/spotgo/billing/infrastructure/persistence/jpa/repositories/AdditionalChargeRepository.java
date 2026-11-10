package com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.billing.domain.model.aggregates.AdditionalCharge;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface AdditionalChargeRepository extends JpaRepository<AdditionalCharge,UUID> {
 @Query("select c from AdditionalCharge c where c.reservation.id=:id order by c.createdAt") List<AdditionalCharge> byReservation(@Param("id") UUID id);
@Query("select c from AdditionalCharge c where c.reservation.profile.id=:id order by c.createdAt desc") List<AdditionalCharge> byProfile(@Param("id") UUID id);
@Query("select c from AdditionalCharge c where c.reservation.tenant.id=:id order by c.createdAt desc") List<AdditionalCharge> byTenant(@Param("id") UUID id);
@Query("select c from AdditionalCharge c where c.payment.id=:id") Optional<AdditionalCharge> byPayment(@Param("id") UUID id);
@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from AdditionalCharge c where c.id=:id") Optional<AdditionalCharge> lock(@Param("id") UUID id);
}
