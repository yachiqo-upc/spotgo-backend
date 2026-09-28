package com.yachiqo.spotgo.parking.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yachiqo.spotgo.monitoring.application.EmployeeSpotAssignmentService;
import com.yachiqo.spotgo.parking.domain.model.aggregates.DetectedSpot;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Parking;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Reservation;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationStatus;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.SpotStatus;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.DetectedSpotRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ParkingRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingOccupancyServiceTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private DetectedSpotRepository detectedSpotRepository;
    @Mock
    private ParkingRepository parkingRepository;
    @Mock
    private EmployeeSpotAssignmentService employeeSpotAssignmentService;

    private ParkingOccupancyService service;

    @BeforeEach
    void setUp() {
        service = new ParkingOccupancyService(reservationRepository, detectedSpotRepository, parkingRepository, employeeSpotAssignmentService);
    }

    @Test
    void reconcileParkingUpdatesSpotStatusesAndAvailableSpaces() {
        var active = new Reservation("client-1", "parking-1", "SPG-001", "A1",
                LocalDateTime.of(2026, 7, 8, 10, 0), LocalDateTime.of(2026, 7, 8, 13, 0), 10.0, 10.0, null);
        var spotA1 = new DetectedSpot(1, "bp-1", "parking-1", 0, 0, 0.1, 0.1, 0.1, 0.1, SpotStatus.AVAILABLE);
        var spotA2 = new DetectedSpot(2, "bp-1", "parking-1", 0, 1, 0.1, 0.1, 0.1, 0.1, SpotStatus.OCCUPIED);
        var parking = new Parking("admin-1", "Main", "Addr", "Lima", 2, 2, 1, null, null, null, null, null, 0.0, 5.0, null, null, null);

        when(reservationRepository.findByParkingIdAndStatus("parking-1", ReservationStatus.ACTIVE)).thenReturn(List.of(active));
        when(detectedSpotRepository.findByParkingId("parking-1")).thenReturn(List.of(spotA1, spotA2));
        when(parkingRepository.findById("parking-1")).thenReturn(Optional.of(parking));
        when(employeeSpotAssignmentService.getReservedSpotCodes("parking-1", LocalDateTime.of(2026, 7, 8, 12, 0))).thenReturn(java.util.Set.of());

        service.reconcileParking("parking-1", LocalDateTime.of(2026, 7, 8, 12, 0));

        assertEquals(SpotStatus.OCCUPIED, spotA1.getStatus());
        assertEquals(SpotStatus.AVAILABLE, spotA2.getStatus());
        assertEquals(1, parking.getAvailableSpaces());
        verify(detectedSpotRepository).saveAll(List.of(spotA1, spotA2));
        verify(parkingRepository).save(parking);
    }

    @Test
    void reconcileParkingMarksAssignedOnDutySpotAsReserved() {
        var spotA1 = new DetectedSpot(1, "bp-1", "parking-1", 0, 0, 0.1, 0.1, 0.1, 0.1, SpotStatus.AVAILABLE);
        var spotA2 = new DetectedSpot(2, "bp-1", "parking-1", 0, 1, 0.1, 0.1, 0.1, 0.1, SpotStatus.AVAILABLE);
        var parking = new Parking("admin-1", "Main", "Addr", "Lima", 2, 2, 1, null, null, null, null, null, 0.0, 5.0, null, null, null);

        when(reservationRepository.findByParkingIdAndStatus("parking-1", ReservationStatus.ACTIVE)).thenReturn(List.of());
        when(detectedSpotRepository.findByParkingId("parking-1")).thenReturn(List.of(spotA1, spotA2));
        when(parkingRepository.findById("parking-1")).thenReturn(Optional.of(parking));
        when(employeeSpotAssignmentService.getReservedSpotCodes("parking-1", LocalDateTime.of(2026, 7, 8, 12, 0))).thenReturn(java.util.Set.of("A1"));

        service.reconcileParking("parking-1", LocalDateTime.of(2026, 7, 8, 12, 0));

        assertEquals(SpotStatus.RESERVED, spotA1.getStatus());
        assertEquals(SpotStatus.AVAILABLE, spotA2.getStatus());
        assertEquals(1, parking.getAvailableSpaces());
    }
}
