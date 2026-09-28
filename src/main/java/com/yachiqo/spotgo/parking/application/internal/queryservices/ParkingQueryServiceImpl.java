package com.yachiqo.spotgo.parking.application.internal.queryservices;

import com.yachiqo.spotgo.parking.domain.model.aggregates.Blueprint;
import com.yachiqo.spotgo.parking.domain.model.aggregates.DetectedSpot;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Parking;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Reservation;
import com.yachiqo.spotgo.parking.domain.model.aggregates.ClientReport;
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
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.BlueprintRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.DetectedSpotRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ParkingRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ClientReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ParkingQueryServiceImpl implements ParkingQueryService {

    private final ParkingRepository parkingRepository;
    private final BlueprintRepository blueprintRepository;
    private final DetectedSpotRepository detectedSpotRepository;
    private final ReservationRepository reservationRepository;
    private final ClientReportRepository clientReportRepository;

    public ParkingQueryServiceImpl(ParkingRepository parkingRepository, BlueprintRepository blueprintRepository, DetectedSpotRepository detectedSpotRepository, ReservationRepository reservationRepository, ClientReportRepository clientReportRepository) {
        this.parkingRepository = parkingRepository;
        this.blueprintRepository = blueprintRepository;
        this.detectedSpotRepository = detectedSpotRepository;
        this.reservationRepository = reservationRepository;
        this.clientReportRepository = clientReportRepository;
    }

    @Override
    public Optional<Parking> handle(GetParkingByIdQuery query) {
        return parkingRepository.findById(query.parkingId());
    }

    @Override
    public List<Parking> handle(GetAllParkingsQuery query) {
        return parkingRepository.findAll();
    }

    @Override
    public List<Blueprint> handle(GetBlueprintsByParkingIdQuery query) {
        return blueprintRepository.findByParkingId(query.parkingId());
    }

    @Override
    public List<DetectedSpot> handle(GetSpotsByBlueprintIdQuery query) {
        return detectedSpotRepository.findByBlueprintId(query.blueprintId());
    }

    @Override
    public List<Reservation> handle(GetAllReservationsQuery query) {
        return reservationRepository.findAll();
    }

    @Override
    public List<Reservation> handle(GetReservationsByParkingIdQuery query) {
        return reservationRepository.findByParkingId(query.parkingId());
    }

    @Override
    public List<Reservation> handle(GetReservationsByClientIdQuery query) {
        return reservationRepository.findByClientId(query.clientId());
    }

    @Override
    public List<DetectedSpot> handle(GetDetectedSpotsByParkingIdQuery query) {
        return detectedSpotRepository.findByParkingId(query.parkingId());
    }

    @Override
    public List<DetectedSpot> handle(GetAllDetectedSpotsQuery query) {
        return detectedSpotRepository.findAll();
    }

    @Override
    public List<Blueprint> handle(GetAllBlueprintsQuery query) {
        return blueprintRepository.findAll();
    }

    @Override
    public List<ClientReport> handle(GetAllClientReportsQuery query) {
        return clientReportRepository.findAll();
    }
}
