package com.yachiqo.spotgo.profiles.domain.model.commands;

public record CreateVehicleCommand(String clientId, String licensePlate, String vehicleType, String brand, String model) {
}
