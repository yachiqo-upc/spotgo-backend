package com.yachiqo.spotgo.iam.application;

import com.yachiqo.spotgo.iam.domain.model.aggregates.*;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.*;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.iam.interfaces.rest.resources.AuthenticatedUserResource;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(noRollbackFor = IamException.class)
public class AuthenticationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AccountRepository accounts;
    private final CredentialRepository credentials;
    private final RoleAssignmentRepository roles;
    private final SessionRepository sessions;
    private final AuditEntryRepository audits;
    private final PasswordResetCodeRepository resets;
    private final PasswordEncoder encoder;
    private final JwtTokenService tokens;
    private final PasswordResetNotificationService notifications;
    private final SessionAuthenticationService authentication;
    private final Clock clock;
    private final int resetMinutes;
    private final int maxResetAttempts;
    private final int refreshDays;

    public AuthenticationService(AccountRepository accounts, CredentialRepository credentials, RoleAssignmentRepository roles,
            SessionRepository sessions, AuditEntryRepository audits, PasswordResetCodeRepository resets,
            PasswordEncoder encoder, JwtTokenService tokens, PasswordResetNotificationService notifications,
            SessionAuthenticationService authentication, Clock clock,
            @Value("${app.password-reset.code-expiration-minutes:15}") int resetMinutes,
            @Value("${app.password-reset.max-attempts:5}") int maxResetAttempts,
            @Value("${authorization.jwt.refresh-expiration-days:7}") int refreshDays) {
        if (resetMinutes <= 0 || maxResetAttempts <= 0 || refreshDays <= 0) throw new IllegalArgumentException("Duraciones de IAM invalidas");
        this.accounts = accounts; this.credentials = credentials; this.roles = roles; this.sessions = sessions;
        this.audits = audits; this.resets = resets; this.encoder = encoder; this.tokens = tokens;
        this.notifications = notifications; this.authentication = authentication; this.clock = clock;
        this.resetMinutes = resetMinutes; this.maxResetAttempts = maxResetAttempts; this.refreshDays = refreshDays;
    }

    public AuthenticatedUserResource signUp(String email, String password, String source) {
        String normalized = Account.normalizeEmail(email);
        PasswordPolicy.validate(password);
        if (accounts.existsByEmail(normalized)) {
            audit(null, "SIGN_UP", "FAILURE", source);
            throw new IamException(HttpStatus.CONFLICT, "El correo ya esta registrado");
        }
        var account = accounts.saveAndFlush(new Account(normalized));
        credentials.save(Credential.password(account, encoder.encode(password)));
        roles.save(new RoleAssignment(account, UserRole.DRIVER, null, clock.instant(), null, AssignmentStatus.ACTIVE));
        var result = issueSession(account);
        audit(account, "SIGN_UP", "SUCCESS", source);
        return result;
    }

    public AuthenticatedUserResource signIn(String email, String password, String source) {
        var account = accounts.findByEmail(Account.normalizeEmail(email)).orElse(null);
        if (account == null || !account.isActive()) {
            audit(account, "SIGN_IN", "FAILURE", source); throw invalidCredentials();
        }
        var credential = passwordCredentials(account.getId()).stream()
            .filter(c -> encoder.matches(password, c.getSecretHash())).findFirst().orElse(null);
        if (credential == null) { audit(account, "SIGN_IN", "FAILURE", source); throw invalidCredentials(); }
        credential.usedAt(clock.instant());
        credentials.save(credential);
        var result = issueSession(account);
        audit(account, "SIGN_IN", "SUCCESS", source);
        return result;
    }

    public AuthenticatedUserResource refresh(String refreshToken, String source) {
        String presentedHash = hashRefreshToken(refreshToken);
        var session = sessions.findByRefreshTokenHash(presentedHash).orElse(null);
        if (session == null || !session.getRefreshTokenHash().equals(presentedHash) || !session.isUsableAt(clock.instant()) || !session.getAccount().isActive()) {
            audit(session == null ? null : session.getAccount(), "TOKEN_REFRESH", "FAILURE", source);
            throw new IamException(HttpStatus.UNAUTHORIZED, "Refresh token invalido, utilizado, revocado o vencido");
        }
        String replacement = newRefreshToken();
        session.rotate(hashRefreshToken(replacement), clock.instant());
        sessions.saveAndFlush(session);
        audit(session.getAccount(), "TOKEN_REFRESH", "SUCCESS", source);
        return response(session.getAccount(), session, replacement);
    }

    public void signOut(SpotgoUserPrincipal actor, String source) {
        revokeSession(actor, actor.getSessionId(), source);
    }

    public void revokeSession(SpotgoUserPrincipal actor, UUID sessionId, String source) {
        var session = sessions.findById(sessionId)
            .orElseThrow(() -> new IamException(HttpStatus.NOT_FOUND, "Sesion inexistente"));
        if (!session.getAccountId().equals(actor.getAccountId()))
            throw new IamException(HttpStatus.FORBIDDEN, "Solo puedes revocar tus propias sesiones");
        session.revoke(clock.instant());
        sessions.save(session);
        audit(session.getAccount(), "SIGN_OUT", "SUCCESS", source);
    }

    @Transactional(readOnly = true)
    public List<Session> ownSessions(SpotgoUserPrincipal actor) {
        return sessions.findAllByAccountIdOrderByCreatedAtDesc(actor.getAccountId());
    }

    public void updatePassword(SpotgoUserPrincipal actor, String currentPassword, String newPassword, String source) {
        var account = lockedAccount(actor.getAccountId());
        var local = passwordCredentials(account.getId());
        if (local.stream().noneMatch(c -> encoder.matches(currentPassword, c.getSecretHash()))) {
            audit(account, "PASSWORD_CHANGE", "FAILURE", source);
            throw new IamException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta");
        }
        rotatePassword(account, local, newPassword);
        resets.findByEmail(account.getEmail()).ifPresent(reset -> {
            reset.markUsed(clock.instant()); resets.save(reset);
        });
        audit(account, "PASSWORD_CHANGE", "SUCCESS", source);
    }

    public void requestPasswordReset(String email, String source) {
        var found = accounts.findByEmail(Account.normalizeEmail(email)).filter(Account::isActive).orElse(null);
        if (found == null) { audit(null, "PASSWORD_RESET_REQUEST", "SUCCESS", source); return; }
        var account = lockedAccount(found.getId());
        if (passwordCredentials(account.getId()).isEmpty()) { audit(null, "PASSWORD_RESET_REQUEST", "SUCCESS", source); return; }
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        Instant now = clock.instant();
        String hash = encoder.encode(code);
        var reset = resets.findByEmail(account.getEmail()).orElseGet(() ->
            new PasswordResetCode(account.getEmail(), hash, now.plusSeconds(resetMinutes * 60L), now));
        reset.renew(hash, now.plusSeconds(resetMinutes * 60L), now);
        resets.saveAndFlush(reset);
        try { notifications.sendPasswordResetCode(account.getEmail(), code); }
        catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Servicio de correo no disponible; revisar configuracion de Resend", ex);
        }
        audit(account, "PASSWORD_RESET_REQUEST", "SUCCESS", source);
    }

    public void confirmPasswordReset(String email, String code, String newPassword, String source) {
        PasswordPolicy.validate(newPassword);
        var found = accounts.findByEmail(Account.normalizeEmail(email)).orElse(null);
        if (found == null) { audit(null, "PASSWORD_RESET", "FAILURE", source); throw invalidReset(); }
        var account = lockedAccount(found.getId());
        var reset = resets.findByEmail(account.getEmail()).orElse(null);
        if (reset == null || reset.isUsed() || reset.isExpiredAt(clock.instant()) || reset.getAttempts() >= maxResetAttempts) {
            audit(account, "PASSWORD_RESET", "FAILURE", source); throw invalidReset();
        }
        if (!encoder.matches(code, reset.getCodeHash())) {
            reset.registerFailedAttempt(); resets.save(reset);
            audit(account, "PASSWORD_RESET", "FAILURE", source); throw invalidReset();
        }
        rotatePassword(account, passwordCredentials(account.getId()), newPassword);
        reset.markUsed(clock.instant()); resets.save(reset);
        audit(account, "PASSWORD_RESET", "SUCCESS", source);
    }

    private void rotatePassword(Account account, List<Credential> local, String password) {
        PasswordPolicy.validate(password);
        if (local.isEmpty()) throw new IamException(HttpStatus.BAD_REQUEST, "La cuenta no tiene una contraseña local");
        if (local.stream().anyMatch(c -> encoder.matches(password, c.getSecretHash())))
            throw new IamException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe ser diferente");
        local.get(0).rotate(encoder.encode(password));
        for (int i = 1; i < local.size(); i++) local.get(i).revoke();
        credentials.saveAll(local);
        sessions.revokeAll(account.getId(), clock.instant(), SessionStatus.ACTIVE, SessionStatus.REVOKED);
    }

    private List<Credential> passwordCredentials(UUID accountId) {
        return credentials.findAllByAccountIdAndAuthenticationMethodAndStatus(accountId, AuthenticationMethod.PASSWORD, CredentialStatus.ACTIVE);
    }
    private Account lockedAccount(UUID id) {
        return accounts.findByIdForUpdate(id).orElseThrow(() -> new IamException(HttpStatus.NOT_FOUND, "Cuenta inexistente"));
    }
    private AuthenticatedUserResource issueSession(Account account) {
        String refresh = newRefreshToken();
        var session = sessions.saveAndFlush(new Session(account, hashRefreshToken(refresh),
            clock.instant(), clock.instant().plusSeconds(refreshDays * 86400L)));
        return response(account, session, refresh);
    }
    private AuthenticatedUserResource response(Account account, Session session, String refresh) {
        var principal = authentication.principal(account, session.getId());
        String access = tokens.generateToken(account, principal.getRole(), session);
        return new AuthenticatedUserResource(account.getId(), account.getEmail(), principal.getRole(), principal.getTenantId(),
            session.getId(), access, refresh, "Bearer", tokens.parseAccessToken(access).expiresAt(), session.getExpiresAt());
    }
    private void audit(Account account, String action, String result, String source) {
        audits.save(new AuditEntry(account, action, result, source, clock.instant()));
    }
    private static String newRefreshToken() {
        byte[] random = new byte[32]; RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }
    public static String hashRefreshToken(String raw) {
        if (raw == null || raw.isBlank()) throw new IamException(HttpStatus.UNAUTHORIZED, "Refresh token requerido");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static IamException invalidCredentials() {
        return new IamException(HttpStatus.UNAUTHORIZED, "Correo o contraseña invalidos, o cuenta no habilitada");
    }
    private static IamException invalidReset() {
        return new IamException(HttpStatus.BAD_REQUEST, "Codigo de recuperacion invalido, utilizado, vencido o bloqueado");
    }
}
