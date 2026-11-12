package com.yachiqo.spotgo.favorites.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateFavoriteResource(@NotNull UUID tenantId) {}
