package com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.parking.domain.model.aggregates.ParkingSession;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
public interface ParkingSessionRepository extends JpaRepository<ParkingSession,UUID> { @Query("select s from ParkingSession s where s.reservation.tenant.id=:id") List<ParkingSession> byTenant(@Param("id") UUID id);
@Query("select s from ParkingSession s where s.profile.id=:id") List<ParkingSession> byProfile(@Param("id") UUID id);
@Query("select s from ParkingSession s where s.vehicle.id=:id and s.exitAt is null") List<ParkingSession> openVehicle(@Param("id") UUID id);
@Query("select s from ParkingSession s where s.spot.id=:id and s.exitAt is null") List<ParkingSession> openSpot(@Param("id") UUID id);
@Query("select s from ParkingSession s where s.reservation.id=:id") Optional<ParkingSession> byReservation(@Param("id") UUID id);
@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from ParkingSession s where s.id=:id") Optional<ParkingSession> lock(@Param("id") UUID id); }
