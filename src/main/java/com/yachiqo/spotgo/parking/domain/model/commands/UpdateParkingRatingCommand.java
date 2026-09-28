package com.yachiqo.spotgo.parking.domain.model.commands;

public record UpdateParkingRatingCommand(String parkingId, Double rating) {
}
