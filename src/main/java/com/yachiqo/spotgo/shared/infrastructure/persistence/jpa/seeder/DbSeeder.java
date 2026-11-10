package com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.seeder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Loads demo fixtures only into compatible, existing tables. Never creates or deletes tables. */
@Component
@Profile({"dev", "local", "profiles-check", "production"})
@ConditionalOnProperty(name = "app.seeder.enabled", havingValue = "true")
public class DbSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DbSeeder.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, String> REFERENCES = Map.ofEntries(
        Map.entry("account_id", "accounts"), Map.entry("profile_id", "profiles"),
        Map.entry("assigned_by", "profiles"), Map.entry("tenant_id", "tenants"),
        Map.entry("zone_id", "parking_zones"), Map.entry("spot_id", "parking_spots"),
        Map.entry("floor_plan_id", "floor_plans"), Map.entry("reservation_id", "reservations"),
        Map.entry("payment_id", "digital_payments"), Map.entry("token_id", "payment_tokens"),
        Map.entry("charge_id", "additional_charges"), Map.entry("sensor_id", "occupancy_sensors"),
        Map.entry("reading_id", "occupancy_readings"), Map.entry("health_event_id", "sensor_health_events"),
        Map.entry("conflict_id", "occupancy_conflicts"));
    private static final Map<String, String> PRIMARY_KEYS = Map.ofEntries(
        Map.entry("accounts", "account_id"), Map.entry("profiles", "profile_id"),
        Map.entry("tenants", "tenant_id"), Map.entry("parking_zones", "zone_id"),
        Map.entry("parking_spots", "spot_id"), Map.entry("floor_plans", "floor_plan_id"),
        Map.entry("reservations", "reservation_id"), Map.entry("digital_payments", "payment_id"),
        Map.entry("payment_tokens", "token_id"), Map.entry("additional_charges", "charge_id"),
        Map.entry("occupancy_sensors", "sensor_id"), Map.entry("occupancy_readings", "reading_id"),
        Map.entry("sensor_health_events", "health_event_id"), Map.entry("occupancy_conflicts", "conflict_id"));

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    @Value("${app.seeder.demo-password:Password123!}")
    private String demoPassword = "Password123!";

    public DbSeeder(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (demoPassword == null || demoPassword.isBlank()) {
            throw new IllegalStateException("A non-empty app.seeder.demo-password is required to seed demo accounts.");
        }
        JsonNode data;
        try (var input = new ClassPathResource("db.json").getInputStream()) {
            data = JSON.readTree(input);
        }
        Map<String, Table> schema = inspectSchema();
        String passwordHash = encoder.encode(demoPassword);



        int inserted = 0, compatible = 0, skippedRows = 0, existing = 0;
        var tables = new ArrayList<Map.Entry<String, JsonNode>>();
        data.fields().forEachRemaining(tables::add);
        var seedOrder = List.of("accounts", "credentials", "profiles", "payment_tokens", "tenants");
        tables.sort(Comparator.comparingInt(entry -> {
            int index = seedOrder.indexOf(entry.getKey());
            return index < 0 ? seedOrder.size() : index;
        }));
        for (var entry : tables) {
            String name = entry.getKey();
            JsonNode rows = entry.getValue();
            if (!rows.isArray() || rows.isEmpty()) continue;
            Table table = schema.get(name);
            if (table == null) {
                log.info("SEED SKIPPED: {} (table is not implemented yet)", name);
                continue;
            }
            List<String> missing = new ArrayList<>();
            rows.get(0).fieldNames().forEachRemaining(column -> {
                if (!table.columns.containsKey(lower(column))) missing.add(column);
            });
            Set<String> supplied = new HashSet<>();
            rows.get(0).fieldNames().forEachRemaining(column -> supplied.add(lower(column)));
            for (Column column : table.columns.values()) {
                if (column.required && !supplied.contains(lower(column.name))) missing.add("required value: " + column.name);
            }
            rows.get(0).fieldNames().forEachRemaining(column -> {
                Column actual = table.columns.get(lower(column));
                if (actual != null && !column.equals("correlation_id") && (column.endsWith("_id") || column.equals("assigned_by")) && !lower(actual.typeName).equals("uuid"))
                    missing.add("required UUID type: " + column);
            });
            if (!missing.isEmpty() || table.primaryKeys.size() != 1 || field(rows.get(0), table.primaryKeys.get(0)) == null) {
                log.warn("SEED SKIPPED: {} has an incompatible schema. Missing columns: {}.", name, missing);
                continue;
            }
            compatible++;
            for (JsonNode row : rows) {
                if (!referencesExist(name, table, row, schema)) {
                    skippedRows++;
                    continue;
                }
                if (insert(table, row, passwordHash)) inserted++;
                else existing++;
            }
        }
        log.info("SEED SUMMARY: {} JSON tables; {} compatible; {} new rows; {} already existed; {} skipped due to unavailable references.",
            data.size(), compatible, inserted, existing, skippedRows);
    }

    private Map<String, Table> inspectSchema() {
        return jdbc.execute((ConnectionCallback<Map<String, Table>>) connection -> {
            Map<String, Table> result = new LinkedHashMap<>();
            DatabaseMetaData metadata = connection.getMetaData();
            String schema = connection.getSchema();
            try (ResultSet tables = metadata.getTables(connection.getCatalog(), schema, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String name = tables.getString("TABLE_NAME");
                    Map<String, Column> columns = new LinkedHashMap<>();
                    try (ResultSet fields = metadata.getColumns(connection.getCatalog(), schema, name, "%")) {
                        while (fields.next()) {
                            String column = fields.getString("COLUMN_NAME");
                            boolean required = fields.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls
                                && fields.getString("COLUMN_DEF") == null
                                && !"YES".equals(fields.getString("IS_AUTOINCREMENT"));
                            columns.put(lower(column), new Column(column, fields.getInt("DATA_TYPE"), fields.getString("TYPE_NAME"), required));
                        }
                    }
                    List<String> keys = new ArrayList<>();
                    try (ResultSet pk = metadata.getPrimaryKeys(connection.getCatalog(), schema, name)) {
                        while (pk.next()) keys.add(lower(pk.getString("COLUMN_NAME")));
                    }
                    result.put(lower(name), new Table(schema, name, columns, keys));
                }
            }
            return result;
        });
    }

    private boolean referencesExist(String name, Table table, JsonNode row, Map<String, Table> schema) {
        var fields = row.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (field.getValue().isNull() || table.primaryKeys.contains(lower(field.getKey()))) continue;
            String destination = REFERENCES.get(field.getKey());
            if (destination == null) continue;
            Table parent = schema.get(destination);
            String key = PRIMARY_KEYS.get(destination);
            if (destination.equals("accounts") && (parent == null || !parent.columns.containsKey(key))) {
                parent = null;
            }

            if (parent == null || !parent.columns.containsKey(key) || !exists(parent, key, field.getValue())) {
                log.info("SEED ROW SKIPPED: {}.{} requires an available record in {}.", name, field.getKey(), destination);
                return false;
            }
        }
        return true;
    }

    private boolean exists(Table table, String key, JsonNode value) {
        Column column = table.columns.get(lower(key));
        return Boolean.TRUE.equals(jdbc.query("SELECT 1 FROM " + table.sqlName() + " WHERE " + quote(column.name) + " = ?",
            statement -> bind(statement, 1, column, value, null),
            (org.springframework.jdbc.core.ResultSetExtractor<Boolean>) ResultSet::next));
    }

    private boolean insert(Table table, JsonNode row, String passwordHash) {
        String key = table.primaryKeys.get(0);
        JsonNode id = field(row, key);
        if (id == null || id.isNull()) throw new IllegalArgumentException("Missing primary key in fixture for " + table.name);
        if (exists(table, key, id)) return false;
        List<Column> columns = new ArrayList<>();
        List<JsonNode> values = new ArrayList<>();
        row.fields().forEachRemaining(entry -> {
            Column column = table.columns.get(lower(entry.getKey()));
            if (column == null) throw new IllegalArgumentException("Incompatible column in " + table.name + ": " + entry.getKey());
            columns.add(column); values.add(entry.getValue());
        });
        String names = String.join(",", columns.stream().map(c -> quote(c.name)).toList());
        String placeholders = String.join(",", Collections.nCopies(columns.size(), "?"));
        jdbc.update("INSERT INTO " + table.sqlName() + " (" + names + ") VALUES (" + placeholders + ")", statement -> {
            for (int i = 0; i < columns.size(); i++) bind(statement, i + 1, columns.get(i), values.get(i), passwordHash);
        });
        return true;
    }

    private static void bind(PreparedStatement statement, int index, Column column, JsonNode value, String passwordHash) throws SQLException {
        if (value == null || value.isNull()) { statement.setNull(index, column.sqlType); return; }
        String type = lower(column.typeName);
        if (type.equals("uuid")) statement.setObject(index, UUID.fromString(value.asText()));
        else if (type.equals("jsonb") || type.equals("json")) {
            if (statement.getConnection().getMetaData().getDatabaseProductName().equals("H2"))
                statement.setBytes(index, value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            else statement.setObject(index, value.toString(), Types.OTHER);
        }
        else if (column.sqlType == Types.TIMESTAMP_WITH_TIMEZONE)
            statement.setObject(index, OffsetDateTime.ofInstant(Instant.parse(value.asText()), ZoneOffset.UTC));
        else if (column.sqlType == Types.TIMESTAMP) statement.setTimestamp(index, Timestamp.from(Instant.parse(value.asText())));
        else if (value.isBoolean()) statement.setBoolean(index, value.asBoolean());
        else if (value.isNumber()) statement.setBigDecimal(index, value.decimalValue());
        else statement.setString(index, value.asText().equals("${DEMO_PASSWORD_HASH}") ? passwordHash : value.asText());
    }


    private static JsonNode field(JsonNode row, String key) {
        var fields = row.fields();
        while (fields.hasNext()) { var entry = fields.next(); if (lower(entry.getKey()).equals(key)) return entry.getValue(); }
        return null;
    }
    private static String lower(String text) { return text.toLowerCase(Locale.ROOT); }
    private static String quote(String text) { return "\"" + text.replace("\"", "\"\"") + "\""; }
    private record Column(String name, int sqlType, String typeName, boolean required) {}
    private record Table(String schema, String name, Map<String, Column> columns, List<String> primaryKeys) {
        String sqlName() { return quote(schema) + "." + quote(name); }
    }
}
