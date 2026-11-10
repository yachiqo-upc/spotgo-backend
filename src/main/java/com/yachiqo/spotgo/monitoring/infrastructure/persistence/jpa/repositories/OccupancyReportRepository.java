package com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.OccupancyReport;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
public interface OccupancyReportRepository extends JpaRepository<OccupancyReport,UUID> {
 @Query("select r from OccupancyReport r where r.tenant.id=:id") List<OccupancyReport> byTenant(@Param("id") UUID id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from OccupancyReport e where e.id=:id") Optional<OccupancyReport> lock(@Param("id") UUID id);
}
