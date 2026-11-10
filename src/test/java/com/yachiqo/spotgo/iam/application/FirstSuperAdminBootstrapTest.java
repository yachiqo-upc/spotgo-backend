package com.yachiqo.spotgo.iam.application;

import com.yachiqo.spotgo.iam.domain.model.aggregates.Account;
import com.yachiqo.spotgo.iam.domain.model.aggregates.Credential;
import com.yachiqo.spotgo.iam.domain.model.aggregates.RoleAssignment;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.AccountRepository;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.CredentialRepository;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.RoleAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirstSuperAdminBootstrapTest {
    @Mock AccountRepository accounts;
    @Mock CredentialRepository credentials;
    @Mock RoleAssignmentRepository roles;
    @Mock PasswordEncoder passwordEncoder;

    @Test
    void createsTheFirstSuperAdminOnlyWhenNoSuperAdminExists() {
        when(accounts.existsByEmail("admin@example.com")).thenReturn(false);
        when(accounts.saveAndFlush(any(Account.class))).thenAnswer(call -> call.getArgument(0));
        when(passwordEncoder.encode("SecureAdmin123!")).thenReturn("encoded-password");
        var bootstrap = new FirstSuperAdminBootstrap(accounts, credentials, roles, passwordEncoder,
                Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC),
                " Admin@Example.com ", "SecureAdmin123!");

        bootstrap.run(null);

        ArgumentCaptor<Account> account = ArgumentCaptor.forClass(Account.class);
        verify(accounts).saveAndFlush(account.capture());
        assertEquals("admin@example.com", account.getValue().getEmail());
        ArgumentCaptor<Credential> credential = ArgumentCaptor.forClass(Credential.class);
        verify(credentials).saveAndFlush(credential.capture());
        assertEquals("encoded-password", credential.getValue().getSecretHash());
        ArgumentCaptor<RoleAssignment> assignment = ArgumentCaptor.forClass(RoleAssignment.class);
        verify(roles).saveAndFlush(assignment.capture());
        assertEquals(UserRole.SUPER_ADMIN, assignment.getValue().getRoleType());
    }

    @Test
    void doesNotResetOrRecreateAnExistingSuperAdmin() {
        when(roles.existsByRoleType(UserRole.SUPER_ADMIN)).thenReturn(true);
        var bootstrap = new FirstSuperAdminBootstrap(accounts, credentials, roles, passwordEncoder,
                Clock.systemUTC(), "admin@example.com", "SecureAdmin123!");

        assertDoesNotThrow(() -> bootstrap.run(null));

        verifyNoInteractions(accounts, credentials, passwordEncoder);
        verify(roles, never()).saveAndFlush(any());
    }

    @Test
    void failsClearlyWhenAnEmptyProductionDatabaseHasNoBootstrapCredentials() {
        var bootstrap = new FirstSuperAdminBootstrap(accounts, credentials, roles, passwordEncoder,
                Clock.systemUTC(), "", "");

        var error = assertThrows(IllegalStateException.class, () -> bootstrap.run(null));

        assertTrue(error.getMessage().contains("SUPERADMIN_BOOTSTRAP_EMAIL"));
        verifyNoInteractions(accounts, credentials, passwordEncoder);
    }
}
