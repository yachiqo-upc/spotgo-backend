package com.yachiqo.spotgo.shared.application.security;

import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.Employee;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Blueprint;
import com.yachiqo.spotgo.parking.domain.model.aggregates.ClientReport;
import com.yachiqo.spotgo.parking.domain.model.aggregates.DetectedSpot;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Reservation;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ParkingRepository;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Vehicle;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    private final ParkingRepository parkingRepository;

    public AuthorizationService(ParkingRepository parkingRepository) {
        this.parkingRepository = parkingRepository;
    }

    public boolean isAdmin(SpotgoUserPrincipal principal) {
        return principal.getRole() == UserRole.PARKING_ADMIN;
    }

    public boolean isClient(SpotgoUserPrincipal principal) {
        return principal.getRole() == UserRole.DRIVER;
    }

    public String requireAdminParkingId(SpotgoUserPrincipal principal) {
        if (!isAdmin(principal)) {
            throw new AccessDeniedException("Admin role required");
        }
        if (principal.getTenantId() == null || !parkingRepository.existsById(principal.getTenantId().toString())) {
            throw new AccessDeniedException("El rol no tiene un estacionamiento disponible asignado");
        }
        return principal.getTenantId().toString();
    }

    public void requireParkingOwnership(SpotgoUserPrincipal principal, String parkingId) {
        if (!requireAdminParkingId(principal).equals(parkingId)) {
            throw new AccessDeniedException("Parking does not belong to authenticated admin");
        }
    }

    public void requireVehicleOwner(SpotgoUserPrincipal principal, Vehicle vehicle) {
        if (!vehicle.getProfile().getAccountId().toString().equals(principal.getUserId())) {
            throw new AccessDeniedException("Vehicle does not belong to authenticated client");
        }
    }


    public void requireReservationAccess(SpotgoUserPrincipal principal, Reservation reservation) {
        if (isClient(principal) && reservation.getClientId().equals(principal.getUserId())) {
            return;
        }
        if (isAdmin(principal) && requireAdminParkingId(principal).equals(reservation.getParkingId())) {
            return;
        }
        throw new AccessDeniedException("Reservation is outside authenticated scope");
    }

    public void requireClientReportAccess(SpotgoUserPrincipal principal, ClientReport report) {
        if (isClient(principal) && report.getClientId().equals(principal.getUserId())) {
            return;
        }
        if (isAdmin(principal) && requireAdminParkingId(principal).equals(report.getParkingId())) {
            return;
        }
        throw new AccessDeniedException("Client report is outside authenticated scope");
    }

    public void requireBlueprintOwnership(SpotgoUserPrincipal principal, Blueprint blueprint) {
        requireParkingOwnership(principal, blueprint.getParkingId());
    }

    public void requireDetectedSpotOwnership(SpotgoUserPrincipal principal, DetectedSpot spot) {
        requireParkingOwnership(principal, spot.getParkingId());
    }

    public void requireEmployeeOwnership(SpotgoUserPrincipal principal, Employee employee) {
        requireParkingOwnership(principal, employee.getParkingId());
    }
}
