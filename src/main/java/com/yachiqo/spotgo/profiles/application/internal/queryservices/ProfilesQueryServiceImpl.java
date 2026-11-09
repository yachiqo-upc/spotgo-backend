package com.yachiqo.spotgo.profiles.application.internal.queryservices;

import com.yachiqo.spotgo.profiles.application.ProfilesQueryService;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Vehicle;
import com.yachiqo.spotgo.profiles.domain.model.queries.GetAllVehiclesQuery;
import com.yachiqo.spotgo.profiles.domain.model.queries.GetVehiclesByProfileIdQuery;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProfilesQueryServiceImpl implements ProfilesQueryService {
    private final VehicleRepository vehicleRepository;

    public ProfilesQueryServiceImpl(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    public List<Vehicle> handle(GetAllVehiclesQuery query) {
        return vehicleRepository.findAll();
    }

    @Override
    public List<Vehicle> handle(GetVehiclesByProfileIdQuery query) {
        return vehicleRepository.findAllByProfileId(query.profileId());
    }


}
