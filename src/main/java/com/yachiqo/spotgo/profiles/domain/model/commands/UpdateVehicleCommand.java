package com.yachiqo.spotgo.profiles.domain.model.commands;

public record UpdateVehicleCommand(String vehicleId, String licensePlate, String vehicleType, String brand, String model) {
}
