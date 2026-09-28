package com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories;

import com.yachiqo.spotgo.parking.domain.model.aggregates.Blueprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BlueprintRepository extends JpaRepository<Blueprint, String> {
    List<Blueprint> findByParkingId(String parkingId);
}
