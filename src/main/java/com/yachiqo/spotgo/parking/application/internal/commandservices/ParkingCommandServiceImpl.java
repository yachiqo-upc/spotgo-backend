package com.yachiqo.spotgo.parking.application.internal.commandservices;

import com.yachiqo.spotgo.billing.domain.model.aggregates.Receipt;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.ReceiptStatus;
import com.yachiqo.spotgo.billing.domain.repositories.ClientPlanRepository;
import com.yachiqo.spotgo.billing.domain.repositories.ReceiptRepository;
import com.yachiqo.spotgo.billing.domain.repositories.SubscriptionRepository;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.UserAccountRepository;
import com.yachiqo.spotgo.monitoring.application.EmployeeSpotAssignmentService;
import com.yachiqo.spotgo.parking.application.ParkingOccupancyService;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Blueprint;
import com.yachiqo.spotgo.parking.domain.model.aggregates.ClientReport;
import com.yachiqo.spotgo.parking.domain.model.aggregates.DetectedSpot;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Parking;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Reservation;
import com.yachiqo.spotgo.parking.domain.model.commands.CreateBlueprintCommand;
import com.yachiqo.spotgo.parking.domain.model.commands.CreateClientReportCommand;
import com.yachiqo.spotgo.parking.domain.model.commands.CreateDetectedSpotCommand;
import com.yachiqo.spotgo.parking.domain.model.commands.CreateParkingCommand;
import com.yachiqo.spotgo.parking.domain.model.commands.DeleteBlueprintCommand;
import com.yachiqo.spotgo.parking.domain.model.commands.ReserveSpotCommand;
import com.yachiqo.spotgo.parking.domain.model.commands.UpdateParkingRatingCommand;
import com.yachiqo.spotgo.parking.domain.model.commands.UpdateParkingCommand;
import com.yachiqo.spotgo.parking.domain.model.commands.UpdateBlueprintCommand;
import com.yachiqo.spotgo.parking.domain.model.commands.UpdateClientReportStatusCommand;
import com.yachiqo.spotgo.parking.domain.model.commands.UpdateReservationCommand;
import com.yachiqo.spotgo.parking.domain.model.commands.UpdateSpotStatusCommand;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationStatus;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.SpotStatus;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.DetectedSpotRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.BlueprintRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ClientReportRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ParkingRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ParkingCommandServiceImpl implements ParkingCommandService {

    private final ParkingRepository parkingRepository;
    private final BlueprintRepository blueprintRepository;
    private final DetectedSpotRepository detectedSpotRepository;
    private final ReservationRepository reservationRepository;
    private final ClientReportRepository clientReportRepository;
    private final UserAccountRepository userAccountRepository;
    private final ParkingOccupancyService parkingOccupancyService;
    private final EmployeeSpotAssignmentService employeeSpotAssignmentService;
    private final ReceiptRepository receiptRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ClientPlanRepository clientPlanRepository;
    private final Clock clock;

    public ParkingCommandServiceImpl(ParkingRepository parkingRepository, BlueprintRepository blueprintRepository, DetectedSpotRepository detectedSpotRepository, ReservationRepository reservationRepository, ClientReportRepository clientReportRepository, UserAccountRepository userAccountRepository, ParkingOccupancyService parkingOccupancyService, EmployeeSpotAssignmentService employeeSpotAssignmentService, ReceiptRepository receiptRepository, SubscriptionRepository subscriptionRepository, ClientPlanRepository clientPlanRepository, Clock clock) {
        this.parkingRepository = parkingRepository;
        this.blueprintRepository = blueprintRepository;
        this.detectedSpotRepository = detectedSpotRepository;
        this.reservationRepository = reservationRepository;
        this.clientReportRepository = clientReportRepository;
        this.userAccountRepository = userAccountRepository;
        this.parkingOccupancyService = parkingOccupancyService;
        this.employeeSpotAssignmentService = employeeSpotAssignmentService;
        this.receiptRepository = receiptRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.clientPlanRepository = clientPlanRepository;
        this.clock = clock;
    }

    @Override
    public Optional<Parking> handle(CreateParkingCommand command) {
        var parking = new Parking(
                command.adminId(), command.name(), command.address(), command.city(),
                command.totalSpaces(), command.availableSpaces(), command.totalFloors(),
                command.averageOccupancy(), command.occupancyTrendPercent(), command.peakHour(),
                command.totalRevenue(), command.systemStatus(), command.rating(), command.pricePerHour(),
                command.revenueTrendPercent(), command.totalCapacity(), command.efficiencyIndex());
        return Optional.of(parkingRepository.save(parking));
    }

    @Override
    public Optional<Blueprint> handle(CreateBlueprintCommand command) {
        if (!parkingRepository.existsById(command.parkingId())) {
            throw new IllegalArgumentException("Parking does not exist");
        }
        var blueprint = new Blueprint(command.adminId(), command.parkingId(), command.name(), command.dataUrl());
        return Optional.of(blueprintRepository.save(blueprint));
    }

    @Override
    public Optional<Blueprint> handle(UpdateBlueprintCommand command) {
        var blueprintOpt = blueprintRepository.findById(command.blueprintId());
        if (blueprintOpt.isEmpty()) {
            return Optional.empty();
        }
        var blueprint = blueprintOpt.get();
        blueprint = new Blueprint(blueprint.getAdminId(), blueprint.getParkingId(), command.name(), command.dataUrl());
        blueprint.setId(command.blueprintId());
        return Optional.of(blueprintRepository.save(blueprint));
    }

    @Override
    public Optional<DetectedSpot> handle(UpdateSpotStatusCommand command) {
        var spot = detectedSpotRepository.findById(command.spotId());
        if (spot.isEmpty()) return Optional.empty();
        var detectedSpot = spot.get();
        detectedSpot.updateStatus(command.status());
        var savedSpot = detectedSpotRepository.save(detectedSpot);
        parkingOccupancyService.reconcileParking(savedSpot.getParkingId(), LocalDateTime.now(clock));
        return Optional.of(savedSpot);
    }

    @Override
    public Optional<Reservation> handle(ReserveSpotCommand command) {
        var parking = parkingRepository.findByIdForUpdate(command.parkingId())
                .orElseThrow(() -> new IllegalArgumentException("Parking does not exist"));
        var normalizedSpot = normalizeSpot(command.spot());
        validateReservation(command.clientId(), command.parkingId(), normalizedSpot, command.startDate(), command.endDate(), null);
        double baseAmount = calculateBaseAmount(parking.getPricePerHour(), command.startDate(), command.endDate());
        double discountPercent = resolveDiscountPercent(command.clientId());
        double amount = applyDiscount(baseAmount, discountPercent);
        var reservation = new Reservation(
                command.clientId(), command.parkingId(), generateReservationCode(), normalizedSpot,
                command.startDate(), command.endDate(),
                amount, baseAmount, command.rating());
        var savedReservation = reservationRepository.save(reservation);
        createReceiptForReservation(savedReservation, parking.getName());
        registerReservationSavings(command.clientId(), discountPercent, baseAmount, amount);
        parkingOccupancyService.reconcileParking(command.parkingId(), LocalDateTime.now(clock));
        return Optional.of(savedReservation);
    }

    @Override
    public Optional<Parking> handle(UpdateParkingRatingCommand command) {
        var parkingOpt = parkingRepository.findById(command.parkingId());
        if (parkingOpt.isEmpty()) {
            return Optional.empty();
        }
        var parking = parkingOpt.get();
        parking.updateRating(command.rating());
        return Optional.of(parkingRepository.save(parking));
    }

    @Override
    public Optional<DetectedSpot> handle(CreateDetectedSpotCommand command) {
        var blueprint = blueprintRepository.findById(command.blueprintId())
                .orElseThrow(() -> new IllegalArgumentException("Blueprint does not exist"));
        if (!blueprint.getParkingId().equals(command.parkingId())) {
            throw new IllegalArgumentException("Blueprint does not belong to the provided parking");
        }
        var spot = new DetectedSpot(
                command.localId(), command.blueprintId(), command.parkingId(),
                command.row(), command.col(),
                command.xPct(), command.yPct(), command.wPct(), command.hPct(),
                command.status());
        return Optional.of(detectedSpotRepository.save(spot));
    }

    @Override
    public void handle(DeleteBlueprintCommand command) {
        if (!detectedSpotRepository.findByBlueprintId(command.blueprintId()).isEmpty()) {
            throw new IllegalStateException("Cannot delete blueprint with detected spots");
        }
        blueprintRepository.deleteById(command.blueprintId());
    }

    @Override
    public Optional<ClientReport> handle(CreateClientReportCommand command) {
        var reservation = reservationRepository.findById(command.reservationId())
                .orElseThrow(() -> new IllegalArgumentException("Reservation does not exist"));
        if (!reservation.getClientId().equals(command.clientId()) || !reservation.getParkingId().equals(command.parkingId())) {
            throw new IllegalArgumentException("Report does not match reservation ownership");
        }
        var report = new ClientReport(
                command.clientId(), command.parkingId(), command.reservationId(),
                generateReportCode(), command.type(), parseDateTime(command.date()));
        return Optional.of(clientReportRepository.save(report));
    }

    @Override
    public Optional<ClientReport> handle(UpdateClientReportStatusCommand command) {
        var reportOpt = clientReportRepository.findById(command.reportId());
        if (reportOpt.isEmpty()) return Optional.empty();
        var report = reportOpt.get();
        report.updateStatus(command.status());
        return Optional.of(clientReportRepository.save(report));
    }

    @Override
    public Optional<Parking> handle(UpdateParkingCommand command) {
        var parkingOpt = parkingRepository.findById(command.parkingId());
        if (parkingOpt.isEmpty()) {
            return Optional.empty();
        }
        var parking = parkingOpt.get();
        parking.updateStats(command.totalSpaces(), command.availableSpaces(), command.totalFloors(), command.city(), command.rating(), command.pricePerHour());
        return Optional.of(parkingRepository.save(parking));
    }

    @Override
    public Optional<Reservation> handle(UpdateReservationCommand command) {
        var reservationOpt = reservationRepository.findById(command.reservationId());
        if (reservationOpt.isEmpty()) {
            return Optional.empty();
        }
        var reservation = reservationOpt.get();
        parkingRepository.findByIdForUpdate(reservation.getParkingId())
                .orElseThrow(() -> new IllegalArgumentException("Parking does not exist"));
        validateReservation(
                reservation.getClientId(),
                reservation.getParkingId(),
                reservation.getSpot(),
                reservation.getStartDate(),
                command.endDate() == null ? reservation.getEndDate() : command.endDate(),
                reservation.getId());
        reservation.updateDetails(command.endDate(), command.amount(), command.baseAmount(), command.rating(), command.status());
        if (command.endDate() != null) {
            var parking = parkingRepository.findById(reservation.getParkingId())
                    .orElseThrow(() -> new IllegalArgumentException("Parking does not exist"));
            double recalculatedBase = calculateBaseAmount(parking.getPricePerHour(), reservation.getStartDate(), reservation.getEndDate());
            double amount = command.amount() != null ? command.amount() : applyDiscount(recalculatedBase, resolveDiscountPercent(reservation.getClientId()));
            double baseAmount = command.baseAmount() != null ? command.baseAmount() : recalculatedBase;
            reservation.updatePricing(amount, baseAmount);
        }
        var savedReservation = reservationRepository.save(reservation);
        parkingOccupancyService.reconcileParking(reservation.getParkingId(), LocalDateTime.now(clock));
        return Optional.of(savedReservation);
    }

    private void validateReservation(String clientId, String parkingId, String spot, LocalDateTime startDate, LocalDateTime endDate, String reservationIdToIgnore) {
        if (!userAccountRepository.existsById(clientId)) {
            throw new IllegalArgumentException("Client does not exist");
        }
        if (!parkingRepository.existsById(parkingId)) {
            throw new IllegalArgumentException("Parking does not exist");
        }
        if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("Reservation time range is invalid");
        }
        var detectedSpot = resolveDetectedSpot(parkingId, spot);
        if (detectedSpot == null) {
            throw new IllegalArgumentException("Spot does not exist in the selected parking");
        }
        if (detectedSpot.getStatus() == SpotStatus.MAINTENANCE) {
            throw new IllegalArgumentException("Spot is under maintenance");
        }

        var conflictingReservation = reservationRepository.findByParkingIdAndSpot(parkingId, spot).stream()
                .filter(existing -> reservationIdToIgnore == null || !existing.getId().equals(reservationIdToIgnore))
                .filter(existing -> existing.getStatus() == ReservationStatus.ACTIVE)
                .anyMatch(existing -> overlaps(startDate, endDate, existing.getStartDate(), existing.getEndDate()));
        if (conflictingReservation) {
            throw new IllegalArgumentException("Spot is not available for the selected time range");
        }
        if (employeeSpotAssignmentService.isSpotReservedForEmployee(parkingId, spot, startDate, endDate)) {
            throw new IllegalArgumentException("Spot is reserved for an on-duty employee during the selected time range");
        }
    }

    private boolean overlaps(LocalDateTime startDate, LocalDateTime endDate, LocalDateTime otherStartDate, LocalDateTime otherEndDate) {
        return startDate.isBefore(otherEndDate) && endDate.isAfter(otherStartDate);
    }

    private String generateReportCode() {
        String code;
        do {
            code = "RPT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        } while (clientReportRepository.existsByCode(code));
        return code;
    }

    private Instant parseDateTime(String value) {
        return Instant.parse(value);
    }

    private DetectedSpot resolveDetectedSpot(String parkingId, String spotCode) {
        var normalized = normalizeSpot(spotCode);
        int row = normalized.charAt(0) - 'A';
        int col = Integer.parseInt(normalized.substring(1)) - 1;
        return detectedSpotRepository.findByParkingIdAndRowAndCol(parkingId, row, col).stream().findFirst().orElse(null);
    }

    private String normalizeSpot(String spotCode) {
        if (spotCode == null) {
            throw new IllegalArgumentException("Spot is required");
        }
        var normalized = spotCode.trim().toUpperCase();
        if (!normalized.matches("[A-Z]+\\d+")) {
            throw new IllegalArgumentException("Spot code is invalid");
        }
        return normalized;
    }

    private double calculateBaseAmount(Double pricePerHour, LocalDateTime startDate, LocalDateTime endDate) {
        double hourlyRate = pricePerHour == null ? 0.0 : pricePerHour;
        long minutes = Math.max(1, ChronoUnit.MINUTES.between(startDate, endDate));
        return Math.round(((minutes / 60.0) * hourlyRate) * 100.0) / 100.0;
    }

    private double resolveDiscountPercent(String clientId) {
        return subscriptionRepository.findAllByClientId(clientId).stream()
                .findFirst()
                .flatMap(subscription -> clientPlanRepository.findById(subscription.getPlanId()))
                .map(plan -> plan.getDiscountPercent() == null ? 0.0 : plan.getDiscountPercent())
                .orElse(0.0);
    }

    private double applyDiscount(double baseAmount, double discountPercent) {
        double discounted = baseAmount * (1 - (Math.max(0.0, discountPercent) / 100.0));
        return Math.round(discounted * 100.0) / 100.0;
    }

    private void createReceiptForReservation(Reservation reservation, String parkingName) {
        if (!receiptRepository.findAllByReservationId(reservation.getId().toString()).isEmpty()) {
            return;
        }
        long totalMinutes = Math.max(1, ChronoUnit.MINUTES.between(reservation.getStartDate(), reservation.getEndDate()));
        int hours = (int) (totalMinutes / 60);
        int minutes = (int) (totalMinutes % 60);
        var receipt = new Receipt(
                reservation.getClientId(),
                reservation.getId().toString(),
                generateInvoiceNumber(),
                parkingName,
                LocalDate.now(clock).toString(),
                hours,
                minutes,
                "Simulated",
                reservation.getAmount(),
                ReceiptStatus.PAID);
        receiptRepository.save(receipt);
    }

    private void registerReservationSavings(String clientId, double discountPercent, double baseAmount, double amount) {
        subscriptionRepository.findAllByClientId(clientId).stream().findFirst().ifPresent(subscription -> {
            double savings = discountPercent <= 0 ? 0.0 : Math.max(0.0, baseAmount - amount);
            subscription.registerReservation(currentYearMonth(), savings);
            subscriptionRepository.save(subscription);
        });
    }

    private String currentYearMonth() {
        var today = LocalDate.now(clock);
        return "%d-%02d".formatted(today.getYear(), today.getMonthValue());
    }

    private String generateReservationCode() {
        return "SPG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String generateInvoiceNumber() {
        return "INV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
