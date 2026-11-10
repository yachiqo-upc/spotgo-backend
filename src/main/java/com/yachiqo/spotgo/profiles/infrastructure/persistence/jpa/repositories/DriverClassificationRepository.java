package com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.DriverClassification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface DriverClassificationRepository extends JpaRepository<DriverClassification, UUID> {
    @org.springframework.data.jpa.repository.Query("select c from DriverClassification c where c.profile.id = :profileId and c.tenantId = :tenantId")
    Optional<DriverClassification> findByProfileIdAndTenantId(@org.springframework.data.repository.query.Param("profileId") UUID profileId,
        @org.springframework.data.repository.query.Param("tenantId") UUID tenantId);
    List<DriverClassification> findAllByTenantId(UUID tenantId);
    @org.springframework.data.jpa.repository.Query("select c from DriverClassification c where c.profile.id = :profileId")
    List<DriverClassification> findAllByProfileId(@org.springframework.data.repository.query.Param("profileId") UUID profileId);
}
