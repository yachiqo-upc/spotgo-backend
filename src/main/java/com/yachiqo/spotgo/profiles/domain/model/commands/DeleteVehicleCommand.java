package com.yachiqo.spotgo.profiles.domain.model.commands;
import java.util.UUID;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.VehicleStatus;
public record DeleteVehicleCommand(UUID vehicleId) {}
