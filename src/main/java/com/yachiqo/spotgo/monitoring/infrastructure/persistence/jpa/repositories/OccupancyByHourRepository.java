package com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories;

import com.yachiqo.spotgo.monitoring.domain.model.aggregates.OccupancyByHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OccupancyByHourRepository extends JpaRepository<OccupancyByHour, String> {
    List<OccupancyByHour> findByParkingId(String parkingId);
}
