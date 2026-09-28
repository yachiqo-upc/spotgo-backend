package com.yachiqo.spotgo.parking.domain.model.commands;

import com.yachiqo.spotgo.parking.domain.model.valueobjects.SpotStatus;

public record CreateDetectedSpotCommand(
        Integer localId,
        String blueprintId,
        String parkingId,
        Integer row,
        Integer col,
        Double xPct,
        Double yPct,
        Double wPct,
        Double hPct,
        SpotStatus status
) {
}
