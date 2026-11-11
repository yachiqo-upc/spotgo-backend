package com.yachiqo.spotgo.profiles.application;

import com.yachiqo.spotgo.iam.domain.model.aggregates.Account;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.AccountRepository;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.RoleAssignmentRepository;
import com.yachiqo.spotgo.iam.domain.model.aggregates.RoleAssignment;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.AssignmentStatus;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;

import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.TenantRepository;
import com.yachiqo.spotgo.profiles.application.internal.ProfilesManagementService;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.*;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.DriverProfileType;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.*;
import com.yachiqo.spotgo.shared.application.events.SpotgoDomainEventPublisher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.time.Clock;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfilesManagementServiceTest {
    @Mock ProfileRepository profiles;
    @Mock DriverClassificationRepository classifications;
    @Mock AccountRepository accounts;
    @Mock TenantRepository parkings;
    @Mock RoleAssignmentRepository roles;
    @Mock SpotgoDomainEventPublisher domainEvents;
    ProfilesManagementService service;
    UUID accountId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    @BeforeEach void setup() { service = new ProfilesManagementService(profiles, classifications, accounts, parkings, roles, Clock.systemUTC(), domainEvents); }
    SpotgoUserPrincipal actor(UserRole role) {
        return new SpotgoUserPrincipal(accountId, "test@example.com", role, role == UserRole.PARKING_ADMIN ? tenantId : null, null);
    }

    @Test void createsOnlyForAuthenticatedAccountAndRejectsDuplicates() {
        when(accounts.existsById(accountId)).thenReturn(true);
        when(profiles.saveAndFlush(any(Profile.class))).thenAnswer(i -> {
            var profile = (Profile) i.getArgument(0);
            ReflectionTestUtils.setField(profile, "id", UUID.randomUUID());
            return profile;
        });
        assertEquals(accountId, service.create(actor(UserRole.DRIVER), "Juan", "Perez", "999").getAccountId());
        when(profiles.existsByAccountId(accountId)).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class, () ->
            service.create(actor(UserRole.DRIVER), "Juan", "Perez", "999")).getStatusCode().value());
    }

    @Test void requiresProfileBeforeUsingVehicles() {
        assertEquals(404, assertThrows(ResponseStatusException.class, () ->
            service.driver(actor(UserRole.DRIVER))).getStatusCode().value());
    }

    @Test void refusesOtherOwnersVehicle() {
        var owner = new Profile(UUID.randomUUID(), "Otro", "Dueno", "999");
        var vehicle = new Vehicle(owner, "ABC", "Kia", "Rio", "Blue");
        assertThrows(AccessDeniedException.class, () -> service.requireOwner(actor(UserRole.DRIVER), vehicle));
    }

    @Test void driverCannotAssignTheirOwnClassification() {
        assertThrows(AccessDeniedException.class, () -> service.assign(actor(UserRole.DRIVER),
            UUID.randomUUID(), UUID.randomUUID(), DriverProfileType.EMPLOYEE));
        verifyNoInteractions(classifications);
    }

    @Test void administratorCannotModifyAnotherTenant() {
        UUID tenant = UUID.randomUUID();
        assertThrows(AccessDeniedException.class, () -> service.assign(actor(UserRole.PARKING_ADMIN), tenant,
            UUID.randomUUID(), DriverProfileType.EMPLOYEE));
        verifyNoInteractions(classifications);
    }

    @Test void assignsDefaultPrivateAndRecordsAdministratorsProfile() {
        UUID tenant = tenantId, driverId = UUID.randomUUID();
        when(parkings.existsById(tenant)).thenReturn(true);
        var admin = new Profile(accountId, "Ana", "Ruiz", "999");
        UUID adminProfileId = UUID.randomUUID();
        ReflectionTestUtils.setField(admin, "id", adminProfileId);
        when(profiles.findByAccountId(accountId)).thenReturn(Optional.of(admin));
        var driver = new Profile(UUID.randomUUID(), "Juan", "Perez", "888");
        when(profiles.findById(driverId)).thenReturn(Optional.of(driver));
        var account = new Account("juan@example.com");
        ReflectionTestUtils.setField(account, "id", driver.getAccountId());
        when(accounts.findById(driver.getAccountId())).thenReturn(Optional.of(account));
        when(roles.findByAccountId(account.getId())).thenReturn(Optional.of(new RoleAssignment(
            account, UserRole.DRIVER, null, Instant.EPOCH, null, AssignmentStatus.ACTIVE)));
        when(classifications.saveAndFlush(any())).thenAnswer(i -> {
            var classification = (DriverClassification) i.getArgument(0);
            ReflectionTestUtils.setField(classification, "id", UUID.randomUUID());
            return classification;
        });
        var result = service.assign(actor(UserRole.PARKING_ADMIN), tenant, driverId, null);
        assertEquals(DriverProfileType.PRIVATE, result.getProfileType());
        assertEquals(adminProfileId, result.getAssignedById());
    }

    @Test void requiresAuthenticatedAccount() {
        assertThrows(AccessDeniedException.class, () -> service.current(null));
    }
}
