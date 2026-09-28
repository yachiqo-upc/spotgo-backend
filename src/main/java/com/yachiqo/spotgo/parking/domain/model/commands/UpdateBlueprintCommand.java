package com.yachiqo.spotgo.parking.domain.model.commands;

public record UpdateBlueprintCommand(
        String blueprintId,
        String name,
        String dataUrl
) {}
