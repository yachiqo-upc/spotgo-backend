package com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Reservation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
public interface ReservationRepository extends JpaRepository<Reservation,UUID> { @Query("select r from Reservation r where r.tenant.id=:id") List<Reservation> byTenant(@Param("id") UUID id);
@Query("select r from Reservation r where r.profile.id=:id") List<Reservation> byProfile(@Param("id") UUID id);
@Query("select r from Reservation r where r.spot.id=:spot and r.startAt<:finish and r.endAt>:start") List<Reservation> overlappingSpot(@Param("spot") UUID spot,@Param("start") Instant start,@Param("finish") Instant finish);
@Query("select r from Reservation r where r.vehicle.id=:vehicle and r.startAt<:finish and r.endAt>:start") List<Reservation> overlappingVehicle(@Param("vehicle") UUID vehicle,@Param("start") Instant start,@Param("finish") Instant finish);
@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from Reservation r where r.id=:id") Optional<Reservation> lock(@Param("id") UUID id);
boolean existsByPaymentId(UUID paymentId);
@Query("select r from Reservation r where r.paymentId=:id") Optional<Reservation> byPayment(@Param("id") UUID id); }
