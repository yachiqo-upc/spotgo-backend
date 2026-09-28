package com.yachiqo.spotgo.parking.domain.model.commands;

import com.yachiqo.spotgo.parking.domain.model.valueobjects.SpotStatus;

public record UpdateSpotStatusCommand(String spotId, SpotStatus status) {
}
