package com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.parking.domain.model.aggregates.ParkingSpot;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
public interface ParkingSpotRepository extends JpaRepository<ParkingSpot,UUID> { @Query("select s from ParkingSpot s where s.zone.id=:id") List<ParkingSpot> byZone(@Param("id") UUID id);
@Query("select s from ParkingSpot s where s.zone.tenant.id=:id") List<ParkingSpot> byTenant(@Param("id") UUID id);
@Query("select count(s) from ParkingSpot s where s.zone.tenant.id=:id") long countByTenant(@Param("id") UUID id);
@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from ParkingSpot s where s.id=:id") Optional<ParkingSpot> lock(@Param("id") UUID id); }
