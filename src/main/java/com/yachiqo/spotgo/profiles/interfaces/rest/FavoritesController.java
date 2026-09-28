package com.yachiqo.spotgo.profiles.interfaces.rest;

import com.yachiqo.spotgo.profiles.application.ProfilesCommandService;
import com.yachiqo.spotgo.profiles.application.ProfilesQueryService;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Favorite;
import com.yachiqo.spotgo.profiles.domain.model.commands.CreateFavoriteCommand;
import com.yachiqo.spotgo.profiles.domain.model.commands.DeleteFavoriteCommand;
import com.yachiqo.spotgo.profiles.domain.model.queries.GetFavoritesByClientIdQuery;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.FavoriteRepository;
import com.yachiqo.spotgo.profiles.interfaces.rest.resources.CreateFavoriteResource;
import com.yachiqo.spotgo.profiles.interfaces.rest.resources.FavoriteResource;
import com.yachiqo.spotgo.shared.application.security.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/favorites")
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Favorites", description = "Endpoints for managing client favorite parking facilities")
public class FavoritesController {

    private final ProfilesCommandService profilesCommandService;
    private final ProfilesQueryService profilesQueryService;
    private final FavoriteRepository favoriteRepository;
    private final AuthorizationService authorizationService;

    public FavoritesController(ProfilesCommandService profilesCommandService, ProfilesQueryService profilesQueryService,
                               FavoriteRepository favoriteRepository, AuthorizationService authorizationService) {
        this.profilesCommandService = profilesCommandService;
        this.profilesQueryService = profilesQueryService;
        this.favoriteRepository = favoriteRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    @Operation(summary = "Get favorites", description = "Returns favorites, optionally filtered by client identifier.")
    @ApiResponse(responseCode = "200", description = "Favorites returned successfully",
            content = @Content(schema = @Schema(implementation = FavoriteResource.class)))
    public ResponseEntity<List<FavoriteResource>> getFavorites(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                                               @RequestParam(required = false) String clientId) {
        if (clientId != null && !clientId.equals(principal.getUserId())) {
            throw new AccessDeniedException("Requested client is outside authenticated scope");
        }
        var favorites = profilesQueryService.handle(new GetFavoritesByClientIdQuery(principal.getUserId()));
        return ResponseEntity.ok(favorites.stream().map(this::toResource).toList());
    }

    @PostMapping
    @Operation(summary = "Create favorite", description = "Creates a new favorite parking reference for a client.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Favorite created successfully",
                    content = @Content(schema = @Schema(implementation = FavoriteResource.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
    })
    public ResponseEntity<FavoriteResource> createFavorite(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                                           @Valid @RequestBody CreateFavoriteResource resource) {
        var favorite = profilesCommandService.handle(new CreateFavoriteCommand(
                principal.getUserId(), resource.parkingId(), resource.distanceMi(), resource.lastVisited()));
        return favorite.map(value -> ResponseEntity.status(HttpStatus.CREATED).body(toResource(value)))
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    @DeleteMapping("/{favoriteId}")
    @Operation(summary = "Delete favorite", description = "Deletes a favorite by identifier.")
    @ApiResponse(responseCode = "204", description = "Favorite deleted successfully")
    public ResponseEntity<Void> deleteFavorite(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                               @PathVariable String favoriteId) {
        var favorite = favoriteRepository.findById(favoriteId)
                .orElseThrow(() -> new IllegalArgumentException("Favorite not found"));
        authorizationService.requireFavoriteOwner(principal, favorite);
        profilesCommandService.handle(new DeleteFavoriteCommand(favoriteId));
        return ResponseEntity.noContent().build();
    }

    private FavoriteResource toResource(Favorite favorite) {
        return new FavoriteResource(favorite.getId(), favorite.getClientId(), favorite.getParkingId(), favorite.getDistanceMi(), favorite.getLastVisited());
    }
}
