package com.yachiqo.spotgo.parking.application.internal.commandservices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yachiqo.spotgo.billing.domain.repositories.ClientPlanRepository;
import com.yachiqo.spotgo.billing.domain.repositories.ReceiptRepository;
import com.yachiqo.spotgo.billing.domain.repositories.SubscriptionRepository;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.UserAccountRepository;
import com.yachiqo.spotgo.monitoring.application.EmployeeSpotAssignmentService;
import com.yachiqo.spotgo.parking.application.ParkingOccupancyService;
import com.yachiqo.spotgo.parking.domain.model.aggregates.DetectedSpot;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Parking;
import com.yachiqo.spotgo.parking.domain.model.aggregates.ClientReport;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Reservation;
import com.yachiqo.spotgo.parking.domain.model.commands.CreateClientReportCommand;
import com.yachiqo.spotgo.parking.domain.model.commands.ReserveSpotCommand;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReportType;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.SpotStatus;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.BlueprintRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ClientReportRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.DetectedSpotRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ParkingRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingCommandServiceImplTest {

    @Mock
    private ParkingRepository parkingRepository;
    @Mock
    private BlueprintRepository blueprintRepository;
    @Mock
    private DetectedSpotRepository detectedSpotRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ClientReportRepository clientReportRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private ParkingOccupancyService parkingOccupancyService;
    @Mock
    private EmployeeSpotAssignmentService employeeSpotAssignmentService;
    @Mock
    private ReceiptRepository receiptRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private ClientPlanRepository clientPlanRepository;

    private final Clock clock = Clock.systemUTC();

    private ParkingCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ParkingCommandServiceImpl(
                parkingRepository,
                blueprintRepository,
                detectedSpotRepository,
                reservationRepository,
                clientReportRepository,
                userAccountRepository,
                parkingOccupancyService,
                employeeSpotAssignmentService,
                receiptRepository,
                subscriptionRepository,
                clientPlanRepository,
                clock);
    }

    @Test
    void reserveSpotRejectsConflictingReservation() {
        var existing = new Reservation(
                "client-2",
                "parking-1",
                "SPG-OLD",
                "B5",
                LocalDateTime.of(2026, 7, 7, 10, 0),
                LocalDateTime.of(2026, 7, 7, 12, 0),
                10.0,
                10.0,
                null);
        when(userAccountRepository.existsById("client-1")).thenReturn(true);
        when(parkingRepository.existsById("parking-1")).thenReturn(true);
        var parking = mock(Parking.class);
        when(parkingRepository.findByIdForUpdate("parking-1")).thenReturn(Optional.of(parking));
        when(detectedSpotRepository.findByParkingIdAndRowAndCol("parking-1", 1, 4))
                .thenReturn(List.of(new DetectedSpot(1, "bp-1", "parking-1", 1, 4, 0.1, 0.1, 0.1, 0.1, SpotStatus.AVAILABLE)));
        when(reservationRepository.findByParkingIdAndSpot("parking-1", "B5")).thenReturn(List.of(existing));

        var exception = assertThrows(IllegalArgumentException.class, () -> service.handle(new ReserveSpotCommand(
                "client-1",
                "parking-1",
                "B5",
                LocalDateTime.of(2026, 7, 7, 11, 0),
                LocalDateTime.of(2026, 7, 7, 13, 0),
                10.0,
                10.0,
                null)));

        assertEquals("Spot is not available for the selected time range", exception.getMessage());
    }

    @Test
    void createClientReportGeneratesUniqueNonSequentialCode() {
        var reservation = new Reservation(
                "client-1",
                "parking-1",
                "SPG-001",
                "B5",
                LocalDateTime.of(2026, 7, 7, 10, 0),
                LocalDateTime.of(2026, 7, 7, 12, 0),
                10.0,
                10.0,
                null);
        when(reservationRepository.findById("reservation-1")).thenReturn(Optional.of(reservation));
        when(clientReportRepository.existsByCode(any())).thenReturn(false);
        when(clientReportRepository.save(any(ClientReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var report = service.handle(new CreateClientReportCommand(
                "client-1",
                "parking-1",
                "reservation-1",
                ReportType.OTHER,
                "2026-07-07T13:00:00Z")).orElseThrow();

        assertEquals(true, report.getCode().startsWith("RPT-"));
        assertEquals(14, report.getCode().length());
    }

    @Test
    void reserveSpotReconcilesParkingOccupancy() {
        var start = LocalDateTime.now().minusMinutes(5);
        var end = LocalDateTime.now().plusMinutes(55);
        when(userAccountRepository.existsById("client-1")).thenReturn(true);
        when(parkingRepository.existsById("parking-1")).thenReturn(true);
        var parking = mock(Parking.class);
        when(parking.getPricePerHour()).thenReturn(10.0);
        when(parking.getName()).thenReturn("SpotGo Parking");
        when(parkingRepository.findByIdForUpdate("parking-1")).thenReturn(Optional.of(parking));
        when(detectedSpotRepository.findByParkingIdAndRowAndCol("parking-1", 1, 4))
                .thenReturn(List.of(new DetectedSpot(1, "bp-1", "parking-1", 1, 4, 0.1, 0.1, 0.1, 0.1, SpotStatus.AVAILABLE)));
        when(reservationRepository.findByParkingIdAndSpot("parking-1", "B5")).thenReturn(List.of());
        when(employeeSpotAssignmentService.isSpotReservedForEmployee("parking-1", "B5", start, end)).thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            var r = invocation.getArgument(0, Reservation.class);
            if (r.getId() == null) r.setId(java.util.UUID.randomUUID().toString());
            return r;
        });
        when(receiptRepository.findAllByReservationId(any())).thenReturn(List.of());

        service.handle(new ReserveSpotCommand(
                "client-1",
                "parking-1",
                "B5",
                start,
                end,
                10.0,
                10.0,
                null));

        verify(parkingOccupancyService).reconcileParking(any(), any());
    }

    @Test
    void reserveSpotRejectsEmployeeReservedSpot() {
        var start = LocalDateTime.of(2026, 7, 7, 11, 0);
        var end = LocalDateTime.of(2026, 7, 7, 13, 0);
        when(userAccountRepository.existsById("client-1")).thenReturn(true);
        when(parkingRepository.existsById("parking-1")).thenReturn(true);
        var parking = mock(Parking.class);
        when(parkingRepository.findByIdForUpdate("parking-1")).thenReturn(Optional.of(parking));
        when(detectedSpotRepository.findByParkingIdAndRowAndCol("parking-1", 1, 4))
                .thenReturn(List.of(new DetectedSpot(1, "bp-1", "parking-1", 1, 4, 0.1, 0.1, 0.1, 0.1, SpotStatus.AVAILABLE)));
        when(reservationRepository.findByParkingIdAndSpot("parking-1", "B5")).thenReturn(List.of());
        when(employeeSpotAssignmentService.isSpotReservedForEmployee("parking-1", "B5", start, end)).thenReturn(true);

        var exception = assertThrows(IllegalArgumentException.class, () -> service.handle(new ReserveSpotCommand(
                "client-1",
                "parking-1",
                "B5",
                start,
                end,
                10.0,
                10.0,
                null)));

        assertEquals("Spot is reserved for an on-duty employee during the selected time range", exception.getMessage());
    }
}
