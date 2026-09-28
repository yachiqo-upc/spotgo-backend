package com.yachiqo.spotgo.profiles.application.internal.commandservices;

import com.yachiqo.spotgo.profiles.application.ProfilesCommandService;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Favorite;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Vehicle;
import com.yachiqo.spotgo.profiles.domain.model.commands.CreateFavoriteCommand;
import com.yachiqo.spotgo.profiles.domain.model.commands.CreateVehicleCommand;
import com.yachiqo.spotgo.profiles.domain.model.commands.DeleteFavoriteCommand;
import com.yachiqo.spotgo.profiles.domain.model.commands.DeleteVehicleCommand;
import com.yachiqo.spotgo.profiles.domain.model.commands.UpdateVehicleCommand;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.FavoriteRepository;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class ProfilesCommandServiceImpl implements ProfilesCommandService {
    private final VehicleRepository vehicleRepository;
    private final FavoriteRepository favoriteRepository;

    public ProfilesCommandServiceImpl(VehicleRepository vehicleRepository, FavoriteRepository favoriteRepository) {
        this.vehicleRepository = vehicleRepository;
        this.favoriteRepository = favoriteRepository;
    }

    @Override
    public Optional<Vehicle> handle(CreateVehicleCommand command) {
        return Optional.of(vehicleRepository.save(new Vehicle(
                command.clientId(), command.licensePlate(), command.vehicleType(), command.brand(), command.model())));
    }

    @Override
    public Optional<Vehicle> handle(UpdateVehicleCommand command) {
        var vehicleOpt = vehicleRepository.findById(command.vehicleId());
        if (vehicleOpt.isEmpty()) {
            return Optional.empty();
        }
        var vehicle = vehicleOpt.get();
        vehicle.update(command.licensePlate(), command.vehicleType(), command.brand(), command.model());
        return Optional.of(vehicleRepository.save(vehicle));
    }

    @Override
    public void handle(DeleteVehicleCommand command) {
        vehicleRepository.deleteById(command.vehicleId());
    }

    @Override
    public Optional<Favorite> handle(CreateFavoriteCommand command) {
        return Optional.of(favoriteRepository.save(new Favorite(
                command.clientId(), command.parkingId(), command.distanceMi(), command.lastVisited())));
    }

    @Override
    public void handle(DeleteFavoriteCommand command) {
        favoriteRepository.deleteById(command.favoriteId());
    }
}
