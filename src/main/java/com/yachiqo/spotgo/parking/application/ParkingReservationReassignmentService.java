package com.yachiqo.spotgo.parking.application;

import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.ConflictType;
import com.yachiqo.spotgo.parking.domain.model.aggregates.ParkingSpot;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Reservation;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.OperationalStatus;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationStatus;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.TenantStatus;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ZoneStatus;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.GuestParkingSessionRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ParkingSessionRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ParkingSpotRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.TenantRepository;
import com.yachiqo.spotgo.shared.application.events.SpotgoDomainEventPublisher;
import com.yachiqo.spotgo.shared.domain.events.SpotgoDomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ParkingReservationReassignmentService {
    private final ReservationRepository reservations;
    private final ParkingSpotRepository spots;
    private final ParkingSessionRepository sessions;
    private final GuestParkingSessionRepository guestSessions;
    private final TenantRepository tenants;
    private final ParkingOccupancyGateway occupancy;
    private final SpotgoDomainEventPublisher domainEvents;
    private final Clock clock;

    public ParkingReservationReassignmentService(ReservationRepository reservations,
                                                  ParkingSpotRepository spots,
                                                  ParkingSessionRepository sessions,
                                                  GuestParkingSessionRepository guestSessions,
                                                  TenantRepository tenants,
                                                  ParkingOccupancyGateway occupancy,
                                                  SpotgoDomainEventPublisher domainEvents,
                                                  Clock clock) {
        this.reservations = reservations;
        this.spots = spots;
        this.sessions = sessions;
        this.guestSessions = guestSessions;
        this.tenants = tenants;
        this.occupancy = occupancy;
        this.domainEvents = domainEvents;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Reservation> reassignFor(SpotgoDomainEvent event) {
        if (!"monitoring.alert.opened".equals(event.type()) || event.tenantId() == null
                || !isReassignmentConflict(event.data().get("conflict_type"))) return Optional.empty();
        UUID occupiedSpotId = UUID.fromString(event.data().get("spot_id"));
        var tenant = tenants.lock(event.tenantId()).orElse(null);
        if (tenant == null || tenant.getStatus() != TenantStatus.ACTIVE) return Optional.empty();
        var originalSpot = spots.lock(occupiedSpotId).orElse(null);
        if (originalSpot == null || !originalSpot.getTenantId().equals(tenant.getId())) return Optional.empty();

        var now = clock.instant();
        List<Reservation> affected = reservations.byTenant(tenant.getId()).stream()
                .filter(reservation -> reservation.getSpotId().equals(occupiedSpotId))
                .filter(reservation -> reservation.getStatus() == ReservationStatus.RESERVED)
                .filter(reservation -> reservation.getEndAt().isAfter(now))
                .filter(reservation -> sessions.byReservation(reservation.getId()).isEmpty())
                .sorted(Comparator.comparing(Reservation::getStartAt))
                .toList();

        for (Reservation candidate : affected) {
            var lockedReservation = reservations.lock(candidate.getId()).orElse(null);
            if (lockedReservation == null || lockedReservation.getStatus() != ReservationStatus.RESERVED
                    || !lockedReservation.getSpotId().equals(occupiedSpotId)
                    || !lockedReservation.getEndAt().isAfter(now)
                    || sessions.byReservation(lockedReservation.getId()).isPresent()) continue;

            for (ParkingSpot option : spots.byTenant(tenant.getId()).stream()
                    .filter(spot -> !spot.getId().equals(occupiedSpotId))
                    .sorted(Comparator.comparing(ParkingSpot::getCode))
                    .toList()) {
                var alternative = spots.lock(option.getId()).orElse(null);
                if (alternative == null || !compatible(originalSpot, alternative)
                        || !availableFor(alternative, lockedReservation, now)) continue;

                UUID previousSpotId = lockedReservation.getSpotId();
                lockedReservation.reassignSpot(alternative);
                var saved = reservations.saveAndFlush(lockedReservation);
                domainEvents.publish("parking.reservation.reassigned", "Parking Infrastructure", "Reservation",
                        saved.getId(), tenant.getId(), List.of(saved.getProfile().getAccountId()),
                        Map.of("reservation_id", saved.getId().toString(),
                                "previous_spot_id", previousSpotId.toString(),
                                "spot_id", alternative.getId().toString(),
                                "zone_id", alternative.getZoneId().toString(),
                                "reason", event.data().get("conflict_type")));
                return Optional.of(saved);
            }
        }
        return Optional.empty();
    }

    private boolean availableFor(ParkingSpot alternative, Reservation reservation, java.time.Instant now) {
        if (alternative.getOperationalStatus() != OperationalStatus.ACTIVE
                || alternative.getZone().getStatus() != ZoneStatus.ACTIVE
                || alternative.getZone().getTenant().getStatus() != TenantStatus.ACTIVE
                || !occupancy.physicalOccupancy(alternative.getId()).equals("AVAILABLE")
                || !sessions.openSpot(alternative.getId()).isEmpty()
                || !guestSessions.openSpot(alternative.getId()).isEmpty()) return false;
        return reservations.overlappingSpot(alternative.getId(), reservation.getStartAt(), reservation.getEndAt())
                .stream().noneMatch(existing -> !existing.getId().equals(reservation.getId()) && existing.blocks(now));
    }

    private static boolean compatible(ParkingSpot source, ParkingSpot alternative) {
        return source.getSpotType().equals(alternative.getSpotType())
                && source.getFeatures().equals(alternative.getFeatures())
                && source.getTenantId().equals(alternative.getTenantId());
    }

    private static boolean isReassignmentConflict(String conflictType) {
        return ConflictType.OVERSTAY.name().equals(conflictType)
                || ConflictType.UNEXPECTED_OCCUPANCY.name().equals(conflictType)
                || ConflictType.SPACE_NOT_AVAILABLE.name().equals(conflictType);
    }
}
