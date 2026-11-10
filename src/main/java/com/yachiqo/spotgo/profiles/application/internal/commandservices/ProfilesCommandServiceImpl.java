package com.yachiqo.spotgo.profiles.application.internal.commandservices;

import com.yachiqo.spotgo.profiles.application.ProfilesCommandService;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Vehicle;
import com.yachiqo.spotgo.profiles.domain.model.commands.CreateVehicleCommand;
import com.yachiqo.spotgo.profiles.domain.model.commands.DeleteVehicleCommand;
import com.yachiqo.spotgo.profiles.domain.model.commands.UpdateVehicleCommand;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.VehicleRepository;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.ProfileRepository;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.VehicleStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class ProfilesCommandServiceImpl implements ProfilesCommandService {
    private final VehicleRepository vehicleRepository;
    private final ProfileRepository profileRepository;

    public ProfilesCommandServiceImpl(VehicleRepository vehicleRepository,
                                      ProfileRepository profileRepository) {
        this.vehicleRepository = vehicleRepository;
        this.profileRepository = profileRepository;
    }

    @Override
    public Optional<Vehicle> handle(CreateVehicleCommand command) {
        var profile = profileRepository.findById(command.profileId())
                .orElseThrow(() -> new IllegalArgumentException("Perfil inexistente"));
        return Optional.of(vehicleRepository.save(new Vehicle(profile,
                command.plate(), command.make(), command.model(), command.color())));
    }

    @Override
    public Optional<Vehicle> handle(UpdateVehicleCommand command) {
        var vehicleOpt = vehicleRepository.findById(command.vehicleId());
        if (vehicleOpt.isEmpty()) {
            return Optional.empty();
        }
        var vehicle = vehicleOpt.get();
        vehicle.update(command.plate(), command.make(), command.model(), command.color(), command.status());
        return Optional.of(vehicleRepository.save(vehicle));
    }

    @Override
    public void handle(DeleteVehicleCommand command) {
        var vehicle = vehicleRepository.findById(command.vehicleId())
                .orElseThrow(() -> new IllegalArgumentException("Vehiculo inexistente"));
        vehicle.update(null, null, null, null, VehicleStatus.INACTIVE);
        vehicleRepository.save(vehicle);
    }


}
