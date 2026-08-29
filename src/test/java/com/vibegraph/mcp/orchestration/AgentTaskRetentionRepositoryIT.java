package com.vibegraph.mcp.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = AgentTaskRetentionRepositoryIT.TestConfig.class)
class AgentTaskRetentionRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");
    private static final Instant EXPIRED = NOW.minusSeconds(100L * 24 * 60 * 60);
    private static final Instant CUTOFF = NOW.minusSeconds(90L * 24 * 60 * 60);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration"
    })
    @EntityScan(basePackageClasses = AgentTask.class)
    @EnableJpaRepositories(basePackageClasses = AgentTaskRepository.class)
    @Import(AgentTaskRetentionBatch.class)
    static class TestConfig {
    }

    @Autowired
    private AgentTaskRetentionBatch batch;

    @Autowired
    private AgentTaskRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void clearTasks() {
        jdbcTemplate.execute("TRUNCATE TABLE agent_tasks CASCADE");
    }

    @Test
    void pruneBatch_RemovesExpiredLeavesThenTheirParentsWithoutTouchingActiveReferences() {
        insertTask("standalone", TaskStatus.COMPLETED, EXPIRED, null);
        insertTask("root", TaskStatus.COMPLETED, EXPIRED, null);
        insertTask("leaf", TaskStatus.COMPLETED, EXPIRED, null);
        link("root", "leaf");

        insertTask("referenced", TaskStatus.COMPLETED, EXPIRED, null);
        insertTask("active-child", TaskStatus.IN_PROGRESS, EXPIRED, null);
        link("referenced", "active-child");

        insertTask("replacement", TaskStatus.IN_PROGRESS, EXPIRED, null);
        insertTask("superseded", TaskStatus.SUPERSEDED, EXPIRED, "replacement");
        insertTask("replacement-target", TaskStatus.COMPLETED, EXPIRED, null);
        insertTask("retry", TaskStatus.IN_PROGRESS, EXPIRED, "replacement-target");

        insertTask("recent", TaskStatus.COMPLETED, NOW.minusSeconds(10L * 24 * 60 * 60), null);
        insertTask("active", TaskStatus.IN_PROGRESS, EXPIRED, null);

        int first = batch.pruneBatch(CUTOFF, 50);
        int second = batch.pruneBatch(CUTOFF, 50);
        int third = batch.pruneBatch(CUTOFF, 50);

        assertThat(first).isEqualTo(3);
        assertThat(second).isOne();
        assertThat(third).isZero();
        assertThat(repository.existsById("standalone")).isFalse();
        assertThat(repository.existsById("leaf")).isFalse();
        assertThat(repository.existsById("root")).isFalse();
        assertThat(repository.existsById("superseded")).isFalse();
        assertThat(repository.existsById("referenced")).isTrue();
        assertThat(repository.existsById("active-child")).isTrue();
        assertThat(repository.existsById("replacement-target")).isTrue();
        assertThat(repository.existsById("retry")).isTrue();
        assertThat(repository.existsById("recent")).isTrue();
        assertThat(repository.existsById("active")).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_task_dependencies WHERE dependency_task_id = 'referenced'",
                Integer.class)).isOne();
    }

    @Test
    void pruneBatch_SkipsRowsLockedByAnotherReplica() throws Exception {
        insertTask("a-locked", TaskStatus.COMPLETED, EXPIRED, null);
        insertTask("b-free", TaskStatus.COMPLETED, EXPIRED, null);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement lock = connection.prepareStatement(
                    "SELECT id FROM agent_tasks WHERE id = ? FOR UPDATE")) {
                lock.setString(1, "a-locked");
                assertThat(lock.executeQuery().next()).isTrue();

                assertThat(batch.pruneBatch(CUTOFF, 1)).isOne();
                assertThat(repository.existsById("a-locked")).isTrue();
                assertThat(repository.existsById("b-free")).isFalse();
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void migration_AddsNonNullTimestampDefaults() {
        jdbcTemplate.update("""
                INSERT INTO agent_tasks (id, description, status, retry_count, max_retries, version)
                VALUES ('migration-defaults', 'migration defaults', 'PENDING', 0, 0, 0)
                """);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_at IS NOT NULL AND updated_at IS NOT NULL FROM agent_tasks "
                        + "WHERE id = 'migration-defaults'",
                Boolean.class)).isTrue();
    }

    private void insertTask(String id, TaskStatus status, Instant timestamp, String replacementId) {
        jdbcTemplate.update("""
                INSERT INTO agent_tasks (
                    id, description, status, retry_count, max_retries, version,
                    replacement_task_id, created_at, updated_at, completed_at
                ) VALUES (?, ?, ?, 0, 0, 0, ?, ?, ?, ?)
                """,
                id, id, status.name(), replacementId, Timestamp.from(timestamp), Timestamp.from(timestamp),
                isRetentionTerminal(status) ? Timestamp.from(timestamp) : null);
    }

    private void link(String upstreamId, String downstreamId) {
        jdbcTemplate.update("""
                INSERT INTO agent_task_dependencies (task_id, dependency_task_id) VALUES (?, ?)
                """, downstreamId, upstreamId);
        jdbcTemplate.update("""
                INSERT INTO agent_task_dependents (task_id, dependent_task_id) VALUES (?, ?)
                """, upstreamId, downstreamId);
    }

    private boolean isRetentionTerminal(TaskStatus status) {
        return status == TaskStatus.COMPLETED
                || status == TaskStatus.SUPERSEDED
                || status == TaskStatus.FAILED_TERMINAL;
    }
}
