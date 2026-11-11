package com.yachiqo.spotgo.iam.application;

import com.yachiqo.spotgo.iam.domain.model.aggregates.*;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.*;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.TenantStatus;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.TenantRepository;
import com.yachiqo.spotgo.profiles.application.internal.ProfilesManagementService;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Profile;
import com.yachiqo.spotgo.shared.application.events.SpotgoDomainEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AccountAdministrationService {
    private final AccountRepository accounts;
    private final RoleAssignmentRepository roles;
    private final SessionRepository sessions;
    private final AuditEntryRepository audits;
    private final TenantRepository parkings;
    private final ProfilesManagementService profiles;
    private final AuthenticationService authentication;
    private final Clock clock;
    private final SpotgoDomainEventPublisher domainEvents;
    public record ProvisionedParkingAdmin(Account account, RoleAssignment roleAssignment, Profile profile) {}
    public AccountAdministrationService(AccountRepository accounts, RoleAssignmentRepository roles,
            SessionRepository sessions, AuditEntryRepository audits, TenantRepository parkings,
            ProfilesManagementService profiles, AuthenticationService authentication, Clock clock,
            SpotgoDomainEventPublisher domainEvents) {
        this.accounts = accounts; this.roles = roles; this.sessions = sessions; this.audits = audits;
        this.parkings = parkings; this.profiles = profiles; this.authentication = authentication;
        this.clock = clock; this.domainEvents = domainEvents;
    }
    public Account own(SpotgoUserPrincipal actor) { return account(actor.getAccountId()); }
    public Page<Account> list(SpotgoUserPrincipal actor, int page, int size) {
        requireSuper(actor); return accounts.findAll(page(page, size));
    }
    public Account get(SpotgoUserPrincipal actor, UUID id) { requireSuper(actor); return account(id); }
    public RoleAssignment role(SpotgoUserPrincipal actor, UUID id) {
        if (!actor.getAccountId().equals(id)) requireSuper(actor);
        account(id);
        return roles.findByAccountId(id).orElseThrow(() -> new IamException(HttpStatus.NOT_FOUND, "Account has no role assignment."));
    }
    @Transactional
    public Account status(SpotgoUserPrincipal actor, UUID id, AccountStatus status, String source) {
        requireSuper(actor);
        var account = accounts.findByIdForUpdate(id).orElseThrow(() -> notFound());
        account.changeStatus(status);
        if (status != AccountStatus.ACTIVE) sessions.revokeAll(id, clock.instant(), SessionStatus.ACTIVE, SessionStatus.REVOKED);
        audits.save(new AuditEntry(accounts.getReferenceById(actor.getAccountId()), "ACCOUNT_STATUS_CHANGE " + id, "SUCCESS", source, clock.instant()));
        var saved=accounts.saveAndFlush(account);
        domainEvents.publish("identity.account.status_changed","Identity & Access Management","Account",id,null,List.of(id),
                Map.of("account_id",id.toString(),"status",status.name()));
        return saved;
    }
    @Transactional
    public RoleAssignment assign(SpotgoUserPrincipal actor, UUID id, UserRole role, UUID tenantId,
                                  Instant from, Instant to, AssignmentStatus status, String source) {
        requireSuper(actor);
        var account = accounts.findByIdForUpdate(id).orElseThrow(() -> notFound());
        if (role == UserRole.PARKING_ADMIN && (tenantId == null || !parkings.existsById(tenantId)))
            throw new IamException(HttpStatus.BAD_REQUEST, "PARKING_ADMIN requires an existing tenant.");
        Instant start = from == null ? clock.instant() : from;
        AssignmentStatus enabled = status == null ? AssignmentStatus.ACTIVE : status;
        var assignment = roles.findByAccountId(id).orElseGet(() -> new RoleAssignment(account, role, tenantId, start, to, enabled));
        assignment.assign(role, tenantId, start, to, enabled);
        audits.save(new AuditEntry(accounts.getReferenceById(actor.getAccountId()), "ROLE_ASSIGNMENT_CHANGE " + id, "SUCCESS", source, clock.instant()));
        var saved=roles.saveAndFlush(assignment);
        domainEvents.publish("identity.role.assigned","Identity & Access Management","RoleAssignment",saved.getId(),tenantId,
                List.of(id),Map.of("account_id",id.toString(),"role",role.name()));
        return saved;
    }
    @Transactional
    public ProvisionedParkingAdmin provisionParkingAdmin(SpotgoUserPrincipal actor, UUID tenantId,
            String email, String firstName, String lastName, String phone, String source) {
        requireSuper(actor);
        var tenant = parkings.findById(tenantId).orElseThrow(() -> notFound());
        if (tenant.getStatus() != TenantStatus.ACTIVE)
            throw new IamException(HttpStatus.CONFLICT, "Parking Admin accounts can only be assigned to an active tenant.");

        var account = authentication.provisionParkingAdminAccount(email, tenantId, source);
        var profile = profiles.provisionParkingAdminProfile(actor, account.getId(), firstName, lastName, phone);
        var assignment = roles.findByAccountId(account.getId()).orElseThrow(() ->
                new IamException(HttpStatus.INTERNAL_SERVER_ERROR, "The Parking Admin role assignment was not created."));
        audits.save(new AuditEntry(accounts.getReferenceById(actor.getAccountId()),
                "PARKING_ADMIN_PROVISIONED " + account.getId(), "SUCCESS", source, clock.instant()));
        return new ProvisionedParkingAdmin(account, assignment, profile);
    }
    public Page<AuditEntry> audit(SpotgoUserPrincipal actor, UUID accountId, int page, int size) {
        requireSuper(actor);
        var paging = page(page, size);
        return accountId == null ? audits.findAll(paging) : audits.findAllByAccountId(accountId, paging);
    }
    public Instant now() { return clock.instant(); }
    private Account account(UUID id) { return accounts.findById(id).orElseThrow(() -> notFound()); }
    private static IamException notFound() { return new IamException(HttpStatus.NOT_FOUND, "Account not found."); }
    private static void requireSuper(SpotgoUserPrincipal actor) {
        if (actor == null || actor.getRole() != UserRole.SUPER_ADMIN)
            throw new IamException(HttpStatus.FORBIDDEN, "Only SUPER_ADMIN can manage accounts, roles, and audit records.");
    }
    private static Pageable page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page must be at least 0 and size must be between 1 and 100.");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }
}
