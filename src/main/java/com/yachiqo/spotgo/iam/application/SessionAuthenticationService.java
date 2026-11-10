package com.yachiqo.spotgo.iam.application;

import com.yachiqo.spotgo.iam.domain.model.aggregates.UserAccount;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

@Service
public class SessionAuthenticationService {
    private final AccessSessionRepository sessions;
    private final RoleAssignmentRepository roles;
    private final Clock clock;
    public SessionAuthenticationService(AccessSessionRepository sessions, RoleAssignmentRepository roles, Clock clock) {
        this.sessions = sessions; this.roles = roles; this.clock = clock;
    }
    @Transactional
    public SpotgoUserPrincipal authenticate(JwtTokenService.AccessClaims claims) {
        var session = sessions.findById(claims.sessionId()).orElseThrow(() -> unauthorized());
        if (!session.getAccountId().equals(claims.accountId()) || !session.isUsableAt(clock.instant()) || !session.getAccount().isActive())
            throw unauthorized();
        session.touch(clock.instant());
        return principal(session.getAccount(), session.getId());
    }
    public SpotgoUserPrincipal principal(UserAccount account, UUID sessionId) {
        var role = roles.findByAccountId(account.getId()).filter(r -> r.isEffectiveAt(clock.instant())).orElse(null);
        return new SpotgoUserPrincipal(account.getId(), account.getEmail(),
            role == null ? null : role.getRoleType(), role == null ? null : role.getTenantId(), sessionId);
    }
    private static IamException unauthorized() { return new IamException(HttpStatus.UNAUTHORIZED, "Sesion invalida, revocada o vencida"); }
}
