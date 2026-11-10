package com.yachiqo.spotgo.parking.interfaces.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.parking.application.internal.commandservices.ParkingCommandService;
import com.yachiqo.spotgo.parking.application.internal.queryservices.ParkingQueryService;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Reservation;
import com.yachiqo.spotgo.parking.domain.model.commands.ReserveSpotCommand;
import com.yachiqo.spotgo.parking.domain.model.queries.GetReservationsByParkingIdQuery;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationRepository;
import com.yachiqo.spotgo.parking.interfaces.rest.resources.CreateReservationResource;
import com.yachiqo.spotgo.shared.application.security.AuthorizationService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ReservationsControllerTest {

    @Mock
    private ParkingCommandService parkingCommandService;
    @Mock
    private ParkingQueryService parkingQueryService;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private AuthorizationService authorizationService;

    private ReservationsController controller;

    @BeforeEach
    void setUp() {
        controller = new ReservationsController(parkingCommandService, parkingQueryService, reservationRepository, authorizationService);
    }

    @Test
    void reserveSpotUsesAuthenticatedClientIdInsteadOfRequestBodyClientId() {
        var principal = new SpotgoUserPrincipal(java.util.UUID.fromString("00000000-0000-4000-8000-000000000902"), "client@spotgo.com", UserRole.DRIVER, null, null);
        var resource = new CreateReservationResource(
                "parking-1",
                "B5",
                OffsetDateTime.parse("2026-07-07T10:00:00.000-05:00"),
                OffsetDateTime.parse("2026-07-07T11:00:00.000-05:00"));
        when(parkingCommandService.handle(any(ReserveSpotCommand.class))).thenReturn(Optional.of(new Reservation(
                "00000000-0000-4000-8000-000000000902",
                "parking-1",
                "SPG-001",
                "B5",
                LocalDateTime.of(2026, 7, 7, 10, 0),
                LocalDateTime.of(2026, 7, 7, 11, 0),
                10.0,
                10.0,
                null)));

        controller.reserveSpot(principal, resource);

        var commandCaptor = ArgumentCaptor.forClass(ReserveSpotCommand.class);
        verify(parkingCommandService).handle(commandCaptor.capture());
        assertEquals("00000000-0000-4000-8000-000000000902", commandCaptor.getValue().clientId());
    }

    @Test
    void reserveSpotConvertsOffsetDateTimesToLimaLocalTime() {
        var principal = new SpotgoUserPrincipal(java.util.UUID.fromString("00000000-0000-4000-8000-000000000902"), "client@spotgo.com", UserRole.DRIVER, null, null);
        var resource = new CreateReservationResource(
                "parking-1",
                "A1",
                OffsetDateTime.parse("2026-07-07T12:00:00.000-05:00"),
                OffsetDateTime.parse("2026-07-07T13:30:00.000-05:00"));
        when(parkingCommandService.handle(any(ReserveSpotCommand.class))).thenReturn(Optional.of(new Reservation(
                "00000000-0000-4000-8000-000000000902",
                "parking-1",
                "SPG-001",
                "A1",
                LocalDateTime.of(2026, 7, 7, 12, 0),
                LocalDateTime.of(2026, 7, 7, 13, 30),
                10.0,
                10.0,
                null)));

        controller.reserveSpot(principal, resource);

        var commandCaptor = ArgumentCaptor.forClass(ReserveSpotCommand.class);
        verify(parkingCommandService).handle(commandCaptor.capture());
        assertEquals(LocalDateTime.of(2026, 7, 7, 12, 0), commandCaptor.getValue().startDate());
        assertEquals(LocalDateTime.of(2026, 7, 7, 13, 30), commandCaptor.getValue().endDate());
    }

    @Test
    void getAllReservationsRejectsAdminRequestForForeignParking() {
        var principal = new SpotgoUserPrincipal(java.util.UUID.fromString("00000000-0000-4000-8000-000000000901"), "admin@spotgo.com", UserRole.PARKING_ADMIN, null, null);
        when(authorizationService.isAdmin(principal)).thenReturn(true);
        when(authorizationService.requireAdminParkingId(principal)).thenReturn("parking-owned");

        assertThrows(AccessDeniedException.class,
                () -> controller.getAllReservations(principal, "parking-other", null));
    }

    @Test
    void getAllReservationsScopesAdminToOwnedParking() {
        var principal = new SpotgoUserPrincipal(java.util.UUID.fromString("00000000-0000-4000-8000-000000000901"), "admin@spotgo.com", UserRole.PARKING_ADMIN, null, null);
        when(authorizationService.isAdmin(principal)).thenReturn(true);
        when(authorizationService.requireAdminParkingId(principal)).thenReturn("parking-owned");
        when(parkingQueryService.handle(any(GetReservationsByParkingIdQuery.class))).thenReturn(List.of());

        controller.getAllReservations(principal, null, null);

        verify(parkingQueryService).handle(new GetReservationsByParkingIdQuery("parking-owned"));
    }
}
