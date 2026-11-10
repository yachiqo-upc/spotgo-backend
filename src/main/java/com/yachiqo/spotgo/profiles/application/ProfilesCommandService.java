package com.yachiqo.spotgo.profiles.application;

import com.yachiqo.spotgo.profiles.domain.model.aggregates.Vehicle;
import com.yachiqo.spotgo.profiles.domain.model.commands.CreateVehicleCommand;
import com.yachiqo.spotgo.profiles.domain.model.commands.DeleteVehicleCommand;
import com.yachiqo.spotgo.profiles.domain.model.commands.UpdateVehicleCommand;

import java.util.Optional;

public interface ProfilesCommandService {
    Optional<Vehicle> handle(CreateVehicleCommand command);
    Optional<Vehicle> handle(UpdateVehicleCommand command);
    void handle(DeleteVehicleCommand command);
}
