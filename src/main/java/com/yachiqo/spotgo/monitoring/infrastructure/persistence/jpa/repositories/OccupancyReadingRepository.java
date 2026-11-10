package com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.OccupancyReading;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
public interface OccupancyReadingRepository extends JpaRepository<OccupancyReading,UUID> {
 @Query("select r from OccupancyReading r where r.sensor.id=:id order by r.occurredAt desc,r.receivedAt desc") List<OccupancyReading> bySensor(@Param("id") UUID id);
 @Query("select r from OccupancyReading r where r.sensor.id=:sensor and r.payloadHash=:hash") Optional<OccupancyReading> byHash(@Param("sensor") UUID sensor,@Param("hash") String hash);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from OccupancyReading e where e.id=:id") Optional<OccupancyReading> lock(@Param("id") UUID id);
}
