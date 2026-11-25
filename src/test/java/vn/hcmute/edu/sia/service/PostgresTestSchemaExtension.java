package vn.hcmute.edu.sia.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public class PostgresTestSchemaExtension implements ExecutionCondition, AfterAllCallback {

    private static final String TEST_SCHEMA_PREFIX = "sia_test_it_";
    private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean(false);

    private static volatile boolean initialized;
    private static volatile boolean available;
    private static volatile boolean schemaCreated;
    private static volatile String testSchema;
    private static volatile String adminBaseUrl;
    private static volatile String schemaUrl;
    private static volatile String username;
    private static volatile String password;

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (!initialized) {
            synchronized (PostgresTestSchemaExtension.class) {
                if (!initialized) {
                    initialize();
                    initialized = true;
                }
            }
        }
        return available
                ? ConditionEvaluationResult.enabled("PostgreSQL test schema ready: " + testSchema)
                : ConditionEvaluationResult.disabled("PostgreSQL unavailable for integration tests");
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        dropCreatedSchemaIfNeeded();
    }

    public static String getSchemaUrl() {
        return schemaUrl;
    }

    public static String getUsername() {
        return username;
    }

    public static String getPassword() {
        return password;
    }

    public static String getTestSchema() {
        return testSchema;
    }

    public static String stripSchemaParam(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "";
        }
        int queryIndex = jdbcUrl.indexOf('?');
        if (queryIndex < 0) {
            return jdbcUrl;
        }
        String base = jdbcUrl.substring(0, queryIndex);
        List<String> kept = filterNonSchemaParams(jdbcUrl.substring(queryIndex + 1));
        return kept.isEmpty() ? base : base + "?" + String.join("&", kept);
    }

    public static String withSchemaParam(String jdbcUrl, String schema) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("JDBC URL must not be blank");
        }
        if (schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("Schema must not be blank");
        }
        int queryIndex = jdbcUrl.indexOf('?');
        String base = queryIndex < 0 ? jdbcUrl : jdbcUrl.substring(0, queryIndex);
        List<String> params = queryIndex < 0
                ? new ArrayList<>()
                : filterNonSchemaParams(jdbcUrl.substring(queryIndex + 1));
        params.add("currentSchema=" + schema);
        return base + "?" + String.join("&", params);
    }

    private static void initialize() {
        Map<String, String> dotEnv = loadDotEnv();
        String rawUrl = firstNonBlank(
                System.getProperty("test.db.url"),
                System.getenv("TEST_DB_URL"),
                System.getenv("DB_URL"),
                dotEnv.get("DB_URL")
        );
        username = firstNonBlank(
                System.getProperty("test.db.username"),
                System.getenv("TEST_DB_USERNAME"),
                System.getenv("DB_USERNAME"),
                dotEnv.get("DB_USERNAME")
        );
        password = firstNonBlank(
                System.getProperty("test.db.password"),
                System.getenv("TEST_DB_PASSWORD"),
                System.getenv("DB_PASSWORD"),
                dotEnv.get("DB_PASSWORD")
        );
        boolean required = Boolean.parseBoolean(firstNonBlank(
                System.getProperty("test.postgres.required"),
                System.getenv("TEST_POSTGRES_REQUIRED"),
                "false"
        ));

        if (rawUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
            if (required) {
                throw new IllegalStateException("PostgreSQL test credentials are required but missing");
            }
            available = false;
            return;
        }

        testSchema = TEST_SCHEMA_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        adminBaseUrl = stripSchemaParam(rawUrl);
        schemaUrl = withSchemaParam(rawUrl, testSchema);

        DriverManager.setLoginTimeout(2);
        Connection connection;
        try {
            connection = DriverManager.getConnection(adminBaseUrl, username, password);
        } catch (SQLException sqlEx) {
            if (required) {
                throw new IllegalStateException(
                        "PostgreSQL is required for integration tests but unreachable",
                        sqlEx
                );
            }
            available = false;
            return;
        }

        try (Connection conn = connection;
             Statement statement = conn.createStatement()) {
            statement.execute("CREATE SCHEMA " + testSchema);
            schemaCreated = true;
            registerShutdownHookOnce();
            statement.execute("SET search_path TO " + testSchema);

            String schemaSql = loadSchemaSql();
            for (String chunk : schemaSql.split(";")) {
                String sql = chunk.trim();
                if (!sql.isEmpty()) {
                    statement.execute(sql);
                }
            }
            available = true;
        } catch (Exception ex) {
            try {
                dropCreatedSchemaIfNeeded();
            } catch (Exception cleanupEx) {
                ex.addSuppressed(cleanupEx);
            }
            schemaCreated = false;
            available = false;
            throw new IllegalStateException("Failed to initialize PostgreSQL test schema " + testSchema, ex);
        }
    }

    private static String loadSchemaSql() throws IOException {
        Path schemaPath = Path.of("src/main/resources/db/schema.sql");
        if (Files.exists(schemaPath)) {
            return Files.readString(schemaPath, StandardCharsets.UTF_8);
        }
        try (InputStream stream = PostgresTestSchemaExtension.class.getClassLoader().getResourceAsStream("db/schema.sql")) {
            if (stream == null) {
                throw new IOException("Could not locate db/schema.sql on filesystem or classpath");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void registerShutdownHookOnce() {
        if (SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    dropCreatedSchemaIfNeeded();
                } catch (Exception ignored) {
                }
            }, "postgres-test-schema-cleanup"));
        }
    }

    private static synchronized void dropCreatedSchemaIfNeeded() throws SQLException {
        if (!schemaCreated
                || testSchema == null
                || !testSchema.startsWith(TEST_SCHEMA_PREFIX)
                || adminBaseUrl == null
                || adminBaseUrl.isBlank()) {
            return;
        }
        String schemaToDrop = testSchema;
        try (Connection connection = DriverManager.getConnection(adminBaseUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schemaToDrop + " CASCADE");
        } finally {
            schemaCreated = false;
            available = false;
            initialized = false;
        }
    }

    private static List<String> filterNonSchemaParams(String query) {
        List<String> params = new ArrayList<>();
        for (String segment : query.split("&")) {
            if (segment.isBlank()) {
                continue;
            }
            int eqIdx = segment.indexOf('=');
            String key = (eqIdx >= 0 ? segment.substring(0, eqIdx) : segment).trim();
            if (!"currentSchema".equalsIgnoreCase(key)) {
                params.add(segment);
            }
        }
        return params;
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> map = new HashMap<>();
        Path envPath = Path.of(".env");
        if (!Files.exists(envPath)) {
            return map;
        }
        try {
            for (String line : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int idx = trimmed.indexOf('=');
                if (idx > 0) {
                    String key = trimmed.substring(0, idx).trim();
                    String value = stripSurroundingQuotes(trimmed.substring(idx + 1).trim());
                    map.put(key, value);
                }
            }
        } catch (IOException ignored) {
        }
        return map;
    }

    private static String stripSurroundingQuotes(String value) {
        if (value.length() >= 2) {
            boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
            boolean singleQuoted = value.startsWith("'") && value.endsWith("'");
            if (doubleQuoted || singleQuoted) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
