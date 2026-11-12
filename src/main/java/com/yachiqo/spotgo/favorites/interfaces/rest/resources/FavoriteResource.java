package com.yachiqo.spotgo.favorites.interfaces.rest.resources;

import com.yachiqo.spotgo.favorites.domain.model.aggregates.FavoriteTenant;

import java.time.Instant;
import java.util.UUID;

public record FavoriteResource(UUID favoriteId, UUID tenantId, Instant createdAt) {
    public static FavoriteResource from(FavoriteTenant favorite) {
        return new FavoriteResource(favorite.getId(), favorite.getTenantId(), favorite.getCreatedAt());
    }
}
