package com.yachiqo.spotgo.favorites.infrastructure.persistence.jpa.repositories;

import com.yachiqo.spotgo.favorites.domain.model.aggregates.FavoriteTenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteTenantRepository extends JpaRepository<FavoriteTenant, UUID> {
    List<FavoriteTenant> findAllByProfile_IdOrderByCreatedAtDesc(UUID profileId);

    Optional<FavoriteTenant> findByProfile_IdAndTenant_Id(UUID profileId, UUID tenantId);

    Optional<FavoriteTenant> findByIdAndProfile_Id(UUID favoriteId, UUID profileId);
}
