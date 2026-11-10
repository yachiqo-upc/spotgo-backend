package com.yachiqo.spotgo.iam.application;

import com.yachiqo.spotgo.iam.domain.model.aggregates.*;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.*;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.iam.interfaces.rest.resources.AuthenticatedUserResource;
import com.yachiqo.spotgo.profiles.application.internal.ProfilesManagementService;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.ProfileRepository;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.DriverProfileType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
    "spring.datasource.url=jdbc:h2:mem:iam-integration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=VALUE,HOUR,ROWNUM,DAY")
@ActiveProfiles("test")
class AuthenticationServiceTest {
    @Autowired AuthenticationService service;
    @Autowired AccountAdministrationService administration;
    @Autowired SessionAuthenticationService authentication;
    @Autowired AccountRepository accounts;
    @Autowired CredentialRepository credentials;
    @Autowired RoleAssignmentRepository roles;
    @Autowired SessionRepository sessions;
    @Autowired AuditEntryRepository audits;
    @Autowired PasswordResetCodeRepository resets;
    @Autowired ProfileRepository profiles;
    @Autowired ProfilesManagementService profileService;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @MockitoBean PasswordResetNotificationService notifications;
    @Value("${local.server.port}") int port;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void clean() {
        for (String table : List.of("parking_sessions", "guest_parking_sessions", "reservations", "digital_parking_maps", "floor_plans", "parking_spots", "parking_zones", "driver_classifications", "vehicles", "profiles", "audit_entries", "sessions",
                "credentials", "role_assignments", "passwordResetCodes", "accounts", "tenants"))
            jdbc.update("DELETE FROM " + table);
        reset(notifications);
    }
    private AuthenticatedUserResource register(String name) { return service.signUp(name + "@example.com", "Password123!", "TEST"); }
    private SpotgoUserPrincipal principal(AuthenticatedUserResource auth) { return authentication.authenticate(tokens.parseAccessToken(auth.accessToken())); }
    private HttpResponse<String> request(String method, String path, String body, String access) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json");
        if (access != null) builder.header("Authorization", "Bearer " + access);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newHttpClient()) { return client.send(builder.build(), HttpResponse.BodyHandlers.ofString()); }
    }
    private AuthenticatedUserResource superUser() {
        var auth = register("super");
        var role = roles.findByAccountId(auth.accountId()).orElseThrow();
        role.assign(UserRole.SUPER_ADMIN, null, Instant.EPOCH, null, AssignmentStatus.ACTIVE);
        roles.saveAndFlush(role);
        return auth;
    }
    private UUID parking(UUID adminId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants(tenant_id,name,status,address,coordinates,contact_data,rules,created_at,updated_at) VALUES (?,?,'ACTIVE',?,'{}','{}','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, "Demo", "Av Demo");
        return id;
    }

    @Autowired org.springframework.context.ApplicationContext context;

    @Test void onlyTheConfiguredAccountAndSessionAuthenticationIsAvailable() {
        assertTrue(context.getBeansOfType(org.springframework.security.core.userdetails.UserDetailsService.class).isEmpty());
        assertNotNull(context.getBean(AuthenticationService.class));
        assertNotNull(context.getBean(SessionAuthenticationService.class));
    }
    @Test void registrationSeparatesAccountPasswordRoleAndSessionWithNativeUuids() {
        var auth = register("juan");
        assertEquals(UserRole.DRIVER, auth.role());
        assertNotNull(auth.accountId()); assertNotNull(auth.sessionId());
        assertEquals("juan@example.com", accounts.findById(auth.accountId()).orElseThrow().getEmail());
        assertFalse(profiles.existsByAccountId(auth.accountId()));
        var credential = credentials.findAllByAccountId(auth.accountId()).get(0);
        assertTrue(encoder.matches("Password123!", credential.getSecretHash()));
        assertNotEquals("Password123!", credential.getSecretHash());
        var session = sessions.findById(auth.sessionId()).orElseThrow();
        assertEquals(AuthenticationService.hashRefreshToken(auth.refreshToken()), session.getRefreshTokenHash());
        assertNotEquals(auth.refreshToken(), session.getRefreshTokenHash());
        assertNull(session.getRevokedAt());
        assertNull(roles.findByAccountId(auth.accountId()).orElseThrow().getTenantId());
        assertEquals(1, audits.count());
    }

    @Test void signInNormalizesEmailAndUpdatesCredentialLastUse() {
        register("juan");
        var auth = service.signIn(" JUAN@EXAMPLE.COM ", "Password123!", "TEST");
        assertEquals(UserRole.DRIVER, auth.role());
        assertNotNull(credentials.findAllByAccountId(auth.accountId()).get(0).getLastUsedAt());
        assertEquals(2, sessions.count());
    }

    @Test void invalidLoginIs401AndItsAuditSurvivesTheRejection() {
        var ex = assertThrows(IamException.class, () -> service.signIn("unknown@example.com", "Wrong123!", "TEST"));
        assertEquals(401, ex.getStatusCode().value());
        var audit = audits.findAll().get(0);
        assertNull(audit.getAccountId()); assertEquals("FAILURE", audit.getResult());
        assertFalse(audit.getSource().contains("Wrong123!"));
    }

    @Test void duplicateEmailIs409() {
        register("juan");
        assertEquals(409, assertThrows(IamException.class, () -> register("juan")).getStatusCode().value());
        assertEquals(1, accounts.count()); assertEquals(1, credentials.count());
    }

    @Test void refreshRotatesTokenKeepsSessionAndRejectsReusingOldToken() {
        var first = register("juan");
        var next = service.refresh(first.refreshToken(), "TEST");
        assertEquals(first.sessionId(), next.sessionId());
        assertEquals(first.refreshExpiresAt(), next.refreshExpiresAt());
        assertNotEquals(first.refreshToken(), next.refreshToken());
        assertEquals(1, sessions.count());
        assertEquals(401, assertThrows(IamException.class, () -> service.refresh(first.refreshToken(), "TEST")).getStatusCode().value());
        assertEquals(UserRole.DRIVER, principal(next).getRole());
    }

    @Test void concurrentRefreshOnlyAcceptsOneUseOfTheToken() throws Exception {
        var auth = register("juan");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> renewal = () -> {
                start.await();
                try { service.refresh(auth.refreshToken(), "TEST"); return true; }
                catch (IamException rejected) { return false; }
            };
            var one = executor.submit(renewal); var two = executor.submit(renewal); start.countDown();
            assertNotEquals(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
        }
    }

    @Test void signOutRejectsBothExistingAccessAndRefreshTokens() throws Exception {
        var auth = register("juan");
        service.signOut(principal(auth), "TEST");
        assertEquals(401, request("GET", "/api/v1/accounts/me", null, auth.accessToken()).statusCode());
        assertThrows(IamException.class, () -> service.refresh(auth.refreshToken(), "TEST"));
        assertNotNull(sessions.findById(auth.sessionId()).orElseThrow().getRevokedAt());
    }

    @Test void expiredSessionRejectsAccessAndRefreshEvenWithSignedJwt() throws Exception {
        var auth = register("juan");
        jdbc.update("UPDATE sessions SET created_at=?, expires_at=? WHERE session_id=?",
            Timestamp.from(clock.instant().minusSeconds(172800)), Timestamp.from(clock.instant().minusSeconds(86400)), auth.sessionId());
        assertEquals(401, request("GET", "/api/v1/accounts/me", null, auth.accessToken()).statusCode());
        assertThrows(IamException.class, () -> service.refresh(auth.refreshToken(), "TEST"));
    }

    @Test void driverCannotListAccountsOrGrantTheirOwnPrivilegedRole() throws Exception {
        var auth = register("juan");
        assertEquals(403, request("GET", "/api/v1/accounts", null, auth.accessToken()).statusCode());
        assertEquals(403, request("PUT", "/api/v1/accounts/" + auth.accountId() + "/role",
            "{\"roleType\":\"SUPER_ADMIN\"}", auth.accessToken()).statusCode());
        assertEquals(UserRole.DRIVER, roles.findByAccountId(auth.accountId()).orElseThrow().getRoleType());
    }

    @Test void roleChangesAreReadFromDatabaseRatherThanTrustingOldJwtClaims() throws Exception {
        var admin = superUser();
        var driver = register("juan");
        assertEquals(200, request("GET", "/api/v1/accounts", null, admin.accessToken()).statusCode());
        UUID tenant = parking(driver.accountId());
        administration.assign(principal(admin), driver.accountId(), UserRole.PARKING_ADMIN, tenant, null, null, null, "TEST");
        assertEquals(UserRole.PARKING_ADMIN, principal(driver).getRole());
        assertEquals(tenant, principal(driver).getTenantId());
        assertEquals(403, request("GET", "/api/v1/vehicles", null, driver.accessToken()).statusCode());
        assertEquals(1, roles.findAll().stream().filter(r -> r.getAccountId().equals(driver.accountId())).count());
    }

    @Test void expiredOrDisabledRoleDoesNotGrantPermissions() throws Exception {
        var auth = register("juan");
        var role = roles.findByAccountId(auth.accountId()).orElseThrow();
        role.assign(UserRole.DRIVER, null, clock.instant().minusSeconds(100), clock.instant().minusSeconds(1), AssignmentStatus.ACTIVE);
        roles.saveAndFlush(role);
        assertNull(principal(auth).getRole());
        assertEquals(403, request("GET", "/api/v1/vehicles", null, auth.accessToken()).statusCode());
        assertEquals(200, request("GET", "/api/v1/accounts/me", null, auth.accessToken()).statusCode());
    }

    @Test void suspendedAccountCannotUseItsTokensOrLogin() throws Exception {
        var admin = superUser(); var user = register("juan");
        administration.status(principal(admin), user.accountId(), AccountStatus.SUSPENDED, "TEST");
        assertEquals(401, request("GET", "/api/v1/accounts/me", null, user.accessToken()).statusCode());
        assertThrows(IamException.class, () -> service.signIn(user.email(), "Password123!", "TEST"));
        assertThrows(IamException.class, () -> service.refresh(user.refreshToken(), "TEST"));
    }

    @Test void changingPasswordRevokesEverySessionAndInvalidatesOldPassword() throws Exception {
        var first = register("juan"); var second = service.signIn(first.email(), "Password123!", "TEST");
        service.updatePassword(principal(first), "Password123!", "NewPassword123!", "TEST");
        assertEquals(401, request("GET", "/api/v1/accounts/me", null, second.accessToken()).statusCode());
        assertThrows(IamException.class, () -> service.signIn(first.email(), "Password123!", "TEST"));
        assertNotNull(service.signIn(first.email(), "NewPassword123!", "TEST").accessToken());
        assertTrue(sessions.findAll().stream().filter(s -> s.getStatus() == SessionStatus.REVOKED).count() >= 2);
    }

    @Test void resetCodeIsHashedFailedAttemptsPersistAndSuccessfulCodeIsSingleUse() {
        var auth = register("juan");
        service.requestPasswordReset(auth.email(), "TEST");
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(notifications).sendPasswordResetCode(eq(auth.email()), captor.capture());
        String code = captor.getValue();
        assertNotEquals(code, resets.findByEmail(auth.email()).orElseThrow().getCodeHash());
        assertThrows(IamException.class, () -> service.confirmPasswordReset(auth.email(), "wrong-code", "NewPassword123!", "TEST"));
        assertEquals(1, resets.findByEmail(auth.email()).orElseThrow().getAttempts());
        service.confirmPasswordReset(auth.email(), code, "NewPassword123!", "TEST");
        assertNotNull(resets.findByEmail(auth.email()).orElseThrow().getUsedAt());
        assertThrows(IamException.class, () -> service.confirmPasswordReset(auth.email(), code, "OtherPassword123!", "TEST"));
        assertThrows(IamException.class, () -> service.refresh(auth.refreshToken(), "TEST"));
        assertNotNull(service.signIn(auth.email(), "NewPassword123!", "TEST").accessToken());
    }

    @Test void unknownResetRequestDoesNotDiscloseAccountOrSendMail() {
        assertDoesNotThrow(() -> service.requestPasswordReset("missing@example.com", "TEST"));
        verifyNoInteractions(notifications);
        assertNull(audits.findAll().get(0).getAccountId());
    }

    @Test void sessionsCannotBeRevokedByAnotherAccountAndDoNotExposeTokenHashes() throws Exception {
        var one = register("juan"); var other = register("ana");
        assertEquals(403, request("DELETE", "/api/v1/sessions/" + other.sessionId(), null, one.accessToken()).statusCode());
        var response = request("GET", "/api/v1/sessions/me", null, one.accessToken());
        assertEquals(200, response.statusCode());
        assertFalse(response.body().contains("refreshTokenHash"));
        assertFalse(response.body().contains(one.refreshToken()));
        assertEquals(one.sessionId().toString(), json.readTree(response.body()).get(0).path("id").asText());
    }

    @Test void accountAndProfileAreLinkedByAForeignKeyAndClassificationUsesCurrentRoleScope() {
        var driver = register("juan"); var admin = superUser(); var parkingAdmin = register("ana");
        UUID tenant = parking(parkingAdmin.accountId());
        administration.assign(principal(admin), parkingAdmin.accountId(), UserRole.PARKING_ADMIN, tenant, null, null, null, "TEST");
        var driverProfile = profileService.create(principal(driver), "Juan", "Demo", "999");
        var adminProfile = profileService.create(principal(parkingAdmin), "Ana", "Demo", "888");
        var classification = profileService.assign(principal(parkingAdmin), tenant, driverProfile.getId(), null);
        assertEquals(DriverProfileType.PRIVATE, classification.getProfileType());
        assertEquals(adminProfile.getId(), classification.getAssignedById());
    }

    @Test void databaseEnforcesUniqueAccountRoleAndCredentialMethodRequirements() {
        var auth = register("juan");
        var account = accounts.findById(auth.accountId()).orElseThrow();
        assertThrows(DataIntegrityViolationException.class, () -> roles.saveAndFlush(
            new RoleAssignment(account, UserRole.SUPER_ADMIN, null, Instant.EPOCH, null, AssignmentStatus.ACTIVE)));
        var google = credentials.saveAndFlush(Credential.google(account, "verified-google-subject-demo"));
        assertNull(google.getSecretHash()); assertNotNull(google.getExternalSubject());
        var invalid = Credential.password(account, encoder.encode("Password123!"));
        ReflectionTestUtils.setField(invalid, "secretHash", null);
        assertThrows(DataIntegrityViolationException.class, () -> credentials.saveAndFlush(invalid));
    }

    @Test void auditIsRestrictedToSuperAdminAndContainsSecurityEventsWithoutPasswords() throws Exception {
        var driver = register("juan"); var admin = superUser();
        assertEquals(403, request("GET", "/api/v1/audit-entries", null, driver.accessToken()).statusCode());
        var response = request("GET", "/api/v1/audit-entries", null, admin.accessToken());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("SIGN_UP"));
        assertFalse(response.body().contains("Password123!"));
        assertFalse(response.body().contains(driver.refreshToken()));
    }

    @Test void seederUsesTheRealIamTablesAndAllDemoRolesCanSignIn() throws Exception {
        new com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.seeder.DbSeeder(jdbc, encoder).run();
        assertEquals(UserRole.SUPER_ADMIN, service.signIn("super.admin@example.com", "Password123!", "TEST").role());
        assertEquals(UserRole.PARKING_ADMIN, service.signIn("ana.admin@example.com", "Password123!", "TEST").role());
        var driver = service.signIn("juan.driver@example.com", "Password123!", "TEST");
        assertEquals(UserRole.DRIVER, driver.role());
        assertEquals("Juan", profileService.current(principal(driver)).getFirstName());
        assertEquals(4, accounts.count());
        assertEquals(4, roles.count());
    }

    @Test void swaggerDocumentsIamRolesRefreshAndRevocationWithoutTheOldUsersController() throws Exception {
        var response = request("GET", "/v3/api-docs/02-iam-actualizado", null, null);
        assertEquals(200, response.statusCode());
        var document = json.readTree(response.body());
        var paths = document.path("paths");
        assertTrue(paths.has("/api/v1/authentication/refresh"));
        assertTrue(paths.has("/api/v1/accounts/{accountId}/role"));
        assertTrue(paths.has("/api/v1/sessions/me"));
        assertTrue(paths.has("/api/v1/audit-entries"));
        assertFalse(paths.has("/api/v1/users"));
        var signup = document.path("components").path("schemas").path("SignUpResource").path("properties");
        assertTrue(signup.has("email")); assertTrue(signup.has("password")); assertFalse(signup.has("role"));
    }
}
