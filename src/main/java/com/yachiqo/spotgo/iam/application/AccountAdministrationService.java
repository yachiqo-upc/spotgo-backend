package com.yachiqo.spotgo.iam.application;

import com.yachiqo.spotgo.iam.domain.model.aggregates.*;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.*;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.TenantRepository;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AccountAdministrationService {
    private final AccountRepository accounts;
    private final RoleAssignmentRepository roles;
    private final SessionRepository sessions;
    private final AuditEntryRepository audits;
    private final TenantRepository parkings;
    private final Clock clock;
    public AccountAdministrationService(AccountRepository accounts, RoleAssignmentRepository roles,
            SessionRepository sessions, AuditEntryRepository audits, TenantRepository parkings, Clock clock) {
        this.accounts = accounts; this.roles = roles; this.sessions = sessions; this.audits = audits;
        this.parkings = parkings; this.clock = clock;
    }
    public Account own(SpotgoUserPrincipal actor) { return account(actor.getAccountId()); }
    public Page<Account> list(SpotgoUserPrincipal actor, int page, int size) {
        requireSuper(actor); return accounts.findAll(page(page, size));
    }
    public Account get(SpotgoUserPrincipal actor, UUID id) { requireSuper(actor); return account(id); }
    public RoleAssignment role(SpotgoUserPrincipal actor, UUID id) {
        if (!actor.getAccountId().equals(id)) requireSuper(actor);
        account(id);
        return roles.findByAccountId(id).orElseThrow(() -> new IamException(HttpStatus.NOT_FOUND, "Cuenta sin asignacion de rol"));
    }
    @Transactional
    public Account status(SpotgoUserPrincipal actor, UUID id, AccountStatus status, String source) {
        requireSuper(actor);
        var account = accounts.findByIdForUpdate(id).orElseThrow(() -> notFound());
        account.changeStatus(status);
        if (status != AccountStatus.ACTIVE) sessions.revokeAll(id, clock.instant(), SessionStatus.ACTIVE, SessionStatus.REVOKED);
        audits.save(new AuditEntry(accounts.getReferenceById(actor.getAccountId()), "ACCOUNT_STATUS_CHANGE " + id, "SUCCESS", source, clock.instant()));
        return accounts.saveAndFlush(account);
    }
    @Transactional
    public RoleAssignment assign(SpotgoUserPrincipal actor, UUID id, UserRole role, UUID tenantId,
                                  Instant from, Instant to, AssignmentStatus status, String source) {
        requireSuper(actor);
        var account = accounts.findByIdForUpdate(id).orElseThrow(() -> notFound());
        if (role == UserRole.PARKING_ADMIN && (tenantId == null || !parkings.existsById(tenantId)))
            throw new IamException(HttpStatus.BAD_REQUEST, "PARKING_ADMIN requiere un estacionamiento existente");
        Instant start = from == null ? clock.instant() : from;
        AssignmentStatus enabled = status == null ? AssignmentStatus.ACTIVE : status;
        var assignment = roles.findByAccountId(id).orElseGet(() -> new RoleAssignment(account, role, tenantId, start, to, enabled));
        assignment.assign(role, tenantId, start, to, enabled);
        audits.save(new AuditEntry(accounts.getReferenceById(actor.getAccountId()), "ROLE_ASSIGNMENT_CHANGE " + id, "SUCCESS", source, clock.instant()));
        return roles.saveAndFlush(assignment);
    }
    public Page<AuditEntry> audit(SpotgoUserPrincipal actor, UUID accountId, int page, int size) {
        requireSuper(actor);
        var paging = page(page, size);
        return accountId == null ? audits.findAll(paging) : audits.findAllByAccountId(accountId, paging);
    }
    public Instant now() { return clock.instant(); }
    private Account account(UUID id) { return accounts.findById(id).orElseThrow(() -> notFound()); }
    private static IamException notFound() { return new IamException(HttpStatus.NOT_FOUND, "Cuenta inexistente"); }
    private static void requireSuper(SpotgoUserPrincipal actor) {
        if (actor == null || actor.getRole() != UserRole.SUPER_ADMIN)
            throw new IamException(HttpStatus.FORBIDDEN, "Solo SUPER_ADMIN administra cuentas, roles y auditoria");
    }
    private static Pageable page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page >= 0 y size entre 1 y 100");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }
}
