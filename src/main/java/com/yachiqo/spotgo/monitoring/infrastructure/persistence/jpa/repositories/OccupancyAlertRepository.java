package com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.OccupancyAlert;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
public interface OccupancyAlertRepository extends JpaRepository<OccupancyAlert,UUID> {
 @Query("select a from OccupancyAlert a where a.tenant.id=:id") List<OccupancyAlert> byTenant(@Param("id") UUID id);
 @Query("select a from OccupancyAlert a where a.conflict.id=:id") List<OccupancyAlert> byConflict(@Param("id") UUID id);
 @Query("select a from OccupancyAlert a where a.healthEvent.id=:id") List<OccupancyAlert> byHealthEvent(@Param("id") UUID id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from OccupancyAlert e where e.id=:id") Optional<OccupancyAlert> lock(@Param("id") UUID id);
}
