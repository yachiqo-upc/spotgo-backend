package com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.seeder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class DbSeederTest {
    private JdbcTemplate jdbc;
    private DbSeeder seeder;
    private JsonNode fixtures;

    @BeforeEach void setup() throws Exception {
        var source = new SimpleDriverDataSource(new org.h2.Driver(),
            "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "sa", "");
        jdbc = new JdbcTemplate(source);
        seeder = new DbSeeder(jdbc, new BCryptPasswordEncoder());
        try (var input = new ClassPathResource("db.json").getInputStream()) {
            fixtures = new ObjectMapper().readTree(input);
        }
    }

    private void currentSchema() {
        jdbc.execute("CREATE TABLE accounts (account_id UUID PRIMARY KEY, email VARCHAR UNIQUE NOT NULL, status VARCHAR NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE credentials (credential_id UUID PRIMARY KEY, account_id UUID NOT NULL REFERENCES accounts(account_id), authentication_method VARCHAR NOT NULL, secret_hash VARCHAR, external_subject VARCHAR, last_used_at TIMESTAMP WITH TIME ZONE, status VARCHAR NOT NULL)");
        jdbc.execute("CREATE TABLE role_assignments (assignment_id UUID PRIMARY KEY, account_id UUID UNIQUE NOT NULL REFERENCES accounts(account_id), role_type VARCHAR NOT NULL, tenant_id UUID, valid_from TIMESTAMP WITH TIME ZONE NOT NULL, valid_to TIMESTAMP WITH TIME ZONE, status VARCHAR NOT NULL)");
        jdbc.execute("CREATE TABLE sessions (session_id UUID PRIMARY KEY, account_id UUID NOT NULL REFERENCES accounts(account_id), refresh_token_hash VARCHAR UNIQUE NOT NULL, last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL, status VARCHAR NOT NULL, revoked_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL, expires_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE audit_entries (audit_id UUID PRIMARY KEY, account_id UUID REFERENCES accounts(account_id), action VARCHAR NOT NULL, result VARCHAR NOT NULL, source VARCHAR NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, correlation_id VARCHAR NOT NULL)");
        jdbc.execute("CREATE TABLE parkings (id VARCHAR(36) PRIMARY KEY, adminId VARCHAR, name VARCHAR NOT NULL, address VARCHAR NOT NULL, city VARCHAR, totalSpaces INTEGER NOT NULL)");
        jdbc.execute("CREATE TABLE profiles (profile_id UUID PRIMARY KEY, account_id UUID UNIQUE NOT NULL, first_name VARCHAR NOT NULL, last_name VARCHAR NOT NULL, phone VARCHAR NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE vehicles (vehicle_id UUID PRIMARY KEY, profile_id UUID NOT NULL REFERENCES profiles(profile_id), plate VARCHAR UNIQUE, make VARCHAR NOT NULL, model VARCHAR NOT NULL, color VARCHAR NOT NULL, status VARCHAR NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE driver_classifications (classification_id UUID PRIMARY KEY, profile_id UUID NOT NULL REFERENCES profiles(profile_id), tenant_id UUID NOT NULL, profile_type VARCHAR NOT NULL, assigned_by UUID NOT NULL REFERENCES profiles(profile_id), created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, UNIQUE(profile_id,tenant_id))");
        // These old tables share names with the new model but their columns are incompatible.
        jdbc.execute("CREATE TABLE reservations (id VARCHAR PRIMARY KEY, clientId VARCHAR NOT NULL)");
        jdbc.execute("CREATE TABLE subscriptions (id VARCHAR PRIMARY KEY, clientId VARCHAR NOT NULL)");
    }

    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    @Test void loadsOnlyCompatibleTablesWithWorkingDemoPasswordsAndUuidRelationships() throws Exception {
        currentSchema();
        seeder.run();
        assertEquals(4, count("accounts"));
        assertEquals(4, count("credentials"));
        assertEquals(4, count("role_assignments"));
        assertEquals(1, count("sessions"));
        assertEquals(2, count("audit_entries"));
        assertEquals(1, count("parkings"));
        assertEquals(3, count("profiles"));
        assertEquals(3, count("vehicles"));
        assertEquals(2, count("driver_classifications"));
        assertEquals(0, count("reservations"));
        assertEquals(0, count("subscriptions"));
        String hash = jdbc.queryForObject("SELECT c.secret_hash FROM credentials c JOIN accounts a ON c.account_id=a.account_id WHERE a.email='juan.driver@example.com'", String.class);
        assertTrue(new BCryptPasswordEncoder().matches("Password123!", hash));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM vehicles v JOIN profiles p ON v.profile_id=p.profile_id WHERE p.first_name='Juan'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM driver_classifications c JOIN profiles p ON c.assigned_by=p.profile_id WHERE p.first_name='Ana'", Integer.class));
    }

    @Test void secondRunDoesNotDuplicateOrOverwriteEditedRecords() throws Exception {
        currentSchema();
        seeder.run();
        jdbc.update("UPDATE profiles SET first_name='Editado' WHERE first_name='Juan'");
        seeder.run();
        assertEquals(3, count("profiles"));
        assertEquals(3, count("vehicles"));
        assertEquals(2, count("driver_classifications"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM profiles WHERE first_name='Editado'", Integer.class));
    }

    @Test void absentTablesAreSkippedWithoutCreatingThem() {
        assertDoesNotThrow(() -> seeder.run());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='PUBLIC'", Integer.class));
    }

    @Test void missingAccountPreventsInsertingOrphanProfilesAndVehicles() throws Exception {
        currentSchema();
        jdbc.execute("DROP TABLE parkings");
        jdbc.execute("DROP TABLE accounts CASCADE");
        seeder.run();
        assertEquals(0, count("profiles"));
        assertEquals(0, count("vehicles"));
        assertEquals(0, count("driver_classifications"));
    }

    @Test void extraRequiredLegacyColumnsCauseSkipInsteadOfInsertFailure() throws Exception {
        currentSchema();
        jdbc.execute("ALTER TABLE profiles ADD old_required VARCHAR NOT NULL");
        seeder.run();
        assertEquals(0, count("profiles"));
        assertEquals(0, count("vehicles"));
    }

    @Test void fixtureColumnsMatchAll31TablesInRootReadme() throws Exception {
        String readme = Files.readString(Path.of("..", "README.md"));
        var sections = Pattern.compile("(?ms)^### ([a-z_]+)\\r?\\n(.*?)(?=^### |^## |\\z)").matcher(readme);
        int checked = 0;
        while (sections.find()) {
            String table = sections.group(1);
            JsonNode rows = fixtures.get(table);
            assertNotNull(rows, table);
            Set<String> columns = new HashSet<>();
            var matches = Pattern.compile("(?m)^\\| `([a-z_]+)` \\|").matcher(sections.group(2));
            while (matches.find()) columns.add(matches.group(1));
            for (JsonNode row : rows) {
                Set<String> actual = new HashSet<>();
                row.fieldNames().forEachRemaining(actual::add);
                assertEquals(columns, actual, table);
            }
            checked++;
        }
        assertEquals(31, checked);
        assertEquals(31, fixtures.size());
    }

    @Test void fixtureReferencesAndConditionalNullsAreConsistent() {
        Map<String, String> targets = Map.ofEntries(
            Map.entry("account_id", "accounts"), Map.entry("profile_id", "profiles"), Map.entry("assigned_by", "profiles"),
            Map.entry("tenant_id", "tenants"), Map.entry("zone_id", "parking_zones"), Map.entry("spot_id", "parking_spots"),
            Map.entry("floor_plan_id", "floor_plans"), Map.entry("reservation_id", "reservations"),
            Map.entry("payment_id", "digital_payments"), Map.entry("token_id", "payment_tokens"),
            Map.entry("charge_id", "additional_charges"), Map.entry("sensor_id", "occupancy_sensors"),
            Map.entry("reading_id", "occupancy_readings"), Map.entry("health_event_id", "sensor_health_events"),
            Map.entry("conflict_id", "occupancy_conflicts"));
        var tables = fixtures.fields();
        while (tables.hasNext()) {
            var table = tables.next();
            for (JsonNode row : table.getValue()) {
                var fields = row.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    if (field.getValue().isNull()) continue;
                    String destination = targets.get(field.getKey());
                    if (destination == null) continue;
                    UUID.fromString(field.getValue().asText());
                    String targetKey = field.getKey().equals("assigned_by") ? "profile_id" : field.getKey();
                    boolean found = false;
                    for (JsonNode parent : fixtures.get(destination))
                        if (parent.path(targetKey).equals(field.getValue())) found = true;
                    assertTrue(found, table.getKey() + "." + field.getKey());
                }
            }
        }
        for (JsonNode alert : fixtures.get("occupancy_alerts"))
            assertNotEquals(alert.get("health_event_id").isNull(), alert.get("conflict_id").isNull());
        for (JsonNode subscription : fixtures.get("subscriptions"))
            assertNotEquals(subscription.get("profile_id").isNull(), subscription.get("tenant_id").isNull());
    }
}
