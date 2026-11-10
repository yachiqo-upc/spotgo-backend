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

/** IAM uses native UUID accounts and role assignments. Parking uses UUID-based tenants. */
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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Create your profile first."));
    }

    @Transactional
    public Profile create(SpotgoUserPrincipal actor, String firstName, String lastName, String phone) {
        UUID accountId = accountId(actor);
        if (!accounts.existsById(actor.getAccountId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found.");
        if (profiles.existsByAccountId(accountId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The account already has a profile.");
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
            throw new AccessDeniedException("The vehicle belongs to another account.");
    }

    @Transactional
    public DriverClassification assign(SpotgoUserPrincipal actor, UUID tenantId, UUID profileId, DriverProfileType type) {
        requireTenantAdmin(actor, tenantId);
        Profile administrator = current(actor);
        Profile driver = profiles.findById(profileId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found."));
        var account = accounts.findById(driver.getAccountId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver account not found."));
        if (!roles.findByAccountId(account.getId())
                .filter(role -> role.isEffectiveAt(clock.instant()) && role.getRoleType() == UserRole.DRIVER).isPresent())
            throw new IllegalArgumentException("Only driver profiles can be classified.");
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
            throw new AccessDeniedException("You do not administer this tenant.");
        if (!parkings.existsById(tenantId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found.");
    }

    private static void requireRole(SpotgoUserPrincipal actor, UserRole role) {
        if (actor == null || actor.getRole() != role)
            throw new AccessDeniedException("The caller's role is not authorized for this operation.");
    }

    private static UUID accountId(SpotgoUserPrincipal actor) {
        if (actor == null) throw new AccessDeniedException("Authentication is required.");
        return actor.getAccountId();
    }
}
