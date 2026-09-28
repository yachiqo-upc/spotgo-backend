package com.yachiqo.spotgo.profiles.domain.model.commands;

public record CreateFavoriteCommand(String clientId, String parkingId, Double distanceMi, String lastVisited) {
}
