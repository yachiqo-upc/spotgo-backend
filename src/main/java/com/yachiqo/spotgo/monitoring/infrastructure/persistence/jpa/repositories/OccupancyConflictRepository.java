package com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.OccupancyConflict;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
public interface OccupancyConflictRepository extends JpaRepository<OccupancyConflict,UUID> {
 @Query("select c from OccupancyConflict c where c.tenant.id=:id") List<OccupancyConflict> byTenant(@Param("id") UUID id);
 @Query("select c from OccupancyConflict c where c.spot.id=:id and c.status<>com.yachiqo.spotgo.monitoring.domain.model.valueobjects.ConflictStatus.RESOLVED") List<OccupancyConflict> openSpot(@Param("id") UUID id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from OccupancyConflict e where e.id=:id") Optional<OccupancyConflict> lock(@Param("id") UUID id);
}
