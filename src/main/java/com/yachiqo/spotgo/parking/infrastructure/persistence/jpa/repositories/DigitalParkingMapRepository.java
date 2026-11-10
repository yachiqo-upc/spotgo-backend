package com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.parking.domain.model.aggregates.DigitalParkingMap;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
public interface DigitalParkingMapRepository extends JpaRepository<DigitalParkingMap,UUID> { @Query("select m from DigitalParkingMap m where m.floorPlan.id=:id order by m.version desc") List<DigitalParkingMap> byPlan(@Param("id") UUID id); }
