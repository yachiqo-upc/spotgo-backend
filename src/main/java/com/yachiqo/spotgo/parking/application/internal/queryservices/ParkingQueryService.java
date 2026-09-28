package com.yachiqo.spotgo.parking.application.internal.queryservices;

import com.yachiqo.spotgo.parking.domain.model.queries.GetAllParkingsQuery;
import com.yachiqo.spotgo.parking.domain.model.queries.GetBlueprintsByParkingIdQuery;
import com.yachiqo.spotgo.parking.domain.model.queries.GetParkingByIdQuery;
import com.yachiqo.spotgo.parking.domain.model.queries.GetSpotsByBlueprintIdQuery;
import com.yachiqo.spotgo.parking.domain.model.queries.GetAllReservationsQuery;
import com.yachiqo.spotgo.parking.domain.model.queries.GetReservationsByParkingIdQuery;
import com.yachiqo.spotgo.parking.domain.model.queries.GetReservationsByClientIdQuery;
import com.yachiqo.spotgo.parking.domain.model.queries.GetDetectedSpotsByParkingIdQuery;
import com.yachiqo.spotgo.parking.domain.model.queries.GetAllDetectedSpotsQuery;
import com.yachiqo.spotgo.parking.domain.model.queries.GetAllBlueprintsQuery;
import com.yachiqo.spotgo.parking.domain.model.queries.GetAllClientReportsQuery;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Blueprint;
import com.yachiqo.spotgo.parking.domain.model.aggregates.DetectedSpot;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Parking;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Reservation;
import com.yachiqo.spotgo.parking.domain.model.aggregates.ClientReport;

import java.util.List;
import java.util.Optional;

public interface ParkingQueryService {
    Optional<Parking> handle(GetParkingByIdQuery query);
    List<Parking> handle(GetAllParkingsQuery query);
    List<Blueprint> handle(GetBlueprintsByParkingIdQuery query);
    List<DetectedSpot> handle(GetSpotsByBlueprintIdQuery query);
    List<Reservation> handle(GetAllReservationsQuery query);
    List<Reservation> handle(GetReservationsByParkingIdQuery query);
    List<Reservation> handle(GetReservationsByClientIdQuery query);
    List<DetectedSpot> handle(GetDetectedSpotsByParkingIdQuery query);
    List<DetectedSpot> handle(GetAllDetectedSpotsQuery query);
    List<Blueprint> handle(GetAllBlueprintsQuery query);
    List<ClientReport> handle(GetAllClientReportsQuery query);
}
