package com.vibegraph.common.supabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.vibegraph.abuse.entity.RequestEvent;
import com.vibegraph.abuse.RequestEventRepository;
import com.vibegraph.common.supabase.repository.JdbcRequestEventRepository;

/**
 * Verifies the split between the Supabase migration (DDL) credential and the runtime (CRUD)
 * credential against a real PostgreSQL instance.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = SupabaseCredentialSeparationIT.TestConfig.class)
@DisplayName("Supabase migration and runtime credential separation (PostgreSQL)")
class SupabaseCredentialSeparationIT {

    private static final String SCHEMA = "vibegraph_realtime";
    private static final String RUNTIME_ROLE = "vibegraph_runtime";
    // Container-local credential for an ephemeral database; never a real secret.
    private static final String RUNTIME_ROLE_PASSWORD = "container-local-only";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void provisionRuntimeRole() throws SQLException {
        POSTGRES.start();
        // Mirrors scripts/supabase-runtime-role.sql: the runtime role gets CONNECT, schema USAGE
        // and CRUD through default privileges, but never CREATE.
        try (Connection connection = java.sql.DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
            statement.execute("CREATE ROLE " + RUNTIME_ROLE
                    + " LOGIN PASSWORD '" + RUNTIME_ROLE_PASSWORD + "'");
            statement.execute("ALTER ROLE " + RUNTIME_ROLE
                    + " NOCREATEDB NOCREATEROLE NOSUPERUSER NOREPLICATION");
            statement.execute("GRANT CONNECT ON DATABASE " + POSTGRES.getDatabaseName()
                    + " TO " + RUNTIME_ROLE);
            statement.execute("GRANT USAGE ON SCHEMA " + SCHEMA + " TO " + RUNTIME_ROLE);
            statement.execute("REVOKE CREATE ON SCHEMA " + SCHEMA + " FROM " + RUNTIME_ROLE);
            statement.execute("REVOKE CREATE ON SCHEMA public FROM " + RUNTIME_ROLE);
            statement.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
            statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA " + SCHEMA
                    + " GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO " + RUNTIME_ROLE);
            statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA " + SCHEMA
                    + " GRANT USAGE, SELECT ON SEQUENCES TO " + RUNTIME_ROLE);
        }
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");

        registry.add("vibegraph.supabase.enabled", () -> "true");
        registry.add("vibegraph.supabase.schema", () -> SCHEMA);
        registry.add("vibegraph.supabase.require-separate-credentials", () -> "true");
        // Runtime: least privilege.
        registry.add("vibegraph.supabase.jdbc-url", POSTGRES::getJdbcUrl);
        registry.add("vibegraph.supabase.username", () -> RUNTIME_ROLE);
        registry.add("vibegraph.supabase.password", () -> RUNTIME_ROLE_PASSWORD);
        // Migration: owns the schema and runs Flyway.
        registry.add("vibegraph.supabase.migration.jdbc-url", POSTGRES::getJdbcUrl);
        registry.add("vibegraph.supabase.migration.username", POSTGRES::getUsername);
        registry.add("vibegraph.supabase.migration.password", POSTGRES::getPassword);
    }

    @Configuration
    @EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration"
    })
    @Import({ SupabaseDatabaseConfig.class, JdbcRequestEventRepository.class })
    static class TestConfig {
    }

    @Autowired SupabaseDatabase runtimeDatabase;
    @Autowired RequestEventRepository requestEventRepository;
    @Autowired JdbcTemplate primaryJdbcTemplate;

    @Autowired
    @Qualifier("supabaseMigrationDatabase")
    SupabaseDatabase migrationDatabase;

    @Test
    @DisplayName("migration and runtime use distinct pools and distinct roles")
    void datasources_areSeparate() throws SQLException {
        assertThat(migrationDatabase).isNotSameAs(runtimeDatabase);
        assertThat(currentUser(migrationDatabase.dataSource())).isEqualTo(POSTGRES.getUsername());
        assertThat(currentUser(runtimeDatabase.dataSource())).isEqualTo(RUNTIME_ROLE);
    }

    @Test
    @DisplayName("Flyway ran on the migration datasource and created the Supabase schema")
    void flyway_ranWithMigrationCredential() {
        Long migrations = new JdbcTemplate(migrationDatabase.dataSource()).queryForObject(
                "SELECT count(*) FROM " + SCHEMA + ".flyway_schema_history WHERE success", Long.class);
        assertThat(migrations).isPositive();
        assertThat(new JdbcTemplate(migrationDatabase.dataSource()).queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = ?",
                Long.class, SCHEMA)).isPositive();
    }

    @Test
    @DisplayName("the runtime role can perform every CRUD operation it needs")
    void runtimeRole_hasRequiredCrud() {
        UUID id = UUID.randomUUID();
        RequestEvent stored = requestEventRepository.save(RequestEvent.builder()
                .id(id)
                .ipAddress("203.0.113.50")
                .route("/api/projects/{id}")
                .method("GET")
                .status(200)
                .eventType("REQUEST")
                .occurredAt(Instant.now())
                .build());
        assertThat(stored).isNotNull();

        JdbcTemplate runtime = new JdbcTemplate(runtimeDatabase.dataSource());
        assertThat(runtime.queryForObject(
                "SELECT count(*) FROM request_events WHERE id = ?", Long.class, id)).isEqualTo(1L);
        assertThat(runtime.update("UPDATE request_events SET status = 201 WHERE id = ?", id)).isEqualTo(1);
        assertThat(requestEventRepository.deleteOccurredBefore(Instant.now().plusSeconds(60)))
                .isPositive();
    }

    @Test
    @DisplayName("the runtime role cannot run CREATE, ALTER or DROP")
    void runtimeRole_cannotRunDdl() {
        JdbcTemplate runtime = new JdbcTemplate(runtimeDatabase.dataSource());

        // Spring wraps the driver error, so the authorization detail lives on the root cause.
        assertThatThrownBy(() -> runtime.execute("CREATE TABLE should_fail (id integer)"))
                .rootCause()
                .hasMessageContaining("permission denied");
        assertThatThrownBy(() -> runtime.execute("ALTER TABLE request_events ADD COLUMN should_fail integer"))
                .rootCause()
                .hasMessageContaining("must be owner");
        assertThatThrownBy(() -> runtime.execute("DROP TABLE request_events"))
                .rootCause()
                .hasMessageContaining("must be owner");
    }

    @Test
    @DisplayName("the primary Flyway and JPA datasource are unaffected")
    void primaryPersistence_isUnaffected() {
        assertThat(primaryJdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.flyway_schema_history WHERE success", Long.class))
                .isPositive();
        assertThatCode(() -> primaryJdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.users", Long.class)).doesNotThrowAnyException();
    }

    private String currentUser(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var results = statement.executeQuery("SELECT current_user")) {
            results.next();
            return results.getString(1);
        }
    }
}
