package com.yachiqo.spotgo.profiles.application;

import com.yachiqo.spotgo.profiles.domain.model.aggregates.Vehicle;
import com.yachiqo.spotgo.profiles.domain.model.queries.GetVehiclesByProfileIdQuery;

import java.util.List;

public interface ProfilesQueryService {
    List<Vehicle> handle(GetVehiclesByProfileIdQuery query);
}
