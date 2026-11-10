package com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.parking.domain.model.aggregates.FloorPlan;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
public interface FloorPlanRepository extends JpaRepository<FloorPlan,UUID> { @Query("select p from FloorPlan p where p.zone.id=:id order by p.version desc") List<FloorPlan> byZone(@Param("id") UUID id);
@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from FloorPlan p where p.id=:id") Optional<FloorPlan> lock(@Param("id") UUID id); }
