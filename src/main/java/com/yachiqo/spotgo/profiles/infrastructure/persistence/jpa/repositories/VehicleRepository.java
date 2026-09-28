package com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories;

import com.yachiqo.spotgo.profiles.domain.model.aggregates.Vehicle;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, String> {
    List<Vehicle> findAllByClientId(String clientId);
}
