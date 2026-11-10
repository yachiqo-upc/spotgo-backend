package com.yachiqo.spotgo.profiles.domain.model.aggregates;

import com.yachiqo.spotgo.profiles.domain.model.valueobjects.*;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises real ORM mappings and database constraints without the development PostgreSQL database. */
class ProfilesPersistenceTest {
    private SessionFactory factory;

    @BeforeEach void setup() {
        factory = new Configuration().addAnnotatedClass(Profile.class).addAnnotatedClass(Vehicle.class)
            .addAnnotatedClass(com.yachiqo.spotgo.iam.domain.model.aggregates.UserAccount.class)
            .addAnnotatedClass(DriverClassification.class)
            .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
            .setProperty("hibernate.connection.url", "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL")
            .setProperty("hibernate.hbm2ddl.auto", "create-drop")
            .setProperty("hibernate.show_sql", "false")
            .buildSessionFactory();
    }
    @AfterEach void close() { if (factory != null) factory.close(); }

    private Profile profile(UUID accountId, String firstName, String lastName, String phone) {
        factory.inTransaction(session -> {
            if (session.find(com.yachiqo.spotgo.iam.domain.model.aggregates.UserAccount.class, accountId) == null) {
                session.doWork(connection -> {
                    try (var statement = connection.prepareStatement("INSERT INTO accounts(account_id,email,status,created_at,updated_at) VALUES (?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
                        statement.setObject(1, accountId);
                        statement.setString(2, accountId + "@example.com");
                        statement.executeUpdate();
                    }
                });
            }
        });
        return new Profile(accountId, firstName, lastName, phone);
    }

    @Test void generatesUuidAndDatesAndLoadsThroughEmptyConstructor() {
        var profile = profile(UUID.randomUUID(), " Juan ", " Perez ", "999888777");
        factory.inTransaction(s -> s.persist(profile));
        assertNotNull(profile.getId());
        assertNotNull(profile.getCreatedAt());
        assertEquals(profile.getCreatedAt(), profile.getUpdatedAt());
        factory.inTransaction(s -> {
            var loaded = s.find(Profile.class, profile.getId());
            assertEquals("Juan", loaded.getFirstName());
            loaded.update("Ana", "Perez", "999888777");
        });
        factory.inTransaction(s -> {
            var loaded = s.find(Profile.class, profile.getId());
            assertEquals("Ana", loaded.getFirstName());
            assertEquals(profile.getCreatedAt().toEpochMilli(), loaded.getCreatedAt().toEpochMilli());
            assertFalse(loaded.getUpdatedAt().isBefore(loaded.getCreatedAt()));
        });
    }

    @Test void accountCannotHaveTwoProfiles() {
        UUID account = UUID.randomUUID();
        factory.inTransaction(s -> s.persist(profile(account, "Juan", "Perez", "999")));
        assertThrows(RuntimeException.class, () -> factory.inTransaction(s ->
            s.persist(profile(account, "Ana", "Ruiz", "888"))));
    }

    @Test void multipleVehiclesBelongToOneProfileAndPlateIsUniqueWhenPresent() {
        var profile = profile(UUID.randomUUID(), "Juan", "Perez", "999");
        var first = new Vehicle(profile, " abc-123 ", "Toyota", "Yaris", "Azul");
        factory.inTransaction(s -> {
            s.persist(profile);
            s.persist(first);
            s.persist(new Vehicle(profile, "DEF-456", "Kia", "Rio", "Rojo"));
            s.persist(new Vehicle(profile, null, "Kia", "Rio", "Rojo"));
            s.persist(new Vehicle(profile, null, "Kia", "Rio", "Rojo"));
        });
        assertEquals("ABC-123", first.getPlate());
        assertEquals(profile.getId(), first.getProfileId());
        assertNotNull(first.getCreatedAt());
        assertThrows(RuntimeException.class, () -> factory.inTransaction(s ->
            s.persist(new Vehicle(s.find(Profile.class, profile.getId()), "abc-123", "Kia", "Rio", "Rojo"))));
    }

    @Test void classificationIsUniquePerPairAndDefaultsToPrivate() {
        var driver = profile(UUID.randomUUID(), "Juan", "Perez", "999");
        var other = profile(UUID.randomUUID(), "Luis", "Ruiz", "888");
        var admin = profile(UUID.randomUUID(), "Ana", "Ruiz", "777");
        UUID tenant = UUID.randomUUID();
        var classification = new DriverClassification(driver, tenant, null, admin);
        factory.inTransaction(s -> {
            s.persist(driver); s.persist(other); s.persist(admin);
            s.persist(classification);
            s.persist(new DriverClassification(other, tenant, DriverProfileType.TAXI, admin));
            s.persist(new DriverClassification(driver, UUID.randomUUID(), DriverProfileType.EMPLOYEE, admin));
        });
        assertEquals(DriverProfileType.PRIVATE, classification.getProfileType());
        assertEquals(admin.getId(), classification.getAssignedById());
        assertNotNull(classification.getCreatedAt());
        assertThrows(RuntimeException.class, () -> factory.inTransaction(s -> s.persist(
            new DriverClassification(s.find(Profile.class, driver.getId()), tenant,
                DriverProfileType.TAXI, s.find(Profile.class, admin.getId())))));
    }

    @Test void refusesToDeleteAProfileReferencedByVehicles() {
        var profile = profile(UUID.randomUUID(), "Juan", "Perez", "999");
        factory.inTransaction(s -> {
            s.persist(profile);
            s.persist(new Vehicle(profile, "ABC-123", "Kia", "Rio", "Rojo"));
        });
        assertThrows(RuntimeException.class, () -> factory.inTransaction(s ->
            s.remove(s.find(Profile.class, profile.getId()))));
    }

    @Test void rejectsBlankNamesAndVehicleDescriptions() {
        assertThrows(IllegalArgumentException.class, () -> profile(UUID.randomUUID(), "  ", "Perez", "999"));
        var profile = profile(UUID.randomUUID(), "Juan", "Perez", "999");
        assertThrows(IllegalArgumentException.class, () -> new Vehicle(profile, "ABC", " ", "Rio", "Rojo"));
    }
}
