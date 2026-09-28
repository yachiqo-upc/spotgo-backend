package com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories;

import com.yachiqo.spotgo.monitoring.domain.model.aggregates.WeeklyTrend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WeeklyTrendRepository extends JpaRepository<WeeklyTrend, String> {
    List<WeeklyTrend> findByParkingId(String parkingId);
}
