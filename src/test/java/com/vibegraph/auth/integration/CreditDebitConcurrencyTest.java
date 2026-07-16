package com.vibegraph.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.vibegraph.auth.repository.CreditLedgerRepository;
import com.vibegraph.auth.repository.UserCreditBalanceRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.CreditBalanceService;
import com.vibegraph.auth.service.CreditPeriodCalculator;
import com.vibegraph.common.exception.InsufficientCreditsException;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = CreditDebitConcurrencyTest.TestConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Concurrent credit debit (PostgreSQL)")
class CreditDebitConcurrencyTest {

    private static final Instant REGISTERED_AT = Instant.parse("2024-01-31T08:00:00Z");
    private static final Instant NOW = Instant.parse("2024-03-30T12:00:00Z");

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
    @EntityScan("com.vibegraph.auth.domain")
    @EnableJpaRepositories("com.vibegraph.auth.repository")
    static class TestConfig {

        @Bean
        CreditPeriodCalculator creditPeriodCalculator() {
            return new CreditPeriodCalculator();
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        CreditBalanceService creditBalanceService(
                UserCreditBalanceRepository balanceRepository,
                CreditLedgerRepository ledgerRepository,
                AccountSettingsService accountSettingsService,
                UserRepository userRepository,
                CreditPeriodCalculator periodCalculator,
                Clock clock) {
            return new CreditBalanceService(
                    balanceRepository,
                    ledgerRepository,
                    accountSettingsService,
                    userRepository,
                    periodCalculator,
                    clock);
        }
    }

    @Autowired CreditBalanceService creditBalanceService;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean AccountSettingsService accountSettingsService;

    @Test
    @DisplayName("two simultaneous debits cannot spend more than the remaining balance")
    void concurrentDebits_onlyOneSucceeds() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID balanceId = UUID.randomUUID();
        seedUserAndBalance(userId, balanceId, 100);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        creditBalanceService.deductCredits(
                                userId, 80, "MCP", "MCP_TOOL_CALL", null);
                        return true;
                    } catch (InsufficientCreditsException ex) {
                        return false;
                    }
                }));
            }
            ready.await();
            start.countDown();

            List<Boolean> results = List.of(futures.get(0).get(), futures.get(1).get());
            assertThat(results).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT credits_used FROM user_credit_balances WHERE id = ?",
                Integer.class,
                balanceId)).isEqualTo(80);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM credit_ledger WHERE user_id = ?",
                Integer.class,
                userId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sum(credits_delta) FROM credit_ledger WHERE user_id = ?",
                Integer.class,
                userId)).isEqualTo(-80);
    }

    @Test
    @DisplayName("legacy calendar balance keeps usage and resets next on registration day")
    void legacyBalance_reanchorsWithoutFreeReset() {
        UUID userId = UUID.randomUUID();
        UUID balanceId = UUID.randomUUID();
        seedUserAndBalance(userId, balanceId, 100);
        jdbcTemplate.update("""
                UPDATE user_credit_balances
                SET period_start = DATE '2024-03-01',
                    period_end = DATE '2024-03-31',
                    credits_used = 40,
                    credits_adjustment = 7
                WHERE id = ?
                """, balanceId);

        var result = creditBalanceService.findOrCreateCurrentPeriod(userId);

        assertThat(result.getId()).isEqualTo(balanceId);
        assertThat(result.getPeriodStart()).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(result.getPeriodEnd()).isEqualTo(LocalDate.of(2024, 3, 30));
        assertThat(result.getCreditsUsed()).isEqualTo(40);
        assertThat(result.getCreditsAdjustment()).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_credit_balances WHERE user_id = ?",
                Integer.class,
                userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("simultaneous admin adjustments accumulate without losing either ledger row")
    void concurrentAdminAdjustments_accumulate() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID balanceId = UUID.randomUUID();
        seedUserAndBalance(userId, balanceId, 100);

        runConcurrently(
                () -> creditBalanceService.applyAdminAdjustment(userId, 10, "first"),
                () -> creditBalanceService.applyAdminAdjustment(userId, 20, "second"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT credits_adjustment FROM user_credit_balances WHERE id = ?",
                Integer.class,
                balanceId)).isEqualTo(30);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM credit_ledger WHERE user_id = ? AND operation_code = 'ADMIN_ADJUSTMENT'",
                Integer.class,
                userId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sum(credits_delta) FROM credit_ledger WHERE user_id = ?",
                Integer.class,
                userId)).isEqualTo(30);
    }

    @Test
    @DisplayName("admin adjustment and debit preserve both balance columns under concurrency")
    void concurrentDebitAndAdjustment_preserveBothColumns() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID balanceId = UUID.randomUUID();
        seedUserAndBalance(userId, balanceId, 100);

        runConcurrently(
                () -> creditBalanceService.deductCredits(
                        userId, 80, "CLI", "CLI_PUSH", null),
                () -> creditBalanceService.applyAdminAdjustment(userId, 25, "bonus"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT credits_used FROM user_credit_balances WHERE id = ?",
                Integer.class,
                balanceId)).isEqualTo(80);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT credits_adjustment FROM user_credit_balances WHERE id = ?",
                Integer.class,
                balanceId)).isEqualTo(25);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM credit_ledger WHERE user_id = ?",
                Integer.class,
                userId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sum(credits_delta) FROM credit_ledger WHERE user_id = ?",
                Integer.class,
                userId)).isEqualTo(-55);
    }

    @Test
    @DisplayName("quota limit update and debit do not reset usage under concurrency")
    void concurrentDebitAndLimitUpdate_preserveUsage() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID balanceId = UUID.randomUUID();
        seedUserAndBalance(userId, balanceId, 100);

        runConcurrently(
                () -> creditBalanceService.deductCredits(
                        userId, 80, "CLI", "CLI_PUSH", null),
                () -> creditBalanceService.updateCurrentPeriodLimitSnapshot(userId, 120));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT credits_used FROM user_credit_balances WHERE id = ?",
                Integer.class,
                balanceId)).isEqualTo(80);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT credits_limit_snapshot FROM user_credit_balances WHERE id = ?",
                Integer.class,
                balanceId)).isEqualTo(120);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM credit_ledger WHERE user_id = ?",
                Integer.class,
                userId)).isEqualTo(1);
    }

    private void runConcurrently(ThrowingRunnable first, ThrowingRunnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> firstFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                first.run();
                return null;
            });
            Future<Void> secondFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                second.run();
                return null;
            });
            ready.await();
            start.countDown();
            firstFuture.get();
            secondFuture.get();
        } finally {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private void seedUserAndBalance(UUID userId, UUID balanceId, int limit) {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, role, created_at)
                VALUES (?, ?, 'USER', ?)
                """, userId, userId + "@example.test", java.sql.Timestamp.from(REGISTERED_AT));
        jdbcTemplate.update("""
                INSERT INTO user_credit_balances (
                    id, user_id, period_start, period_end,
                    credits_limit_snapshot, credits_used, credits_adjustment)
                VALUES (?, ?, DATE '2024-02-29', DATE '2024-03-30', ?, 0, 0)
                """, balanceId, userId, limit);
    }
}
