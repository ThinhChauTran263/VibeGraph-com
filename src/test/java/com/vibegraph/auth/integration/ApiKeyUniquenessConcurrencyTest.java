package com.vibegraph.auth.integration;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ApiKeyUniquenessConcurrencyTest.TestConfig.class)
class ApiKeyUniquenessConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Configuration
    @EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration"
    })
    static class TestConfig {
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Test
    void concurrentCreatesForSameUserAndProject_onlyOnePersists() throws Exception {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, email) VALUES (?, ?)", userId, userId + "@test.local");
        jdbcTemplate.update("""
                INSERT INTO projects (project_id, owner_id, name, source_type)
                VALUES ('project-1', ?, 'Project One', 'LOCAL')
                """, userId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> insertKey(userId, ready, start));
            Future<Boolean> second = executor.submit(() -> insertKey(userId, ready, start));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM api_keys WHERE user_id = ? AND project_id = ? AND deleted_at IS NULL",
                Integer.class, userId, "project-1");
        assertThat(count).isEqualTo(1);
    }

    @Test
    void deletingProjectWithAdminLockedKey_isRejectedAndHistoryIsPreserved() {
        UUID userId = UUID.randomUUID();
        String projectId = "locked-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, email) VALUES (?, ?)", userId, userId + "@test.local");
        jdbcTemplate.update("""
                INSERT INTO projects (project_id, owner_id, name, source_type)
                VALUES (?, ?, 'Locked Project', 'LOCAL')
                """, projectId, userId);
        jdbcTemplate.update("""
                INSERT INTO api_keys (user_id, project_id, key_hash, key_prefix, name,
                                      disabled_at, disabled_by, disabled_reason)
                VALUES (?, ?, ?, ?, 'Locked CLI', now(), 'ADMIN', 'Policy violation')
                """, userId, projectId, UUID.randomUUID().toString(),
                "vbg_" + UUID.randomUUID().toString().substring(0, 8));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> jdbcTemplate.update("DELETE FROM projects WHERE project_id = ?", projectId))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("administrator-locked API key");

        Integer keyCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM api_keys WHERE project_id = ? AND disabled_by = 'ADMIN'",
                Integer.class, projectId);
        assertThat(keyCount).isEqualTo(1);
    }

    private boolean insertKey(UUID userId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            jdbcTemplate.update("""
                    INSERT INTO api_keys (user_id, project_id, key_hash, key_prefix, name)
                    VALUES (?, 'project-1', ?, ?, 'CLI')
                    """, userId, UUID.randomUUID().toString(), "vbg_" + UUID.randomUUID().toString().substring(0, 8));
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }
}
