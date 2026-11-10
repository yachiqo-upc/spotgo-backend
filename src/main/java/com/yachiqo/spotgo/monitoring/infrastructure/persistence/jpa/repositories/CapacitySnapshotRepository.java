package com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.CapacitySnapshot;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
public interface CapacitySnapshotRepository extends JpaRepository<CapacitySnapshot,UUID> {
 @Query("select s from CapacitySnapshot s where s.tenant.id=:id") List<CapacitySnapshot> byTenant(@Param("id") UUID id);
 @Query("select s from CapacitySnapshot s where s.zone.id=:zone and s.observedAt>=:start and s.observedAt<:finish order by s.observedAt") List<CapacitySnapshot> interval(@Param("zone") UUID zone,@Param("start") Instant start,@Param("finish") Instant finish);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from CapacitySnapshot e where e.id=:id") Optional<CapacitySnapshot> lock(@Param("id") UUID id);
}
