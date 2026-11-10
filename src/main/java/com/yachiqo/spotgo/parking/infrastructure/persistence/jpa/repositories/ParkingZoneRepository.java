package com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.parking.domain.model.aggregates.ParkingZone;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
public interface ParkingZoneRepository extends JpaRepository<ParkingZone,UUID> { @Query("select z from ParkingZone z where z.tenant.id=:id") List<ParkingZone> byTenant(@Param("id") UUID id);
@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select z from ParkingZone z where z.id=:id") Optional<ParkingZone> lock(@Param("id") UUID id); }
