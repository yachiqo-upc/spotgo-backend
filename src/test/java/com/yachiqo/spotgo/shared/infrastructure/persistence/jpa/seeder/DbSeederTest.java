package com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.seeder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.*;
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
        jdbc.execute("CREATE TABLE tenants (tenant_id UUID PRIMARY KEY, name VARCHAR NOT NULL, status VARCHAR NOT NULL, coordinates JSONB NOT NULL, address VARCHAR NOT NULL, token_id UUID, contact_data JSONB NOT NULL, rules JSONB NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE profiles (profile_id UUID PRIMARY KEY, account_id UUID UNIQUE NOT NULL, first_name VARCHAR NOT NULL, last_name VARCHAR NOT NULL, phone VARCHAR NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE payment_tokens (token_id UUID PRIMARY KEY, profile_id UUID NOT NULL, provider_token_ref VARCHAR NOT NULL, brand VARCHAR NOT NULL, last_four VARCHAR NOT NULL, expiration_month SMALLINT NOT NULL, expiration_year SMALLINT NOT NULL, status VARCHAR NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, revoked_at TIMESTAMP WITH TIME ZONE)");
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
        assertEquals(3, count("accounts"));
        assertEquals(3, count("credentials"));
        assertEquals(3, count("role_assignments"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM role_assignments WHERE role_type='SUPER_ADMIN'", Integer.class));
        assertEquals(1, count("sessions"));
        assertEquals(2, count("audit_entries"));
        assertEquals(1, count("tenants"));
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
        jdbc.update("UPDATE profiles SET first_name='Edited' WHERE first_name='Juan'");
        seeder.run();
        assertEquals(3, count("profiles"));
        assertEquals(3, count("vehicles"));
        assertEquals(2, count("driver_classifications"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM profiles WHERE first_name='Edited'", Integer.class));
    }

    @Test void absentTablesAreSkippedWithoutCreatingThem() {
        assertDoesNotThrow(() -> seeder.run());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='PUBLIC'", Integer.class));
    }

    @Test void missingAccountPreventsInsertingOrphanProfilesAndVehicles() throws Exception {
        currentSchema();
        jdbc.execute("DROP TABLE tenants");
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

    @Test void fixtureTablesContainConsistentRowsAndColumns() {
        assertEquals(32, fixtures.size());
        var tables = fixtures.fields();
        while (tables.hasNext()) {
            var table = tables.next();
            assertTrue(table.getValue().isArray(), table.getKey());
            if (table.getValue().isEmpty()) continue;
            Set<String> expected = new HashSet<>();
            table.getValue().get(0).fieldNames().forEachRemaining(expected::add);
            for (JsonNode row : table.getValue()) {
                Set<String> actual = new HashSet<>();
                row.fieldNames().forEachRemaining(actual::add);
                assertEquals(expected, actual, table.getKey());
            }
        }
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
    @Test void subscriptionAndBillingFixturesFollowTheApprovedPaymentFlow() throws Exception {
        JsonNode fixtures;
        try (var input=new ClassPathResource("db.json").getInputStream()) { fixtures=new ObjectMapper().readTree(input); }
        var payments=new java.util.HashMap<String,JsonNode>();
        for(var payment:fixtures.get("digital_payments"))payments.put(payment.path("payment_id").asText(),payment);
        for(var subscription:fixtures.get("subscriptions")){
            assertFalse(subscription.path("payment_id").isNull());
            var payment=payments.get(subscription.path("payment_id").asText());assertNotNull(payment);
            assertEquals("APPROVED",payment.path("status").asText());assertEquals("SUBSCRIPTION",payment.path("operation_type").asText());
            assertEquals(payment.path("approved_at").asText(),subscription.path("start_at").asText());
            assertTrue(subscription.path("plan").path("billing_period_months").asInt()>0);
            assertFalse(subscription.has("billing_period_months"),"The duration belongs in the plan JSON, not another column.");
            assertEquals(0,subscription.path("plan").path("price").decimalValue().compareTo(payment.path("amount").decimalValue()));
        }
        for(var attempt:fixtures.get("payment_attempts")){
            var payment=payments.get(attempt.path("payment_id").asText());
            assertFalse(java.time.Instant.parse(attempt.path("started_at").asText()).isBefore(java.time.Instant.parse(payment.path("created_at").asText())));
        }
        for(var document:fixtures.get("billing_documents")){
            assertEquals("DNI",document.path("tax_data").path("document_type").asText());
            assertTrue(document.path("tax_data").path("document_number").asText().matches("[0-9]{8}"));
        }
    }
    private JsonNode fixture(String table, String key, JsonNode id) {
        for (var row : fixtures.path(table)) if (row.path(key).equals(id)) return row;
        throw new AssertionError("Missing fixture reference: " + table + "." + key + "=" + id);
    }

    @Test void fixtureRelationshipsPreserveOwnershipAndTenantAcrossAllContexts() {
        var tables=fixtures.fields();
        while(tables.hasNext()) {
            var table=tables.next();var ids=new HashSet<String>();
            var firstRow=table.getValue().get(0);
            if(firstRow==null)continue;
            String pk=firstRow.fieldNames().next();
            for(var row:table.getValue())assertTrue(ids.add(UUID.fromString(row.path(pk).asText()).toString()),"Duplicate primary key in "+table.getKey());
        }
        for(var payment:fixtures.path("digital_payments"))if(!payment.path("token_id").isNull()) {
            var token=fixture("payment_tokens","token_id",payment.path("token_id"));
            assertEquals(payment.path("profile_id"),token.path("profile_id"),"Token belongs to another payer");
        }
        for(var reservation:fixtures.path("reservations")) {
            var spot=fixture("parking_spots","spot_id",reservation.path("spot_id"));
            var zone=fixture("parking_zones","zone_id",spot.path("zone_id"));
            var vehicle=fixture("vehicles","vehicle_id",reservation.path("vehicle_id"));
            assertEquals(spot.path("zone_id"),reservation.path("zone_id"));assertEquals(zone.path("tenant_id"),reservation.path("tenant_id"));
            assertEquals(vehicle.path("profile_id"),reservation.path("profile_id"),"Vehicle belongs to another profile");
            if(!reservation.path("payment_id").isNull()) {
                var payment=fixture("digital_payments","payment_id",reservation.path("payment_id"));
                assertEquals(reservation.path("profile_id"),payment.path("profile_id"));assertEquals("RESERVATION",payment.path("operation_type").asText());
                assertEquals(0,reservation.path("amount").decimalValue().compareTo(payment.path("amount").decimalValue()));
            }
        }
        for(var stay:fixtures.path("parking_sessions")) {
            var reservation=fixture("reservations","reservation_id",stay.path("reservation_id"));
            for(var column:java.util.List.of("profile_id","vehicle_id","spot_id"))assertEquals(reservation.path(column),stay.path(column),column);
        }
        for(var charge:fixtures.path("additional_charges"))if(!charge.path("payment_id").isNull()) {
            var reservation=fixture("reservations","reservation_id",charge.path("reservation_id"));
            var payment=fixture("digital_payments","payment_id",charge.path("payment_id"));
            assertEquals(reservation.path("profile_id"),payment.path("profile_id"));assertEquals("ADDITIONAL_CHARGE",payment.path("operation_type").asText());
            assertEquals(0,charge.path("amount").decimalValue().compareTo(payment.path("amount").decimalValue()));
        }
        for(var balance:fixtures.path("outstanding_balances")) {
            var charge=fixture("additional_charges","charge_id",balance.path("charge_id"));
            var reservation=fixture("reservations","reservation_id",charge.path("reservation_id"));
            assertEquals(reservation.path("profile_id"),balance.path("profile_id"),"Balance assigned to another profile");
            if(balance.path("status").asText().equals("OPEN"))assertEquals(0,balance.path("amount").decimalValue().compareTo(charge.path("amount").decimalValue()));
        }
        for(var sensor:fixtures.path("occupancy_sensors")) {
            var spot=fixture("parking_spots","spot_id",sensor.path("spot_id"));var zone=fixture("parking_zones","zone_id",spot.path("zone_id"));
            assertEquals(spot.path("zone_id"),sensor.path("zone_id"));assertEquals(zone.path("tenant_id"),sensor.path("tenant_id"));
        }
        for(var reading:fixtures.path("occupancy_readings"))assertEquals(reading.path("spot_id"),fixture("occupancy_sensors","sensor_id",reading.path("sensor_id")).path("spot_id"));
        for(var conflict:fixtures.path("occupancy_conflicts")) {
            var reading=fixture("occupancy_readings","reading_id",conflict.path("reading_id"));
            var sensor=fixture("occupancy_sensors","sensor_id",reading.path("sensor_id"));
            assertEquals(reading.path("spot_id"),conflict.path("spot_id"));assertEquals(sensor.path("tenant_id"),conflict.path("tenant_id"));
            if(!conflict.path("reservation_id").isNull())assertEquals(conflict.path("spot_id"),fixture("reservations","reservation_id",conflict.path("reservation_id")).path("spot_id"));
        }
        for(var alert:fixtures.path("occupancy_alerts")) {
            JsonNode origin;
            if(!alert.path("conflict_id").isNull())origin=fixture("occupancy_conflicts","conflict_id",alert.path("conflict_id"));
            else {var event=fixture("sensor_health_events","health_event_id",alert.path("health_event_id"));origin=fixture("occupancy_sensors","sensor_id",event.path("sensor_id"));}
            assertEquals(origin.path("tenant_id"),alert.path("tenant_id"));assertEquals(origin.path("spot_id"),alert.path("spot_id"));
            assertTrue(alert.path("evidence_ref").isNull(),"Demo alerts must not fabricate evidence images");
        }
        for(var table:java.util.List.of("capacity_snapshots","occupancy_reports"))for(var row:fixtures.path(table))if(!row.path("zone_id").isNull()) {
            assertEquals(row.path("tenant_id"),fixture("parking_zones","zone_id",row.path("zone_id")).path("tenant_id"));
        }
        for(var classification:fixtures.path("driver_classifications")) {
            var admin=fixture("profiles","profile_id",classification.path("assigned_by"));
            var role=fixture("role_assignments","account_id",admin.path("account_id"));
            assertEquals("PARKING_ADMIN",role.path("role_type").asText());assertEquals(classification.path("tenant_id"),role.path("tenant_id"));
        }
    }
}
