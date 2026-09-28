package com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.seeder;

import com.yachiqo.spotgo.iam.domain.model.aggregates.UserAccount;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.UserAccountRepository;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Blueprint;
import com.yachiqo.spotgo.parking.domain.model.aggregates.DetectedSpot;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Parking;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Reservation;
import com.yachiqo.spotgo.parking.domain.model.aggregates.ClientReport;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationStatus;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.SpotStatus;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReportType;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReportStatus;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.BlueprintRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.DetectedSpotRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ParkingRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ClientReportRepository;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.Employee;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.OccupancyByHour;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.WeeklyTrend;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.EmployeeRole;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.EmployeeSchedule;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.EmployeeStatus;
import com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories.OccupancyByHourRepository;
import com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories.WeeklyTrendRepository;
import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.entities.ClientPlanPersistenceEntity;
import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.entities.SubscriptionPersistenceEntity;
import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.entities.ReceiptPersistenceEntity;
import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories.ClientPlanPersistenceRepository;
import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories.SubscriptionPersistenceRepository;
import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories.ReceiptPersistenceRepository;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Favorite;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Vehicle;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.FavoriteRepository;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.VehicleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

@Profile({"dev", "local", "production"})
@Component
public class DbSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DbSeeder.class);

    private final ParkingRepository parkingRepository;
    private final BlueprintRepository blueprintRepository;
    private final DetectedSpotRepository detectedSpotRepository;
    private final ReservationRepository reservationRepository;
    private final ClientReportRepository clientReportRepository;
    private final ClientPlanPersistenceRepository clientPlanRepository;
    private final SubscriptionPersistenceRepository subscriptionRepository;
    private final ReceiptPersistenceRepository receiptRepository;
    private final EmployeeRepository employeeRepository;
    private final OccupancyByHourRepository occupancyByHourRepository;
    private final WeeklyTrendRepository weeklyTrendRepository;
    private final UserAccountRepository userAccountRepository;
    private final FavoriteRepository favoriteRepository;
    private final VehicleRepository vehicleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.seeder.reset-before-seed:false}")
    private boolean resetBeforeSeed;

    public DbSeeder(ParkingRepository parkingRepository,
                    BlueprintRepository blueprintRepository,
                    DetectedSpotRepository detectedSpotRepository,
                    ReservationRepository reservationRepository,
                    ClientReportRepository clientReportRepository,
                    ClientPlanPersistenceRepository clientPlanRepository,
                     SubscriptionPersistenceRepository subscriptionRepository,
                     ReceiptPersistenceRepository receiptRepository,
                     EmployeeRepository employeeRepository,
                     OccupancyByHourRepository occupancyByHourRepository,
                     WeeklyTrendRepository weeklyTrendRepository,
                     UserAccountRepository userAccountRepository,
                     FavoriteRepository favoriteRepository,
                     VehicleRepository vehicleRepository,
                     PasswordEncoder passwordEncoder) {
        this.parkingRepository = parkingRepository;
        this.blueprintRepository = blueprintRepository;
        this.detectedSpotRepository = detectedSpotRepository;
        this.reservationRepository = reservationRepository;
        this.clientReportRepository = clientReportRepository;
        this.clientPlanRepository = clientPlanRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.receiptRepository = receiptRepository;
        this.employeeRepository = employeeRepository;
        this.occupancyByHourRepository = occupancyByHourRepository;
        this.weeklyTrendRepository = weeklyTrendRepository;
        this.userAccountRepository = userAccountRepository;
        this.favoriteRepository = favoriteRepository;
        this.vehicleRepository = vehicleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        log.info("database connection OK, seeding seed data...");

        try {
            if (resetBeforeSeed) {
                log.info("reset-before-seed is true, wiping tables...");
                favoriteRepository.deleteAll();
                vehicleRepository.deleteAll();
                clientReportRepository.deleteAll(); // references reservations, delete first
                reservationRepository.deleteAll();
                detectedSpotRepository.deleteAll();
                blueprintRepository.deleteAll();
                parkingRepository.deleteAll();
                receiptRepository.deleteAll();
                subscriptionRepository.deleteAll();
                clientPlanRepository.deleteAll();
                employeeRepository.deleteAll();
                occupancyByHourRepository.deleteAll();
                weeklyTrendRepository.deleteAll();
                userAccountRepository.deleteAll();
                log.info("tables wiped successfully");
            } else if (parkingRepository.count() > 0) {
                log.info("tables already contain data, skipping seed");
                return;
            }

            var resource = new ClassPathResource("db.json");

            if (!resource.exists()) {
                log.error("db.json not found at classpath location, skipping seed");
                return;
            }

            var root = objectMapper.readTree(resource.getInputStream());

            seedUsers(root.get("users"));
            seedParkings(root.get("parkings"));
            seedFavorites(root.get("favorites"));
            seedBlueprints(root.get("blueprints"));
            seedDetectedSpots(root.get("detectedSpots"));
            seedReservations(root.get("reservations"));
            seedClientReports(root.get("clientReports"));
            seedClientPlans(root.get("clientPlans"));
            seedSubscriptions(root.get("subscriptions"));
            seedReceipts(root.get("receipts"));
            seedEmployees(root.get("employees"));
            seedOccupancyByHour(root.get("occupancyByHour"));
            seedWeeklyTrends(root.get("weeklyTrends"));
            seedVehicles(root.get("vehicles"));

            log.info("seed completed successfully");
        } catch (Exception e) {
            log.error("seed failed: {}", e.getMessage(), e);
        }
    }

    private void seedUsers(JsonNode users) {
        if (users == null || !users.isArray()) {
            log.warn("no users found in db.json");
            return;
        }
        log.info("seeding {} users...", users.size());
        for (var node : users) {
            var user = new UserAccount(
                    node.get("firstName").asText(),
                    node.get("lastName").asText(),
                    node.get("email").asText(),
                    passwordEncoder.encode(node.path("password").asText("Password123!")),
                    nullableText(node, "phone") == null ? "" : nullableText(node, "phone"),
                    UserRole.fromDisplayName(node.path("role").asText("client"))
            );
            user.setId(node.get("id").asText());
            userAccountRepository.save(user);
        }
    }

    private void seedParkings(JsonNode parkings) {
        if (parkings == null || !parkings.isArray()) {
            log.warn("no parkings found in db.json");
            return;
        }
        log.info("seeding {} parkings...", parkings.size());
        for (var node : parkings) {
            var parking = new Parking(
                    nullableText(node, "adminId"),
                    node.get("name").asText(),
                    node.get("address").asText(),
                    node.get("city").asText(),
                    node.get("totalSpaces").asInt(),
                    nullableInt(node, "availableSpaces"),
                    nullableInt(node, "totalFloors"),
                    nullableDouble(node, "averageOccupancy"),
                    nullableDouble(node, "occupancyTrendPercent"),
                    nullableText(node, "peakHour"),
                    nullableDouble(node, "totalRevenue"),
                    nullableText(node, "systemStatus"),
                    node.has("rating") && !node.get("rating").isNull() ? node.get("rating").asDouble() : null,
                    node.has("pricePerHour") && !node.get("pricePerHour").isNull() ? node.get("pricePerHour").asDouble() : null,
                    nullableDouble(node, "revenueTrendPercent"),
                    nullableInt(node, "totalCapacity"),
                    nullableDouble(node, "efficiencyIndex")
            );
            parking.setId(node.get("id").asText());
            parkingRepository.save(parking);
        }
    }

    private void seedFavorites(JsonNode favorites) {
        if (favorites == null || !favorites.isArray()) {
            log.warn("no favorites found in db.json");
            return;
        }
        log.info("seeding {} favorites...", favorites.size());
        for (var node : favorites) {
            var clientId = node.get("clientId").asText();
            if (!userAccountRepository.existsById(clientId)) {
                log.warn("clientId {} not found, skipping favorite", clientId);
                continue;
            }
            var parkingId = node.get("parkingId").asText();
            if (!parkingRepository.existsById(parkingId)) {
                log.warn("parkingId {} not found, skipping favorite", parkingId);
                continue;
            }
            var favorite = new Favorite(
                    clientId,
                    parkingId,
                    node.get("distanceMi").asDouble(),
                    node.get("lastVisited").asText()
            );
            favorite.setId(node.get("id").asText());
            favoriteRepository.save(favorite);
        }
    }

    private void seedBlueprints(JsonNode blueprints) {
        if (blueprints == null || !blueprints.isArray()) {
            log.warn("no blueprints found in db.json");
            return;
        }
        log.info("seeding {} blueprints...", blueprints.size());
        for (var node : blueprints) {
            var parkingId = node.get("parkingId").asText();
            if (!parkingRepository.existsById(parkingId)) {
                log.warn("parkingId {} not found, skipping blueprint", node.get("parkingId").asText());
                continue;
            }

            var blueprint = new Blueprint(
                    nullableText(node, "adminId"),
                    parkingId,
                    node.has("name") ? node.get("name").asText("") : "",
                    node.has("dataUrl") ? node.get("dataUrl").asText("") : ""
            );
            blueprint.setId(node.get("id").asText());
            blueprintRepository.save(blueprint);
        }
    }

    private void seedDetectedSpots(JsonNode detectedSpots) {
        if (detectedSpots == null || !detectedSpots.isArray()) {
            log.warn("no detectedSpots found in db.json");
            return;
        }
        log.info("seeding {} detectedSpots...", detectedSpots.size());
        for (var node : detectedSpots) {
            var blueprintId = node.get("blueprintId").asText();
            if (!blueprintRepository.existsById(blueprintId)) {
                log.warn("blueprintId {} not found, skipping detectedSpot", node.get("blueprintId").asText());
                continue;
            }

            var parkingId = node.has("parkingId") ? node.get("parkingId").asText() : null;

            var statusStr = node.has("status") ? node.get("status").asText("available") : "available";
            var status = SpotStatus.fromDisplayName(statusStr);

            var detectedSpot = new DetectedSpot(
                    node.has("code") ? node.get("code").asInt() : nullableInt(node, "localId"),
                    blueprintId,
                    parkingId,
                    node.has("row") ? node.get("row").asInt() : null,
                    node.has("col") ? node.get("col").asInt() : null,
                    node.has("x_pct") ? node.get("x_pct").asDouble() : 0.0,
                    node.has("y_pct") ? node.get("y_pct").asDouble() : 0.0,
                    node.has("w_pct") ? node.get("w_pct").asDouble() : 0.0,
                    node.has("h_pct") ? node.get("h_pct").asDouble() : 0.0,
                    status
            );
            detectedSpot.setId(node.get("id").asText());
            detectedSpotRepository.save(detectedSpot);
        }
    }

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private void seedReservations(JsonNode reservations) {
        if (reservations == null || !reservations.isArray()) {
            log.warn("no reservations found in db.json");
            return;
        }
        log.info("seeding {} reservations...", reservations.size());
        for (var node : reservations) {
            var parkingId = node.get("parkingId").asText();
            if (!parkingRepository.existsById(parkingId)) {
                log.warn("parkingId {} not found, skipping reservation", node.get("parkingId").asText());
                continue;
            }

            var startDate = parseDateTime(node.get("startDate").asText());
            var endDate = parseDateTime(node.get("endDate").asText());

            var reservation = new Reservation(
                    node.get("clientId").asText(),
                    parkingId,
                    node.get("code").asText(),
                    node.get("spot").asText(),
                    startDate,
                    endDate,
                    node.has("amount") && !node.get("amount").isNull() ? node.get("amount").asDouble() : null,
                    node.has("baseAmount") && !node.get("baseAmount").isNull() ? node.get("baseAmount").asDouble() : null,
                    node.has("rating") && !node.get("rating").isNull() ? node.get("rating").asDouble() : null
            );
            reservation.setId(node.get("id").asText());

            var status = node.has("status") ? node.get("status").asText("active") : "active";
            switch (status) {
                case "completed" -> reservation.updateStatus(ReservationStatus.COMPLETED);
                case "cancelled" -> reservation.updateStatus(ReservationStatus.CANCELLED);
                default -> reservation.updateStatus(ReservationStatus.ACTIVE);
            }

            reservationRepository.save(reservation);
        }
    }

    private void seedClientReports(JsonNode clientReports) {
        if (clientReports == null || !clientReports.isArray()) {
            log.warn("no clientReports found in db.json");
            return;
        }
        log.info("seeding {} clientReports...", clientReports.size());
        for (var node : clientReports) {
            var parkingId = node.get("parkingId").asText();
            if (!parkingRepository.existsById(parkingId)) {
                log.warn("parkingId {} not found, skipping clientReport", node.get("parkingId").asText());
                continue;
            }
            var reservationId = node.get("reservationId").asText();
            if (!reservationRepository.existsById(reservationId)) {
                log.warn("reservationId {} not found, skipping clientReport", node.get("reservationId").asText());
                continue;
            }

            var report = new ClientReport(
                    node.get("clientId").asText(),
                    parkingId,
                    reservationId,
                    node.get("code").asText(),
                    ReportType.fromDisplayName(node.get("type").asText()),
                    parseInstant(node.get("date").asText())
            );
            report.setId(node.get("id").asText());

            var status = node.has("status") ? node.get("status").asText("submitted") : "submitted";
            report.updateStatus(ReportStatus.fromDisplayName(status));

            clientReportRepository.save(report);
        }
    }

    private void seedClientPlans(JsonNode clientPlans) {
        if (clientPlans == null || !clientPlans.isArray()) {
            log.warn("no clientPlans found in db.json");
            return;
        }
        log.info("seeding {} clientPlans...", clientPlans.size());
        for (var node : clientPlans) {
            var entity = new ClientPlanPersistenceEntity();
            entity.setType(node.get("type").asText());
            entity.setName(node.get("name").asText());
            entity.setMonthlyPrice(node.get("monthlyPrice").asDouble());
            entity.setDescription(node.get("description").asText());
            entity.setReservationsPerMonth(
                    node.has("reservationsPerMonth") && !node.get("reservationsPerMonth").isNull()
                            ? node.get("reservationsPerMonth").asInt() : null
            );
            entity.setDiscountPercent(node.get("discountPercent").asDouble());

            var features = new ArrayList<String>();
            if (node.has("features") && node.get("features").isArray()) {
                for (var f : node.get("features")) {
                    features.add(f.asText());
                }
            }
            entity.setFeatures(features);
            entity.setId(node.get("id").asText());
            clientPlanRepository.save(entity);
        }
    }

    private void seedSubscriptions(JsonNode subscriptions) {
        if (subscriptions == null || !subscriptions.isArray()) {
            log.warn("no subscriptions found in db.json");
            return;
        }
        log.info("seeding {} subscriptions...", subscriptions.size());
        for (var node : subscriptions) {
            var planId = node.get("planId").asText();
            if (!clientPlanRepository.existsById(planId)) {
                log.warn("planId {} not found, skipping subscription", node.get("planId").asText());
                continue;
            }

            var entity = new SubscriptionPersistenceEntity();
            entity.setId(node.get("id").asText());
            entity.setClientId(node.get("clientId").asText());
            entity.setPlanId(planId);
            entity.setStatus(node.get("status").asText());
            entity.setRenewsOn(node.get("renewsOn").asText());
            entity.setPricePerMonth(node.get("pricePerMonth").asDouble());
            entity.setSessions(node.get("sessions").asInt());
            entity.setSavedThisMonth(node.get("savedThisMonth").asDouble());
            entity.setSavingsMonth(node.has("savingsMonth") ? node.get("savingsMonth").asText() : null);
            entity.setMemberSince(node.get("memberSince").asText());
            entity.setAutoRenewal(node.get("autoRenewal").asBoolean());
            entity.setPaymentMethodLastFour(node.get("paymentMethodLastFour").asText());
            entity.setPaymentMethodExpiry(node.get("paymentMethodExpiry").asText());

            subscriptionRepository.save(entity);
        }
    }

    private void seedReceipts(JsonNode receipts) {
        if (receipts == null || !receipts.isArray()) {
            log.warn("no receipts found in db.json");
            return;
        }
        log.info("seeding {} receipts...", receipts.size());
        for (var node : receipts) {
            var entity = new ReceiptPersistenceEntity();
            entity.setId(node.get("id").asText());
            entity.setClientId(node.get("clientId").asText());
            entity.setReservationId(node.get("reservationId").asText());
            entity.setInvoiceNumber(node.get("invoiceNumber").asText());
            entity.setLocationName(node.get("locationName").asText());
            entity.setDate(node.get("date").asText());
            entity.setDurationHours(node.get("durationHours").asInt());
            entity.setDurationMinutes(node.get("durationMinutes").asInt());
            entity.setPaymentMethod(node.get("paymentMethod").asText());
            entity.setAmount(node.get("amount").asDouble());
            entity.setStatus(node.get("status").asText());

            receiptRepository.save(entity);
        }
    }

    private void seedEmployees(JsonNode employees) {
        if (employees == null || !employees.isArray()) {
            log.warn("no employees found in db.json");
            return;
        }
        log.info("seeding {} employees...", employees.size());
        for (var node : employees) {
            var parkingId = node.has("parkingId") ? node.get("parkingId").asText() : null;
            if (parkingId == null || !parkingRepository.existsById(parkingId)) {
                log.warn("parkingId {} not found, skipping employee", node.get("parkingId").asText());
                continue;
            }

            var employee = new Employee(
                    parkingId,
                    node.get("firstName").asText(),
                    node.get("lastName").asText(),
                    EmployeeRole.fromDisplayName(node.get("role").asText()),
                    EmployeeSchedule.fromDisplayName(node.get("schedule").asText()),
                    node.get("shiftStart").asText(),
                    node.get("shiftEnd").asText(),
                    nullableText(node, "assignedSpot"),
                    EmployeeStatus.fromDisplayName(node.get("status").asText())
            );
            employee.setId(node.get("id").asText());
            employeeRepository.save(employee);
        }
    }

    private void seedOccupancyByHour(JsonNode occupancyByHour) {
        if (occupancyByHour == null || !occupancyByHour.isArray()) {
            log.warn("no occupancyByHour found in db.json");
            return;
        }
        log.info("seeding {} occupancyByHour points...", occupancyByHour.size());
        for (var node : occupancyByHour) {
            var parkingId = node.get("parkingId").asText();
            if (!parkingRepository.existsById(parkingId)) {
                log.warn("parkingId {} not found, skipping occupancyByHour point", node.get("parkingId").asText());
                continue;
            }

            var point = new OccupancyByHour(parkingId, node.get("hour").asText(), node.get("intensity").asInt());
            point.setId(node.get("id").asText());
            occupancyByHourRepository.save(point);
        }
    }

    private void seedWeeklyTrends(JsonNode weeklyTrends) {
        if (weeklyTrends == null || !weeklyTrends.isArray()) {
            log.warn("no weeklyTrends found in db.json");
            return;
        }
        log.info("seeding {} weeklyTrends points...", weeklyTrends.size());
        for (var node : weeklyTrends) {
            var parkingId = node.get("parkingId").asText();
            if (!parkingRepository.existsById(parkingId)) {
                log.warn("parkingId {} not found, skipping weeklyTrend point", node.get("parkingId").asText());
                continue;
            }

            var point = new WeeklyTrend(parkingId, node.get("day").asText(), node.get("value").asDouble());
            point.setId(node.get("id").asText());
            weeklyTrendRepository.save(point);
        }
    }

    private void seedVehicles(JsonNode vehicles) {
        if (vehicles == null || !vehicles.isArray()) {
            log.warn("no vehicles found in db.json");
            return;
        }
        log.info("seeding {} vehicles...", vehicles.size());
        for (var node : vehicles) {
            var clientId = node.get("clientId").asText();
            if (!userAccountRepository.existsById(clientId)) {
                log.warn("clientId {} not found, skipping vehicle", clientId);
                continue;
            }
            var vehicle = new Vehicle(
                    clientId,
                    node.get("licensePlate").asText(),
                    node.get("vehicleType").asText(),
                    node.get("brand").asText(),
                    node.get("model").asText()
            );
            vehicle.setId(node.get("id").asText());
            vehicleRepository.save(vehicle);
        }
    }

    private Integer nullableInt(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asInt() : null;
    }

    private Double nullableDouble(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asDouble() : null;
    }

    private String nullableText(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private LocalDateTime parseDateTime(String dateStr) {
        try {
            return LocalDateTime.parse(dateStr, ISO_FORMAT);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e2) {
                log.warn("could not parse date '{}', using current time", dateStr);
                return LocalDateTime.now();
            }
        }
    }

    private Instant parseInstant(String dateStr) {
        try {
            return Instant.parse(dateStr);
        } catch (Exception e) {
            try {
                var ldt = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return ldt.atZone(ZoneId.of("America/Lima")).toInstant();
            } catch (Exception e2) {
                log.warn("could not parse instant '{}', using current time", dateStr);
                return Instant.now();
            }
        }
    }
}
