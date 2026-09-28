package com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories;

import com.yachiqo.spotgo.parking.domain.model.aggregates.DetectedSpot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DetectedSpotRepository extends JpaRepository<DetectedSpot, String> {
    List<DetectedSpot> findByBlueprintId(String blueprintId);
    List<DetectedSpot> findByParkingIdAndRowAndCol(String parkingId, Integer row, Integer col);

    @Query("SELECT d FROM DetectedSpot d WHERE d.blueprintId IN (SELECT b.id FROM Blueprint b WHERE b.parkingId = :parkingId)")
    List<DetectedSpot> findByParkingId(@Param("parkingId") String parkingId);
}
