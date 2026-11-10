package com.yachiqo.spotgo.profiles.application.internal;

import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.AccountRepository;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.RoleAssignmentRepository;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.TenantRepository;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.*;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.DriverProfileType;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import java.time.Clock;

/** IAM uses native UUID accounts and role assignments. Parking utiliza tenants con UUID. */
@Service
@Transactional(readOnly = true)
public class ProfilesManagementService {
    private final ProfileRepository profiles;
    private final DriverClassificationRepository classifications;
    private final AccountRepository accounts;
    private final TenantRepository parkings;
    private final RoleAssignmentRepository roles;
    private final Clock clock;

    public ProfilesManagementService(ProfileRepository profiles, DriverClassificationRepository classifications,
                                     AccountRepository accounts, TenantRepository parkings,
                                     RoleAssignmentRepository roles, Clock clock) {
        this.profiles = profiles;
        this.classifications = classifications;
        this.accounts = accounts;
        this.parkings = parkings;
        this.roles = roles;
        this.clock = clock;
    }

    public Profile current(SpotgoUserPrincipal actor) {
        return profiles.findByAccountId(accountId(actor))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Crea tu perfil primero"));
    }

    @Transactional
    public Profile create(SpotgoUserPrincipal actor, String firstName, String lastName, String phone) {
        UUID accountId = accountId(actor);
        if (!accounts.existsById(actor.getAccountId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta inexistente");
        if (profiles.existsByAccountId(accountId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cuenta ya tiene un perfil");
        return profiles.saveAndFlush(new Profile(accountId, firstName, lastName, phone));
    }

    @Transactional
    public Profile update(SpotgoUserPrincipal actor, String firstName, String lastName, String phone) {
        Profile profile = current(actor);
        profile.update(firstName, lastName, phone);
        return profiles.saveAndFlush(profile);
    }

    public Profile driver(SpotgoUserPrincipal actor) {
        requireRole(actor, UserRole.DRIVER);
        return current(actor);
    }

    public void requireOwner(SpotgoUserPrincipal actor, Vehicle vehicle) {
        requireRole(actor, UserRole.DRIVER);
        if (!vehicle.getProfile().getAccountId().equals(accountId(actor)))
            throw new AccessDeniedException("El vehiculo pertenece a otra cuenta");
    }

    @Transactional
    public DriverClassification assign(SpotgoUserPrincipal actor, UUID tenantId, UUID profileId, DriverProfileType type) {
        requireTenantAdmin(actor, tenantId);
        Profile administrator = current(actor);
        Profile driver = profiles.findById(profileId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil inexistente"));
        var account = accounts.findById(driver.getAccountId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta del conductor inexistente"));
        if (!roles.findByAccountId(account.getId())
                .filter(role -> role.isEffectiveAt(clock.instant()) && role.getRoleType() == UserRole.DRIVER).isPresent())
            throw new IllegalArgumentException("Solo se clasifican perfiles de conductores");
        var classification = classifications.findByProfileIdAndTenantId(profileId, tenantId)
            .orElseGet(() -> new DriverClassification(driver, tenantId, type, administrator));
        classification.assign(type, administrator);
        return classifications.saveAndFlush(classification);
    }

    public List<DriverClassification> tenantClassifications(SpotgoUserPrincipal actor, UUID tenantId) {
        requireTenantAdmin(actor, tenantId);
        return classifications.findAllByTenantId(tenantId);
    }

    public List<DriverClassification> myClassifications(SpotgoUserPrincipal actor) {
        return classifications.findAllByProfileId(driver(actor).getId());
    }

    private void requireTenantAdmin(SpotgoUserPrincipal actor, UUID tenantId) {
        requireRole(actor, UserRole.PARKING_ADMIN);
        if (!tenantId.equals(actor.getTenantId()))
            throw new AccessDeniedException("No administras este estacionamiento");
        if (!parkings.existsById(tenantId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Estacionamiento inexistente");
    }

    private static void requireRole(SpotgoUserPrincipal actor, UserRole role) {
        if (actor == null || actor.getRole() != role)
            throw new AccessDeniedException("Rol no autorizado");
    }

    private static UUID accountId(SpotgoUserPrincipal actor) {
        if (actor == null) throw new AccessDeniedException("Autenticacion requerida");
        return actor.getAccountId();
    }
}
