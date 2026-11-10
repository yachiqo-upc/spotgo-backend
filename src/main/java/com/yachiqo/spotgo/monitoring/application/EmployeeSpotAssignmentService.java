package com.yachiqo.spotgo.monitoring.application;

import com.yachiqo.spotgo.monitoring.domain.model.aggregates.Employee;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.EmployeeSchedule;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.EmployeeStatus;
import com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationStatus;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.OperationalStatus;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ParkingSpotRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class EmployeeSpotAssignmentService {

    private final EmployeeRepository employeeRepository;
    private final ParkingSpotRepository detectedSpotRepository;
    private final ReservationRepository reservationRepository;

    public EmployeeSpotAssignmentService(EmployeeRepository employeeRepository,
                                         ParkingSpotRepository detectedSpotRepository,
                                         ReservationRepository reservationRepository) {
        this.employeeRepository = employeeRepository;
        this.detectedSpotRepository = detectedSpotRepository;
        this.reservationRepository = reservationRepository;
    }

    public void validateAssignment(String parkingId,
                                   String employeeIdToIgnore,
                                   String assignedSpot,
                                   EmployeeSchedule schedule,
                                   String shiftStart,
                                   String shiftEnd,
                                   EmployeeStatus status) {
        var normalizedSpot = normalizeSpot(assignedSpot);
        if (normalizedSpot == null) {
            return;
        }

        var detectedSpot = detectedSpotRepository.byTenant(java.util.UUID.fromString(parkingId)).stream()
                .filter(spot -> normalizedSpot.equals(spot.getCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Assigned spot does not exist in admin parking"));

        if (detectedSpot.getOperationalStatus() == OperationalStatus.MAINTENANCE) {
            throw new IllegalArgumentException("Assigned spot is under maintenance");
        }

        var currentOwner = employeeRepository.findByParkingIdAndAssignedSpot(parkingId, normalizedSpot);
        if (currentOwner.isPresent() && !Objects.equals(currentOwner.get().getId(), employeeIdToIgnore)) {
            throw new IllegalArgumentException("Spot is already assigned to another employee");
        }

        if (status != EmployeeStatus.ON_DUTY) {
            return;
        }

        var conflictingReservation = reservationRepository.byTenant(java.util.UUID.fromString(parkingId)).stream().filter(r -> normalizedSpot.equals(r.getSpot().getCode()))
                .filter(reservation -> (reservation.getStatus() == ReservationStatus.ACTIVE || reservation.getStatus() == ReservationStatus.RESERVED))
                .filter(reservation -> scheduleApplies(schedule,
                        java.time.LocalDateTime.ofInstant(reservation.getStartAt(), java.time.ZoneId.of("America/Lima")).toLocalDate(),
                        java.time.LocalDateTime.ofInstant(reservation.getEndAt(), java.time.ZoneId.of("America/Lima")).toLocalDate()))
                .anyMatch(reservation -> overlapsRecurringShift(
                        java.time.LocalDateTime.ofInstant(reservation.getStartAt(), java.time.ZoneId.of("America/Lima")),
                        java.time.LocalDateTime.ofInstant(reservation.getEndAt(), java.time.ZoneId.of("America/Lima")),
                        shiftStart,
                        shiftEnd));
        if (conflictingReservation) {
            throw new IllegalArgumentException("The assigned spot already has a reservation during the employee shift. Assign another spot before setting the employee on-duty.");
        }
    }

    public boolean isSpotReservedForEmployee(String parkingId, String spot, LocalDateTime startDate, LocalDateTime endDate) {
        var normalizedSpot = normalizeSpot(spot);
        if (normalizedSpot == null) {
            return false;
        }
        return employeeRepository.findByParkingId(parkingId).stream()
                .filter(employee -> normalizedSpot.equals(normalizeSpot(employee.getAssignedSpot())))
                .filter(employee -> employee.getStatus() == EmployeeStatus.ON_DUTY)
                .filter(employee -> scheduleApplies(employee.getSchedule(), startDate.toLocalDate(), endDate.toLocalDate()))
                .anyMatch(employee -> overlapsRecurringShift(startDate, endDate, employee.getShiftStart(), employee.getShiftEnd()));
    }

    public Map<String, Employee> getReservedEmployeesBySpot(String parkingId, LocalDateTime now) {
        return employeeRepository.findByParkingId(parkingId).stream()
                .filter(employee -> employee.getStatus() == EmployeeStatus.ON_DUTY)
                .filter(employee -> employee.getAssignedSpot() != null && !employee.getAssignedSpot().isBlank())
                .filter(employee -> scheduleApplies(employee.getSchedule(), now.toLocalDate(), now.toLocalDate()))
                .filter(employee -> overlapsRecurringShift(now, now.plusNanos(1), employee.getShiftStart(), employee.getShiftEnd()))
                .collect(Collectors.toMap(
                        employee -> normalizeSpot(employee.getAssignedSpot()),
                        employee -> employee,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    public Set<String> getReservedSpotCodes(String parkingId, LocalDateTime now) {
        return getReservedEmployeesBySpot(parkingId, now).keySet();
    }

    public String normalizeSpot(String assignedSpot) {
        if (assignedSpot == null || assignedSpot.isBlank()) {
            return null;
        }
        return assignedSpot.trim().toUpperCase();
    }

    private boolean overlapsRecurringShift(LocalDateTime rangeStart,
                                           LocalDateTime rangeEnd,
                                           String shiftStart,
                                           String shiftEnd) {
        if (rangeStart == null || rangeEnd == null || !rangeEnd.isAfter(rangeStart)) {
            return false;
        }

        var shiftStartTime = parseTime(shiftStart);
        var shiftEndTime = parseTime(shiftEnd);
        for (var date = rangeStart.toLocalDate().minusDays(1);
             !date.isAfter(rangeEnd.toLocalDate());
             date = date.plusDays(1)) {
            var windowStart = date.atTime(shiftStartTime);
            var windowEnd = shiftEndTime.isAfter(shiftStartTime)
                    ? date.atTime(shiftEndTime)
                    : date.plusDays(1).atTime(shiftEndTime);
            if (rangeStart.isBefore(windowEnd) && rangeEnd.isAfter(windowStart)) {
                return true;
            }
        }
        return false;
    }

    private boolean scheduleApplies(EmployeeSchedule schedule, LocalDate startDate, LocalDate endDate) {
        for (var date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (scheduleIncludes(schedule, date)) {
                return true;
            }
        }
        return false;
    }

    private boolean scheduleIncludes(EmployeeSchedule schedule, LocalDate date) {
        return switch (schedule) {
            case ALL_WEEK -> true;
            case WEEKDAYS -> switch (date.getDayOfWeek()) {
                case SATURDAY, SUNDAY -> false;
                default -> true;
            };
            case WEEKENDS -> switch (date.getDayOfWeek()) {
                case SATURDAY, SUNDAY -> true;
                default -> false;
            };
        };
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid employee shift time: " + value, exception);
        }
    }

    private String toSpotCode(Integer row, Integer col) {
        if (row == null || col == null) {
            return null;
        }
        return "%s%d".formatted(String.valueOf((char) ('A' + row)), col + 1);
    }
}
