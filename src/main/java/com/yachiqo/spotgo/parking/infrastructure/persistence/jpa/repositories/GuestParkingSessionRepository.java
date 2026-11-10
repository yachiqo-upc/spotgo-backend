package com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.parking.domain.model.aggregates.GuestParkingSession;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
public interface GuestParkingSessionRepository extends JpaRepository<GuestParkingSession,UUID> { @Query("select s from GuestParkingSession s where s.tenant.id=:id") List<GuestParkingSession> byTenant(@Param("id") UUID id);
@Query("select s from GuestParkingSession s where s.spot.id=:id and s.exitAt is null") List<GuestParkingSession> openSpot(@Param("id") UUID id);
@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from GuestParkingSession s where s.id=:id") Optional<GuestParkingSession> lock(@Param("id") UUID id); }
