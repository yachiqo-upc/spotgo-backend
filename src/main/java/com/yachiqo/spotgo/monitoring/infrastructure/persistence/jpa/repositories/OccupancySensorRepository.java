package com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.OccupancySensor;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
public interface OccupancySensorRepository extends JpaRepository<OccupancySensor,UUID> {
 @Query("select s from OccupancySensor s where s.tenant.id=:id") List<OccupancySensor> byTenant(@Param("id") UUID id);
 @Query("select s from OccupancySensor s where s.spot.id=:id and s.status=com.yachiqo.spotgo.monitoring.domain.model.valueobjects.SensorStatus.ACTIVE") List<OccupancySensor> activeSpot(@Param("id") UUID id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from OccupancySensor e where e.id=:id") Optional<OccupancySensor> lock(@Param("id") UUID id);
}
