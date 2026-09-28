package com.yachiqo.spotgo.profiles.application.internal.queryservices;

import com.yachiqo.spotgo.profiles.application.ProfilesQueryService;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Favorite;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Vehicle;
import com.yachiqo.spotgo.profiles.domain.model.queries.GetAllFavoritesQuery;
import com.yachiqo.spotgo.profiles.domain.model.queries.GetAllVehiclesQuery;
import com.yachiqo.spotgo.profiles.domain.model.queries.GetFavoritesByClientIdQuery;
import com.yachiqo.spotgo.profiles.domain.model.queries.GetVehiclesByClientIdQuery;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.FavoriteRepository;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProfilesQueryServiceImpl implements ProfilesQueryService {
    private final VehicleRepository vehicleRepository;
    private final FavoriteRepository favoriteRepository;

    public ProfilesQueryServiceImpl(VehicleRepository vehicleRepository, FavoriteRepository favoriteRepository) {
        this.vehicleRepository = vehicleRepository;
        this.favoriteRepository = favoriteRepository;
    }

    @Override
    public List<Vehicle> handle(GetAllVehiclesQuery query) {
        return vehicleRepository.findAll();
    }

    @Override
    public List<Vehicle> handle(GetVehiclesByClientIdQuery query) {
        return vehicleRepository.findAllByClientId(query.clientId());
    }

    @Override
    public List<Favorite> handle(GetAllFavoritesQuery query) {
        return favoriteRepository.findAll();
    }

    @Override
    public List<Favorite> handle(GetFavoritesByClientIdQuery query) {
        return favoriteRepository.findAllByClientId(query.clientId());
    }
}
