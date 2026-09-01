package com.vibegraph.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.repository.PlanRepository;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.auth.repository.ProjectUsageRepository;
import com.vibegraph.auth.repository.UserAccountSettingsRepository;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.ProjectUsageService;
import com.vibegraph.common.exception.QuotaExceededException;

@Testcontainers(disabledWithoutDocker = true)
@org.springframework.boot.test.context.SpringBootTest(classes = QuotaReservationConcurrencyTest.TestConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Concurrent source quota reservation (PostgreSQL)")
class QuotaReservationConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

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

    @Configuration
    @EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration"
    })
    @EntityScan("com.vibegraph.auth.domain.entity")
    @EnableJpaRepositories("com.vibegraph.auth.repository")
    static class TestConfig {

        @Bean
        AccountSettingsService accountSettingsService(
                PlanRepository planRepository,
                UserAccountSettingsRepository settingsRepository,
                ProjectUsageRepository usageRepository) {
            return new AccountSettingsService(planRepository, settingsRepository, usageRepository);
        }

        @Bean
        ProjectUsageService projectUsageService(
                ProjectUsageRepository usageRepository,
                UserAccountSettingsRepository settingsRepository,
                ProjectOwnershipRepository ownershipRepository) {
            return new ProjectUsageService(usageRepository, settingsRepository, ownershipRepository);
        }
    }

    @Autowired
    ProjectUsageService projectUsageService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("concurrent imports cannot reserve more than the source storage quota")
    void concurrentImports_onlyOneReservationSucceeds() throws Exception {
        UUID userId = UUID.randomUUID();
        seedAccount(userId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            futures.add(executor.submit(() -> attemptReservation(userId, "p1", ready, start)));
            futures.add(executor.submit(() -> attemptReservation(userId, "p2", ready, start)));
            ready.await();
            start.countDown();

            assertThat(List.of(futures.get(0).get(), futures.get(1).get()))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(storage_bytes), 0) FROM project_usage WHERE owner_id = ?",
                Long.class,
                userId)).isEqualTo(60L);
    }

    private boolean attemptReservation(
            UUID userId,
            String projectId,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            projectUsageService.recordImport(projectId, userId, 60L);
            return true;
        } catch (QuotaExceededException ex) {
            return false;
        }
    }

    private void seedAccount(UUID userId) {
        UUID planId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, email, role)
                VALUES (?, ?, 'USER')
                """, userId, userId + "@quota.test");
        jdbcTemplate.update("""
                INSERT INTO plans (id, code, name, storage_limit_bytes, api_key_limit,
                                   monthly_credit_limit, contact_sales_required, is_active, sort_order)
                VALUES (?, ?, ?, ?, 0, 0, false, true, 0)
                """, planId, "TEST_" + userId.toString().substring(0, 8), "Test",
                100L);
        jdbcTemplate.update("""
                INSERT INTO user_account_settings (user_id, plan_id)
                VALUES (?, ?)
                """, userId, planId);
        jdbcTemplate.update("""
                INSERT INTO projects (project_id, owner_id, name, source_type)
                VALUES ('p1', ?, 'p1', 'LOCAL'), ('p2', ?, 'p2', 'LOCAL')
                """, userId, userId);
    }
}
