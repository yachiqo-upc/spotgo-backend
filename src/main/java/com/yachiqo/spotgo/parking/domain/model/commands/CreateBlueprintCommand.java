package com.yachiqo.spotgo.parking.domain.model.commands;

public record CreateBlueprintCommand(String adminId, String parkingId, String name, String dataUrl) {
}
