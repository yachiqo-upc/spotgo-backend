package com.yachiqo.spotgo.parking.application;

import com.yachiqo.spotgo.monitoring.application.EmployeeSpotAssignmentService;
import com.yachiqo.spotgo.parking.domain.model.aggregates.DetectedSpot;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationStatus;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.SpotStatus;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.DetectedSpotRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ParkingRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationRepository;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParkingOccupancyService {

    private final ReservationRepository reservationRepository;
    private final DetectedSpotRepository detectedSpotRepository;
    private final ParkingRepository parkingRepository;
    private final EmployeeSpotAssignmentService employeeSpotAssignmentService;

    public ParkingOccupancyService(ReservationRepository reservationRepository,
                                   DetectedSpotRepository detectedSpotRepository,
                                   ParkingRepository parkingRepository,
                                   EmployeeSpotAssignmentService employeeSpotAssignmentService) {
        this.reservationRepository = reservationRepository;
        this.detectedSpotRepository = detectedSpotRepository;
        this.parkingRepository = parkingRepository;
        this.employeeSpotAssignmentService = employeeSpotAssignmentService;
    }

    @Transactional
    public void reconcileParking(String parkingId, LocalDateTime now) {
        var occupiedSpotIds = reservationRepository.findByParkingIdAndStatus(parkingId, ReservationStatus.ACTIVE).stream()
                .filter(reservation -> !reservation.getStartDate().isAfter(now) && reservation.getEndDate().isAfter(now))
                .map(reservation -> reservation.getSpot())
                .collect(Collectors.toSet());
        var reservedSpotIds = employeeSpotAssignmentService.getReservedSpotCodes(parkingId, now);

        var detectedSpots = detectedSpotRepository.findByParkingId(parkingId);
        boolean spotsChanged = false;

        for (var spot : detectedSpots) {
            if (spot.getRow() == null || spot.getCol() == null) {
                continue;
            }
            if (spot.getStatus() == SpotStatus.MAINTENANCE) {
                continue;
            }

            var spotCode = toSpotId(spot.getRow(), spot.getCol());
            var shouldBeOccupied = occupiedSpotIds.contains(spotCode);
            var shouldBeReserved = reservedSpotIds.contains(spotCode);
            var nextStatus = shouldBeOccupied ? SpotStatus.OCCUPIED : shouldBeReserved ? SpotStatus.RESERVED : SpotStatus.AVAILABLE;
            if (spot.getStatus() != nextStatus) {
                spot.updateStatus(nextStatus);
                spotsChanged = true;
            }
        }

        if (spotsChanged) {
            detectedSpotRepository.saveAll(detectedSpots);
        }

        parkingRepository.findById(parkingId).ifPresent(parking -> {
            int availableSpaces = computeAvailableSpaces(parkingId, detectedSpots);
            parking.updateStats(null, availableSpaces, null, null, null, null);
            parkingRepository.save(parking);
        });
    }

    private int computeAvailableSpaces(String parkingId,
                                       java.util.List<DetectedSpot> detectedSpots) {
        long freeDetected = detectedSpots.stream()
                .filter(spot -> spot.getStatus() != SpotStatus.MAINTENANCE)
                .filter(spot -> spot.getStatus() == SpotStatus.AVAILABLE)
                .count();
        if (!detectedSpots.isEmpty()) {
            return (int) freeDetected;
        }

        return parkingRepository.findById(parkingId)
                .map(parking -> Math.max(0, parking.getTotalSpaces()))
                .orElse(0);
    }

    private String toSpotId(int row, int col) {
        return "%s%d".formatted(String.valueOf((char) ('A' + row)), col + 1);
    }
}
