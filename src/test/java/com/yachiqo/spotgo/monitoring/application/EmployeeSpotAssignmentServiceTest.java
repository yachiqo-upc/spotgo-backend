package com.yachiqo.spotgo.monitoring.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.yachiqo.spotgo.monitoring.domain.model.aggregates.Employee;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.EmployeeRole;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.EmployeeSchedule;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.EmployeeStatus;
import com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import com.yachiqo.spotgo.parking.domain.model.aggregates.ParkingSpot;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Reservation;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.OperationalStatus;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationStatus;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ParkingSpotRepository;
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
class EmployeeSpotAssignmentServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private ParkingSpotRepository detectedSpotRepository;
    @Mock
    private ReservationRepository reservationRepository;

    private EmployeeSpotAssignmentService service;

    private ParkingSpot spot() {
        var tenant=new com.yachiqo.spotgo.parking.domain.model.aggregates.Tenant("Demo","Demo",java.util.Map.of(),java.util.Map.of(),java.util.Map.of());
        var zone=new com.yachiqo.spotgo.parking.domain.model.aggregates.ParkingZone(tenant,"A",java.util.Map.of(),"#2563EB");
        return new ParkingSpot(zone,"A1","STANDARD",java.util.Map.of());
    }
    private Reservation reservation(ParkingSpot spot,LocalDateTime start,LocalDateTime end) {
        var r=new Reservation(spot,null,null,start.atZone(java.time.ZoneId.of("America/Lima")).toInstant(),end.atZone(java.time.ZoneId.of("America/Lima")).toInstant(),java.math.BigDecimal.TEN,java.time.Instant.EPOCH,10);
        org.springframework.test.util.ReflectionTestUtils.setField(r,"status",ReservationStatus.ACTIVE);return r;
    }
    @BeforeEach
    void setUp() {
        service = new EmployeeSpotAssignmentService(employeeRepository, detectedSpotRepository, reservationRepository);
    }

    @Test
    void validateAssignmentRejectsOnDutyEmployeeWhenSpotHasConflictingReservation() {
        var spotA1 = spot();
        var reservation = reservation(spotA1, LocalDateTime.of(2026, 7, 9, 10, 0), LocalDateTime.of(2026, 7, 9, 11, 0));
        when(detectedSpotRepository.byTenant(java.util.UUID.fromString("00000000-0000-4000-8000-000000000021"))).thenReturn(List.of(spotA1));
        when(employeeRepository.findByParkingIdAndAssignedSpot("00000000-0000-4000-8000-000000000021", "A1")).thenReturn(Optional.empty());
        when(reservationRepository.byTenant(java.util.UUID.fromString("00000000-0000-4000-8000-000000000021"))).thenReturn(List.of(reservation));

        var exception = assertThrows(IllegalArgumentException.class, () -> service.validateAssignment(
                "00000000-0000-4000-8000-000000000021",
                null,
                "a1",
                EmployeeSchedule.ALL_WEEK,
                "09:00",
                "17:00",
                EmployeeStatus.ON_DUTY));

        assertEquals("The assigned spot already has a reservation during the employee shift. Assign another spot before setting the employee on-duty.", exception.getMessage());
    }

    @Test
    void validateAssignmentIgnoresCompletedReservation() {
        var spotA1 = spot();
        var reservation = reservation(spotA1, LocalDateTime.of(2026, 7, 9, 10, 0), LocalDateTime.of(2026, 7, 9, 11, 0));
        org.springframework.test.util.ReflectionTestUtils.setField(reservation,"status",ReservationStatus.COMPLETED);
        when(detectedSpotRepository.byTenant(java.util.UUID.fromString("00000000-0000-4000-8000-000000000021"))).thenReturn(List.of(spotA1));
        when(employeeRepository.findByParkingIdAndAssignedSpot("00000000-0000-4000-8000-000000000021", "A1")).thenReturn(Optional.empty());
        when(reservationRepository.byTenant(java.util.UUID.fromString("00000000-0000-4000-8000-000000000021"))).thenReturn(List.of(reservation));

        assertDoesNotThrow(() -> service.validateAssignment(
                "00000000-0000-4000-8000-000000000021", null, "A1", EmployeeSchedule.ALL_WEEK, "09:00", "17:00", EmployeeStatus.ON_DUTY));
    }

    @Test
    void reservedEmployeesBySpotIncludesOnlyOnDutyEmployeesInsideShift() {
        var onDutyEmployee = new Employee(
                "00000000-0000-4000-8000-000000000021",
                "Ana",
                "Diaz",
                EmployeeRole.GUARD,
                EmployeeSchedule.ALL_WEEK,
                "09:00",
                "17:00",
                "A1",
                EmployeeStatus.ON_DUTY);
        var offDutyEmployee = new Employee(
                "00000000-0000-4000-8000-000000000021",
                "Luis",
                "Perez",
                EmployeeRole.GUARD,
                EmployeeSchedule.ALL_WEEK,
                "09:00",
                "17:00",
                "A2",
                EmployeeStatus.OFF_DUTY);
        when(employeeRepository.findByParkingId("00000000-0000-4000-8000-000000000021")).thenReturn(List.of(onDutyEmployee, offDutyEmployee));

        var reservedBySpot = service.getReservedEmployeesBySpot("00000000-0000-4000-8000-000000000021", LocalDateTime.of(2026, 7, 9, 10, 0));

        assertEquals(1, reservedBySpot.size());
        assertTrue(reservedBySpot.containsKey("A1"));
        assertEquals("Ana", reservedBySpot.get("A1").getFirstName());
    }

    @Test
    void reservedEmployeesBySpotSkipsWeekdayEmployeeOnWeekend() {
        var weekdayEmployee = new Employee(
                "00000000-0000-4000-8000-000000000021",
                "Ana",
                "Diaz",
                EmployeeRole.GUARD,
                EmployeeSchedule.WEEKDAYS,
                "09:00",
                "17:00",
                "A1",
                EmployeeStatus.ON_DUTY);
        when(employeeRepository.findByParkingId("00000000-0000-4000-8000-000000000021")).thenReturn(List.of(weekdayEmployee));

        var reservedBySpot = service.getReservedEmployeesBySpot("00000000-0000-4000-8000-000000000021", LocalDateTime.of(2026, 7, 11, 10, 0));

        assertTrue(reservedBySpot.isEmpty());
    }

    @Test
    void reservedEmployeesBySpotIncludesWeekendEmployeeOnWeekend() {
        var weekendEmployee = new Employee(
                "00000000-0000-4000-8000-000000000021",
                "Ana",
                "Diaz",
                EmployeeRole.GUARD,
                EmployeeSchedule.WEEKENDS,
                "09:00",
                "17:00",
                "A1",
                EmployeeStatus.ON_DUTY);
        when(employeeRepository.findByParkingId("00000000-0000-4000-8000-000000000021")).thenReturn(List.of(weekendEmployee));

        var reservedBySpot = service.getReservedEmployeesBySpot("00000000-0000-4000-8000-000000000021", LocalDateTime.of(2026, 7, 11, 10, 0));

        assertEquals(1, reservedBySpot.size());
        assertTrue(reservedBySpot.containsKey("A1"));
    }

    @Test
    void isSpotReservedForEmployeeSkipsWeekendEmployeeOnWeekday() {
        var weekendEmployee = new Employee(
                "00000000-0000-4000-8000-000000000021",
                "Ana",
                "Diaz",
                EmployeeRole.GUARD,
                EmployeeSchedule.WEEKENDS,
                "09:00",
                "17:00",
                "A1",
                EmployeeStatus.ON_DUTY);
        when(employeeRepository.findByParkingId("00000000-0000-4000-8000-000000000021")).thenReturn(List.of(weekendEmployee));

        var reserved = service.isSpotReservedForEmployee(
                "00000000-0000-4000-8000-000000000021",
                "A1",
                LocalDateTime.of(2026, 7, 10, 10, 0),
                LocalDateTime.of(2026, 7, 10, 11, 0));

        assertEquals(false, reserved);
    }

    @Test
    void validateAssignmentIgnoresReservationOutsideWeekendSchedule() {
        var spotA1 = spot();
        var fridayReservation = reservation(spotA1, LocalDateTime.of(2026, 7, 10, 10, 0), LocalDateTime.of(2026, 7, 10, 11, 0));
        when(detectedSpotRepository.byTenant(java.util.UUID.fromString("00000000-0000-4000-8000-000000000021"))).thenReturn(List.of(spotA1));
        when(employeeRepository.findByParkingIdAndAssignedSpot("00000000-0000-4000-8000-000000000021", "A1")).thenReturn(Optional.empty());
        when(reservationRepository.byTenant(java.util.UUID.fromString("00000000-0000-4000-8000-000000000021"))).thenReturn(List.of(fridayReservation));

        assertDoesNotThrow(() -> service.validateAssignment(
                "00000000-0000-4000-8000-000000000021", null, "A1", EmployeeSchedule.WEEKENDS, "09:00", "17:00", EmployeeStatus.ON_DUTY));
    }
}
