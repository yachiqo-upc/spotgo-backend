package com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.SensorHealthEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
public interface SensorHealthEventRepository extends JpaRepository<SensorHealthEvent,UUID> {
 @Query("select e from SensorHealthEvent e where e.sensor.id=:id order by e.observedAt desc") List<SensorHealthEvent> bySensor(@Param("id") UUID id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from SensorHealthEvent e where e.id=:id") Optional<SensorHealthEvent> lock(@Param("id") UUID id);
}
