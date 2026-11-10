package com.yachiqo.spotgo.iam.application;

import com.yachiqo.spotgo.iam.domain.model.aggregates.Account;
import com.yachiqo.spotgo.iam.domain.model.aggregates.Credential;
import com.yachiqo.spotgo.iam.domain.model.aggregates.RoleAssignment;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.AssignmentStatus;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.AccountRepository;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.CredentialRepository;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.RoleAssignmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Creates the first production administrator once, then leaves account management to the application. */
@Component
@Profile("production")
public class FirstSuperAdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(FirstSuperAdminBootstrap.class);

    private final AccountRepository accounts;
    private final CredentialRepository credentials;
    private final RoleAssignmentRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String email;
    private final String password;

    public FirstSuperAdminBootstrap(
            AccountRepository accounts,
            CredentialRepository credentials,
            RoleAssignmentRepository roles,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${app.bootstrap.super-admin.email:}") String email,
            @Value("${app.bootstrap.super-admin.password:}") String password) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (roles.existsByRoleType(UserRole.SUPER_ADMIN)) return;
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "No SUPER_ADMIN exists. Set SUPERADMIN_BOOTSTRAP_EMAIL and SUPERADMIN_BOOTSTRAP_PASSWORD for the first production startup.");
        }

        String normalizedEmail = Account.normalizeEmail(email);
        if (!normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalStateException("SUPERADMIN_BOOTSTRAP_EMAIL must be a valid email address.");
        }
        PasswordPolicy.validate(password);
        if (accounts.existsByEmail(normalizedEmail)) {
            throw new IllegalStateException(
                    "The bootstrap email already belongs to an account. Use an unused email for the first SUPER_ADMIN.");
        }

        var account = accounts.saveAndFlush(new Account(normalizedEmail));
        credentials.saveAndFlush(Credential.password(account, passwordEncoder.encode(password)));
        roles.saveAndFlush(new RoleAssignment(account, UserRole.SUPER_ADMIN, null,
                clock.instant(), null, AssignmentStatus.ACTIVE));
        log.info("Created the first SUPER_ADMIN account from the one-time bootstrap configuration.");
    }
}
