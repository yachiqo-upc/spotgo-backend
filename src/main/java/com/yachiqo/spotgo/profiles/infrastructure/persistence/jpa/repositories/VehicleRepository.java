package com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "profile")
    java.util.Optional<Vehicle> findById(UUID id);
    List<Vehicle> findAllByProfileId(UUID profileId);
}
