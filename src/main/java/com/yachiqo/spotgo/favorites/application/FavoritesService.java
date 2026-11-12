package com.yachiqo.spotgo.favorites.application;

import com.yachiqo.spotgo.favorites.domain.model.aggregates.FavoriteTenant;
import com.yachiqo.spotgo.favorites.infrastructure.persistence.jpa.repositories.FavoriteTenantRepository;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.TenantRepository;
import com.yachiqo.spotgo.profiles.application.internal.ProfilesManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class FavoritesService {
    private final FavoriteTenantRepository favorites;
    private final TenantRepository tenants;
    private final ProfilesManagementService profiles;

    public FavoritesService(
        FavoriteTenantRepository favorites,
        TenantRepository tenants,
        ProfilesManagementService profiles
    ) {
        this.favorites = favorites;
        this.tenants = tenants;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public List<FavoriteTenant> list(SpotgoUserPrincipal actor) {
        requireDriver(actor);
        var profile = profiles.driver(actor);
        return favorites.findAllByProfile_IdOrderByCreatedAtDesc(profile.getId());
    }

    public FavoriteTenant add(SpotgoUserPrincipal actor, UUID tenantId) {
        requireDriver(actor);
        var profile = profiles.driver(actor);
        var tenant = tenants.findById(tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found."));
        return favorites.findByProfile_IdAndTenant_Id(profile.getId(), tenantId)
            .orElseGet(() -> favorites.save(new FavoriteTenant(profile, tenant)));
    }

    public void remove(SpotgoUserPrincipal actor, UUID favoriteId) {
        requireDriver(actor);
        var profile = profiles.driver(actor);
        var favorite = favorites.findByIdAndProfile_Id(favoriteId, profile.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Favorite not found."));
        favorites.delete(favorite);
    }

    private void requireDriver(SpotgoUserPrincipal actor) {
        if (actor == null || actor.getRole() == null || !"DRIVER".equals(actor.getRole().name())) {
            throw new AccessDeniedException("Favorites are available to Drivers only.");
        }
    }
}
