package com.yachiqo.spotgo.favorites.interfaces.rest;

import com.yachiqo.spotgo.favorites.application.FavoritesService;
import com.yachiqo.spotgo.favorites.interfaces.rest.resources.CreateFavoriteResource;
import com.yachiqo.spotgo.favorites.interfaces.rest.resources.FavoriteResource;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/favorites", produces = "application/json")
@Tag(name = "Favorites", description = "Parking tenants saved by the authenticated Driver profile.")
public class FavoritesController {
    private final FavoritesService service;

    public FavoritesController(FavoritesService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List my favorite parking facilities")
    public List<FavoriteResource> list(@AuthenticationPrincipal SpotgoUserPrincipal actor) {
        return service.list(actor).stream().map(FavoriteResource::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Save a parking facility to my favorites", description = "Idempotently creates or returns the Driver's favorite for the tenant.")
    public FavoriteResource add(
        @AuthenticationPrincipal SpotgoUserPrincipal actor,
        @Valid @RequestBody CreateFavoriteResource body
    ) {
        return FavoriteResource.from(service.add(actor, body.tenantId()));
    }

    @DeleteMapping("/{favoriteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove one of my favorite parking facilities")
    public void remove(
        @AuthenticationPrincipal SpotgoUserPrincipal actor,
        @PathVariable UUID favoriteId
    ) {
        service.remove(actor, favoriteId);
    }
}
