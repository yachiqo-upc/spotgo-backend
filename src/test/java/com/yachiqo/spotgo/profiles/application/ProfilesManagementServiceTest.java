package com.yachiqo.spotgo.profiles.application;

import com.yachiqo.spotgo.iam.domain.model.aggregates.UserAccount;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.UserAccountRepository;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Parking;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ParkingRepository;
import com.yachiqo.spotgo.profiles.application.internal.ProfilesManagementService;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.*;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.DriverProfileType;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfilesManagementServiceTest {
    @Mock ProfileRepository profiles;
    @Mock DriverClassificationRepository classifications;
    @Mock UserAccountRepository accounts;
    @Mock ParkingRepository parkings;
    ProfilesManagementService service;
    UUID accountId = UUID.randomUUID();
    @BeforeEach void setup() { service = new ProfilesManagementService(profiles, classifications, accounts, parkings); }
    SpotgoUserPrincipal actor(UserRole role) {
        return new SpotgoUserPrincipal(accountId.toString(), "test@example.com", "hash", role, 0L);
    }

    @Test void createsOnlyForAuthenticatedAccountAndRejectsDuplicates() {
        when(accounts.existsById(accountId.toString())).thenReturn(true);
        when(profiles.saveAndFlush(any(Profile.class))).thenAnswer(i -> i.getArgument(0));
        assertEquals(accountId, service.create(actor(UserRole.CLIENT), "Juan", "Perez", "999").getAccountId());
        when(profiles.existsByAccountId(accountId)).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class, () ->
            service.create(actor(UserRole.CLIENT), "Juan", "Perez", "999")).getStatusCode().value());
    }

    @Test void requiresProfileBeforeUsingVehicles() {
        assertEquals(404, assertThrows(ResponseStatusException.class, () ->
            service.driver(actor(UserRole.CLIENT))).getStatusCode().value());
    }

    @Test void refusesOtherOwnersVehicle() {
        var owner = new Profile(UUID.randomUUID(), "Otro", "Dueno", "999");
        var vehicle = new Vehicle(owner, "ABC", "Kia", "Rio", "Azul");
        assertThrows(AccessDeniedException.class, () -> service.requireOwner(actor(UserRole.CLIENT), vehicle));
    }

    @Test void driverCannotAssignTheirOwnClassification() {
        assertThrows(AccessDeniedException.class, () -> service.assign(actor(UserRole.CLIENT),
            UUID.randomUUID(), UUID.randomUUID(), DriverProfileType.EMPLOYEE));
        verifyNoInteractions(classifications);
    }

    @Test void administratorCannotModifyAnotherTenant() {
        UUID tenant = UUID.randomUUID();
        var parking = mock(Parking.class);
        when(parking.getAdminId()).thenReturn(UUID.randomUUID().toString());
        when(parkings.findById(tenant.toString())).thenReturn(Optional.of(parking));
        assertThrows(AccessDeniedException.class, () -> service.assign(actor(UserRole.ADMIN), tenant,
            UUID.randomUUID(), DriverProfileType.EMPLOYEE));
        verifyNoInteractions(classifications);
    }

    @Test void assignsDefaultPrivateAndRecordsAdministratorsProfile() {
        UUID tenant = UUID.randomUUID(), driverId = UUID.randomUUID();
        var parking = mock(Parking.class);
        when(parking.getAdminId()).thenReturn(accountId.toString());
        when(parkings.findById(tenant.toString())).thenReturn(Optional.of(parking));
        var admin = new Profile(accountId, "Ana", "Ruiz", "999");
        UUID adminProfileId = UUID.randomUUID();
        ReflectionTestUtils.setField(admin, "id", adminProfileId);
        when(profiles.findByAccountId(accountId)).thenReturn(Optional.of(admin));
        var driver = new Profile(UUID.randomUUID(), "Juan", "Perez", "888");
        when(profiles.findById(driverId)).thenReturn(Optional.of(driver));
        var account = new UserAccount("Juan", "Perez", "juan@example.com", "hash", "888", UserRole.CLIENT);
        when(accounts.findById(driver.getAccountId().toString())).thenReturn(Optional.of(account));
        when(classifications.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.assign(actor(UserRole.ADMIN), tenant, driverId, null);
        assertEquals(DriverProfileType.PRIVATE, result.getProfileType());
        assertEquals(adminProfileId, result.getAssignedById());
    }

    @Test void refusesLegacyNonUuidAccountsInsteadOfInventingIds() {
        var legacy = new SpotgoUserPrincipal("1", "test@example.com", "hash", UserRole.CLIENT, 0L);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.current(legacy))
            .getStatusCode().value());
    }
}
