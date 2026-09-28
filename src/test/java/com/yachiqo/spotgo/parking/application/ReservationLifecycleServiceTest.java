package com.yachiqo.spotgo.parking.application;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yachiqo.spotgo.parking.domain.model.aggregates.Parking;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Reservation;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationStatus;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ParkingRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationLifecycleServiceTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ParkingOccupancyService parkingOccupancyService;
    @Mock
    private ParkingRepository parkingRepository;

    private ReservationLifecycleService service;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(Instant.parse("2026-07-08T12:00:00Z"), ZoneOffset.UTC);
        service = new ReservationLifecycleService(reservationRepository, parkingOccupancyService, parkingRepository, clock);
    }

    @Test
    void reconcileExpiredReservationsCompletesExpiredReservationsAndReconcilesParking() {
        var expired = new Reservation("client-1", "parking-1", "SPG-001", "A1",
                LocalDateTime.of(2026, 7, 8, 10, 0), LocalDateTime.of(2026, 7, 8, 11, 0), 10.0, 10.0, null);
        var parking = new Parking("admin-1", "Main", "Addr", "Lima", 2, 2, 1, null, null, null, null, null, 0.0, 5.0, null, null, null);
        parking.setId("parking-1");

        when(reservationRepository.findByStatusAndEndDateBefore(ReservationStatus.ACTIVE, LocalDateTime.of(2026, 7, 8, 12, 0)))
                .thenReturn(List.of(expired));
        when(parkingRepository.findAll()).thenReturn(List.of(parking));

        service.reconcileExpiredReservations();

        verify(reservationRepository).saveAll(List.of(expired));
        verify(parkingOccupancyService).reconcileParking("parking-1", LocalDateTime.of(2026, 7, 8, 12, 0));
    }

    @Test
    void reconcileExpiredReservationsStillReconcilesParkingsWhenNothingExpired() {
        var parking = new Parking("admin-1", "Main", "Addr", "Lima", 2, 2, 1, null, null, null, null, null, 0.0, 5.0, null, null, null);
        parking.setId("parking-1");
        when(reservationRepository.findByStatusAndEndDateBefore(ReservationStatus.ACTIVE, LocalDateTime.of(2026, 7, 8, 12, 0))).thenReturn(List.of());
        when(parkingRepository.findAll()).thenReturn(List.of(parking));

        service.reconcileExpiredReservations();

        verify(reservationRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
        verify(parkingOccupancyService).reconcileParking("parking-1", LocalDateTime.of(2026, 7, 8, 12, 0));
    }
}
