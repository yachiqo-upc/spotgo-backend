package com.yachiqo.spotgo.parking.domain.model.commands;

public record UpdateParkingCommand(String parkingId, Integer totalSpaces, Integer availableSpaces, Integer totalFloors, String city, Double rating, Double pricePerHour) {
}
