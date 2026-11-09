package com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.DriverClassification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface DriverClassificationRepository extends JpaRepository<DriverClassification, UUID> {
    Optional<DriverClassification> findByProfileIdAndTenantId(UUID profileId, UUID tenantId);
    List<DriverClassification> findAllByTenantId(UUID tenantId);
    List<DriverClassification> findAllByProfileId(UUID profileId);
}
