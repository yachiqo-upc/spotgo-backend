package com.yachiqo.spotgo.profiles.application.internal.commandservices;

import com.yachiqo.spotgo.profiles.application.ProfilesCommandService;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Vehicle;
import com.yachiqo.spotgo.profiles.domain.model.commands.CreateVehicleCommand;
import com.yachiqo.spotgo.profiles.domain.model.commands.DeleteVehicleCommand;
import com.yachiqo.spotgo.profiles.domain.model.commands.UpdateVehicleCommand;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.VehicleRepository;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.ProfileRepository;
import com.yachiqo.spotgo.shared.application.events.SpotgoDomainEventPublisher;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.VehicleStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ProfilesCommandServiceImpl implements ProfilesCommandService {
    private final VehicleRepository vehicleRepository;
    private final ProfileRepository profileRepository;
    private final SpotgoDomainEventPublisher domainEvents;

    public ProfilesCommandServiceImpl(VehicleRepository vehicleRepository,
                                      ProfileRepository profileRepository, SpotgoDomainEventPublisher domainEvents) {
        this.vehicleRepository = vehicleRepository;
        this.profileRepository = profileRepository;
        this.domainEvents = domainEvents;
    }

    @Override
    public Optional<Vehicle> handle(CreateVehicleCommand command) {
        var profile = profileRepository.findById(command.profileId())
                .orElseThrow(() -> new IllegalArgumentException("Profile not found."));
        var vehicle=vehicleRepository.save(new Vehicle(profile,
                command.plate(), command.make(), command.model(), command.color()));
        domainEvents.publish("profiles.vehicle.registered","Profiles & Vehicles Management","Vehicle",vehicle.getId(),null,
                List.of(),Map.of("vehicle_id",vehicle.getId().toString(),"profile_id",profile.getId().toString()));
        return Optional.of(vehicle);
    }

    @Override
    public Optional<Vehicle> handle(UpdateVehicleCommand command) {
        var vehicleOpt = vehicleRepository.findById(command.vehicleId());
        if (vehicleOpt.isEmpty()) {
            return Optional.empty();
        }
        var vehicle = vehicleOpt.get();
        vehicle.update(command.plate(), command.make(), command.model(), command.color(), command.status());
        var saved=vehicleRepository.save(vehicle);
        String type=saved.getStatus()==VehicleStatus.INACTIVE?"profiles.vehicle.deactivated":"profiles.vehicle.updated";
        domainEvents.publish(type,"Profiles & Vehicles Management","Vehicle",saved.getId(),null,List.of(),
                Map.of("vehicle_id",saved.getId().toString(),"profile_id",saved.getProfileId().toString()));
        return Optional.of(saved);
    }

    @Override
    public void handle(DeleteVehicleCommand command) {
        var vehicle = vehicleRepository.findById(command.vehicleId())
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found."));
        vehicle.update(null, null, null, null, VehicleStatus.INACTIVE);
        vehicleRepository.save(vehicle);
        domainEvents.publish("profiles.vehicle.deactivated","Profiles & Vehicles Management","Vehicle",vehicle.getId(),null,List.of(),
                Map.of("vehicle_id",vehicle.getId().toString(),"profile_id",vehicle.getProfileId().toString()));
    }


}
