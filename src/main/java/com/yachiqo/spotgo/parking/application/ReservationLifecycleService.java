package com.yachiqo.spotgo.parking.application;

import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationStatus;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ParkingRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationLifecycleService {

    private final ReservationRepository reservationRepository;
    private final ParkingOccupancyService parkingOccupancyService;
    private final ParkingRepository parkingRepository;
    private final Clock clock;

    public ReservationLifecycleService(ReservationRepository reservationRepository,
                                       ParkingOccupancyService parkingOccupancyService,
                                       ParkingRepository parkingRepository,
                                       Clock clock) {
        this.reservationRepository = reservationRepository;
        this.parkingOccupancyService = parkingOccupancyService;
        this.parkingRepository = parkingRepository;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reconcileOnStartup() {
        reconcileExpiredReservations();
    }

    @Scheduled(fixedDelayString = "${app.reservations.lifecycle.fixed-delay-ms:60000}")
    @Transactional
    public void reconcileExpiredReservations() {
        var now = LocalDateTime.now(clock);
        var expiredReservations = reservationRepository.findByStatusAndEndDateBefore(ReservationStatus.ACTIVE, now);
        var affectedParkingIds = new java.util.HashSet<String>();
        for (var reservation : expiredReservations) {
            reservation.updateStatus(ReservationStatus.COMPLETED);
            affectedParkingIds.add(reservation.getParkingId());
        }
        if (!expiredReservations.isEmpty()) {
            reservationRepository.saveAll(expiredReservations);
        }

        parkingRepository.findAll().forEach(parking -> affectedParkingIds.add(parking.getId()));

        for (var parkingId : affectedParkingIds) {
            parkingOccupancyService.reconcileParking(parkingId, now);
        }
    }
}
