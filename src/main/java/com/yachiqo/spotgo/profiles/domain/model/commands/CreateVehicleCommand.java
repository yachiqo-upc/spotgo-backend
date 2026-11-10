package com.yachiqo.spotgo.profiles.domain.model.commands;
import java.util.UUID;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.VehicleStatus;
public record CreateVehicleCommand(UUID profileId, String plate, String make, String model, String color) {}
