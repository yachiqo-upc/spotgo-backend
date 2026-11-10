package com.yachiqo.spotgo.monitoring.application;

import com.yachiqo.spotgo.monitoring.interfaces.rest.resources.AnalyticsResource;
import com.yachiqo.spotgo.monitoring.interfaces.rest.resources.OccupancyByHourResource;
import com.yachiqo.spotgo.monitoring.interfaces.rest.resources.SpotUtilizationResource;
import com.yachiqo.spotgo.monitoring.interfaces.rest.resources.WeeklyTrendResource;
import com.yachiqo.spotgo.parking.domain.model.aggregates.ParkingSpot;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Tenant;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Reservation;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationStatus;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.OperationalStatus;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ParkingSpotRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.TenantRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final int MAX_CUSTOM_RANGE_DAYS = 31;
    private static final int MAX_UTILIZED_SPOTS = 10;

    private final TenantRepository parkingRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingSpotRepository detectedSpotRepository;
    private final Clock clock;

    public AnalyticsService(TenantRepository parkingRepository,
                            ReservationRepository reservationRepository,
                            ParkingSpotRepository detectedSpotRepository,
                            Clock clock) {
        this.parkingRepository = parkingRepository;
        this.reservationRepository = reservationRepository;
        this.detectedSpotRepository = detectedSpotRepository;
        this.clock = clock;
    }

    public AnalyticsResource getAnalytics(String parkingId, String period, LocalDate from, LocalDate to) {
        var parking = parkingRepository.findById(UUID.fromString(parkingId))
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + parkingId));
        var range = resolveRange(period, from, to);
        var previousRange = range.previous();

        var reservations = reservationRepository.byTenant(UUID.fromString(parkingId));
        var detectedSpots = detectedSpotRepository.byTenant(UUID.fromString(parkingId));

        var currentReservations = reservations.stream()
                .filter(reservation -> overlaps(reservation, range.start(), range.endExclusive()))
                .toList();
        var previousReservations = reservations.stream()
                .filter(reservation -> overlaps(reservation, previousRange.start(), previousRange.endExclusive()))
                .toList();

        var occupancyByHour = buildOccupancyByHour(parkingId, currentReservations, parking, range);
        var weeklyTrends = buildWeeklyTrends(parkingId, reservations, parking, range, period);
        var maintenanceSpotsCount = (int) detectedSpots.stream()
                .filter(spot -> spot.getOperationalStatus() == OperationalStatus.MAINTENANCE)
                .count();
        var mostUtilizedSpots = buildMostUtilizedSpots(currentReservations, detectedSpots);

        var averageOccupancy = round(oneHundred(averageIntensity(occupancyByHour)));
        var previousAverageOccupancy = round(oneHundred(averageIntensity(
                buildOccupancyByHour(parkingId, previousReservations, parking, previousRange))));
        var totalRevenue = calculateRevenue(currentReservations, range);
        var previousRevenue = calculateRevenue(previousReservations, previousRange);
        var peakHour = occupancyByHour.stream()
                .max(Comparator.comparingDouble(OccupancyByHourResource::intensity))
                .map(OccupancyByHourResource::hour)
                .orElse("00:00");
        var totalCapacity = Integer.valueOf(detectedSpotRepository.byTenant(parking.getId()).size()) != null ? Integer.valueOf(detectedSpotRepository.byTenant(parking.getId()).size()) : Integer.valueOf(detectedSpotRepository.byTenant(parking.getId()).size());
        var efficiencyIndex = calculateEfficiencyIndex(currentReservations, detectedSpots, parking, averageOccupancy);

        return new AnalyticsResource(
                parking.getId().toString(),
                null,
                parking.getName(),
                Integer.valueOf(detectedSpotRepository.byTenant(parking.getId()).size()),
                null,
                averageOccupancy,
                trendPercent(averageOccupancy, previousAverageOccupancy),
                peakHour,
                totalRevenue,
                trendPercent(totalRevenue, previousRevenue),
                maintenanceSpotsCount > 0 ? "maintenance" : "active",
                totalCapacity,
                efficiencyIndex,
                maintenanceSpotsCount,
                occupancyByHour,
                weeklyTrends,
                mostUtilizedSpots
        );
    }

    private List<OccupancyByHourResource> buildOccupancyByHour(String parkingId,
                                                               List<Reservation> reservations,
                                                               Tenant parking,
                                                               TimeRange range) {
        var points = new ArrayList<OccupancyByHourResource>(24);
        double capacity = Math.max(1, Integer.valueOf(detectedSpotRepository.byTenant(parking.getId()).size()) == null ? 1 : Integer.valueOf(detectedSpotRepository.byTenant(parking.getId()).size()));

        for (int hour = 0; hour < 24; hour++) {
            double occupancySum = 0;
            int sampledDays = 0;

            for (LocalDate date = range.start().toLocalDate(); date.isBefore(range.endExclusive().toLocalDate()); date = date.plusDays(1)) {
                var slotStart = date.atTime(hour, 0);
                var slotEnd = slotStart.plusHours(1);
                if (!slotStart.isBefore(range.endExclusive()) || !slotEnd.isAfter(range.start())) {
                    continue;
                }
                sampledDays++;
                long activeReservations = reservations.stream()
                        .filter(this::isCountableReservation)
                        .filter(reservation -> LocalDateTime.ofInstant(reservation.getStartAt(), java.time.ZoneId.of("America/Lima")).isBefore(slotEnd)
                                && LocalDateTime.ofInstant(reservation.getEndAt(), java.time.ZoneId.of("America/Lima")).isAfter(slotStart))
                        .count();
                occupancySum += Math.min(1.0, activeReservations / capacity);
            }

            double intensity = sampledDays == 0 ? 0 : occupancySum / sampledDays;
            points.add(new OccupancyByHourResource(
                    UUID.randomUUID().toString(),
                    parkingId,
                    String.format("%02d:00", hour),
                    roundRatio(intensity)
            ));
        }

        return points;
    }

    private List<WeeklyTrendResource> buildWeeklyTrends(String parkingId,
                                                        List<Reservation> reservations,
                                                        Tenant parking,
                                                        TimeRange currentRange,
                                                        String period) {
        var trendRange = "today".equalsIgnoreCase(period)
                ? new TimeRange(currentRange.endExclusive().toLocalDate().minusDays(6).atStartOfDay(), currentRange.endExclusive(), 7)
                : currentRange;
        var points = new ArrayList<WeeklyTrendResource>();
        double capacity = Math.max(1, Integer.valueOf(detectedSpotRepository.byTenant(parking.getId()).size()) == null ? 1 : Integer.valueOf(detectedSpotRepository.byTenant(parking.getId()).size()));

        for (LocalDate date = trendRange.start().toLocalDate(); date.isBefore(trendRange.endExclusive().toLocalDate()); date = date.plusDays(1)) {
            double dailyOccupancy = 0;
            for (int hour = 0; hour < 24; hour++) {
                var slotStart = date.atTime(hour, 0);
                var slotEnd = slotStart.plusHours(1);
                long activeReservations = reservations.stream()
                        .filter(this::isCountableReservation)
                        .filter(reservation -> LocalDateTime.ofInstant(reservation.getStartAt(), java.time.ZoneId.of("America/Lima")).isBefore(slotEnd)
                                && LocalDateTime.ofInstant(reservation.getEndAt(), java.time.ZoneId.of("America/Lima")).isAfter(slotStart))
                        .count();
                dailyOccupancy += Math.min(1.0, activeReservations / capacity);
            }
            var label = trendRange.days() <= 7
                    ? date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                    : date.toString();
            points.add(new WeeklyTrendResource(
                    UUID.randomUUID().toString(),
                    parkingId,
                    label,
                    roundRatio(dailyOccupancy / 24.0)
            ));
        }

        return points;
    }

    private List<SpotUtilizationResource> buildMostUtilizedSpots(List<Reservation> reservations, List<ParkingSpot> detectedSpots) {
        record SpotStats(int count, double revenue) {}

        Map<String, SpotStats> spotStats = new HashMap<>();
        for (var reservation : reservations) {
            if (!isCountableReservation(reservation) || reservation.getSpot().getCode() == null || reservation.getSpot().getCode().isBlank()) {
                continue;
            }
            var current = spotStats.getOrDefault(reservation.getSpot().getCode(), new SpotStats(0, 0));
            double amount = reservation.getAmount().doubleValue();
            spotStats.put(reservation.getSpot().getCode(), new SpotStats(current.count() + 1, current.revenue() + amount));
        }

        int maxCount = spotStats.values().stream().mapToInt(SpotStats::count).max().orElse(1);
        Map<String, String> statusBySpotCode = new HashMap<>();
        for (var spot : detectedSpots) {
            statusBySpotCode.put(toSpotCode(spot), toFrontendStatus(spot.getOperationalStatus()));
        }

        var resources = new ArrayList<SpotUtilizationResource>();
        int index = 0;
        for (var entry : spotStats.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getValue().count(), left.getValue().count()))
                .limit(MAX_UTILIZED_SPOTS)
                .toList()) {
            var spotCode = entry.getKey();
            var stats = entry.getValue();
            resources.add(new SpotUtilizationResource(
                    "su-" + (++index),
                    spotCode,
                    spotCode,
                    "Level " + spotCode.charAt(0),
                    "standard",
                    statusBySpotCode.getOrDefault(spotCode, "available"),
                    stats.count(),
                    (int) Math.round((stats.count() * 100.0) / maxCount),
                    round(stats.revenue())
            ));
        }
        return resources;
    }

    private boolean overlaps(Reservation reservation, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        return LocalDateTime.ofInstant(reservation.getStartAt(), java.time.ZoneId.of("America/Lima")).isBefore(endExclusive) && LocalDateTime.ofInstant(reservation.getEndAt(), java.time.ZoneId.of("America/Lima")).isAfter(startInclusive);
    }

    private boolean isCountableReservation(Reservation reservation) {
        return java.util.Set.of(ReservationStatus.RESERVED,ReservationStatus.ACTIVE,ReservationStatus.COMPLETED,ReservationStatus.OVERSTAYED).contains(reservation.getStatus());
    }

    private double calculateRevenue(List<Reservation> reservations, TimeRange range) {
        return round(reservations.stream()
                .filter(this::isCountableReservation)
                .filter(reservation -> !LocalDateTime.ofInstant(reservation.getStartAt(), java.time.ZoneId.of("America/Lima")).isBefore(range.start())
                        && LocalDateTime.ofInstant(reservation.getStartAt(), java.time.ZoneId.of("America/Lima")).isBefore(range.endExclusive()))
                .mapToDouble(reservation -> reservation.getAmount().doubleValue())
                .sum());
    }

    private double averageIntensity(List<OccupancyByHourResource> points) {
        return points.stream().mapToDouble(OccupancyByHourResource::intensity).average().orElse(0);
    }

    private double oneHundred(double value) {
        return value * 100.0;
    }

    private double trendPercent(double current, double previous) {
        if (Math.abs(previous) < 0.0001) {
            return round(current == 0 ? 0 : 100);
        }
        return round(((current - previous) / previous) * 100.0);
    }

    private double calculateEfficiencyIndex(List<Reservation> reservations,
                                            List<ParkingSpot> detectedSpots,
                                            Tenant parking,
                                            double averageOccupancy) {
        double totalSpaces = Math.max(1, Integer.valueOf(detectedSpotRepository.byTenant(parking.getId()).size()) == null ? 1 : Integer.valueOf(detectedSpotRepository.byTenant(parking.getId()).size()));

        long nonCancelledReservations = reservations.stream()
                .filter(this::isCountableReservation)
                .count();
        long completedReservations = reservations.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.COMPLETED)
                .count();
        long utilizedSpots = reservations.stream()
                .filter(this::isCountableReservation)
                .map(r -> r.getSpot().getCode())
                .filter(spot -> spot != null && !spot.isBlank())
                .distinct()
                .count();
        long maintenanceSpots = detectedSpots.stream()
                .filter(spot -> spot.getOperationalStatus() == OperationalStatus.MAINTENANCE)
                .count();

        double occupancyScore = Math.min(10.0, averageOccupancy / 10.0);
        double utilizationScore = Math.min(10.0, (utilizedSpots / totalSpaces) * 10.0);
        double completionScore = nonCancelledReservations == 0
                ? 0
                : Math.min(10.0, (completedReservations * 10.0) / nonCancelledReservations);
        double maintenancePenalty = Math.min(2.0, (maintenanceSpots / totalSpaces) * 5.0);

        double weightedScore = (occupancyScore * 0.45) + (utilizationScore * 0.25) + (completionScore * 0.30) - maintenancePenalty;
        return round(Math.max(0, Math.min(10.0, weightedScore)));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double roundRatio(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private String toSpotCode(ParkingSpot spot) { return spot.getCode(); }

    private String toFrontendStatus(OperationalStatus status) {
        if (status == null) {
            return "available";
        }
        return switch (status) {
            case MAINTENANCE -> "maintenance";
            case INACTIVE -> "inactive";
            case ACTIVE -> "unknown";
        };
    }

    private TimeRange resolveRange(String period, LocalDate from, LocalDate to) {
        var today = LocalDate.now(clock);
        if (period == null || period.isBlank() || "today".equalsIgnoreCase(period)) {
            return new TimeRange(today.atStartOfDay(), today.plusDays(1).atStartOfDay(), 1);
        }
        if ("last7".equalsIgnoreCase(period)) {
            return new TimeRange(today.minusDays(6).atStartOfDay(), today.plusDays(1).atStartOfDay(), 7);
        }
        if (!"custom".equalsIgnoreCase(period)) {
            throw new IllegalArgumentException("Unsupported analytics period: " + period);
        }
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Custom analytics range requires valid from/to dates");
        }
        if (from.isAfter(today) || to.isAfter(today)) {
            throw new IllegalArgumentException("Custom analytics range cannot include future dates");
        }
        int days = (int) ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_CUSTOM_RANGE_DAYS) {
            throw new IllegalArgumentException("Custom analytics range cannot exceed %d days".formatted(MAX_CUSTOM_RANGE_DAYS));
        }
        return new TimeRange(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), days);
    }

    private record TimeRange(LocalDateTime start, LocalDateTime endExclusive, int days) {
        private TimeRange previous() {
            return new TimeRange(start.minusDays(days), start, days);
        }
    }
}
