package com.vibegraph.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.auth.repository.projection.AuthSnapshot;

/**
 * Executes the consolidated authentication query against a real PostgreSQL.
 *
 * <p>The query replaces four separate reads on the per-request path, and it is the kind of thing a
 * mock cannot vouch for: a constructor projection over a {@code LEFT JOIN} plus a correlated
 * {@code EXISTS} either produces the right row or silently produces the wrong one. Spring validates
 * the JPQL at bootstrap, which proves it parses — these tests prove it answers correctly.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = AuthSnapshotQueryIT.TestConfig.class)
@DisplayName("Consolidated auth snapshot query (PostgreSQL)")
class AuthSnapshotQueryIT {

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
    }

    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private UUID insertUser(boolean deactivated, String deactivationReasonSafe) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, role, deactivated, deactivation_reason_safe)
                VALUES (?, ?, 'x', 'USER', ?, ?)
                """, id, id + "@test.local", deactivated, deactivationReasonSafe);
        return id;
    }

    /** Settings rows need a real plan; the migrations seed those, so borrow the first one. */
    private void insertSettings(UUID userId, boolean blocked) {
        jdbcTemplate.update("""
                INSERT INTO user_account_settings (user_id, plan_id, blocked_at, blocked_reason_safe)
                VALUES (?, (SELECT id FROM plans ORDER BY id LIMIT 1), ?, ?)
                """,
                userId,
                blocked ? java.sql.Timestamp.from(Instant.now()) : null,
                blocked ? "Policy review" : null);
    }

    private UUID insertSession(UUID userId, Instant expiresAt, Instant revokedAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO refresh_sessions (id, user_id, family_id, token_hash, expires_at, revoked_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, userId, UUID.randomUUID(), UUID.randomUUID().toString(),
                java.sql.Timestamp.from(expiresAt),
                revokedAt == null ? null : java.sql.Timestamp.from(revokedAt));
        return id;
    }

    @Test
    @DisplayName("returns identity and a live session for a plain active account")
    void activeUserWithLiveSession() {
        UUID userId = insertUser(false, null);
        UUID sessionId = insertSession(userId, Instant.now().plusSeconds(3600), null);

        AuthSnapshot snapshot = userRepository
                .findAuthSnapshot(userId, sessionId, Instant.now())
                .orElseThrow();

        assertThat(snapshot.id()).isEqualTo(userId);
        assertThat(snapshot.role()).isEqualTo(Role.USER);
        assertThat(snapshot.deactivated()).isFalse();
        // No settings row at all — the LEFT JOIN must not drop the user.
        assertThat(snapshot.blocked()).isFalse();
        assertThat(snapshot.sessionActive()).isTrue();
    }

    @Test
    @DisplayName("reports a revoked session as inactive")
    void revokedSessionIsInactive() {
        UUID userId = insertUser(false, null);
        UUID sessionId = insertSession(userId, Instant.now().plusSeconds(3600), Instant.now());

        assertThat(userRepository.findAuthSnapshot(userId, sessionId, Instant.now()).orElseThrow()
                .sessionActive()).isFalse();
    }

    @Test
    @DisplayName("reports an expired session as inactive")
    void expiredSessionIsInactive() {
        UUID userId = insertUser(false, null);
        UUID sessionId = insertSession(userId, Instant.now().minusSeconds(60), null);

        assertThat(userRepository.findAuthSnapshot(userId, sessionId, Instant.now()).orElseThrow()
                .sessionActive()).isFalse();
    }

    @Test
    @DisplayName("a session belonging to another account never counts as active")
    void sessionOfAnotherUserIsInactive() {
        UUID owner = insertUser(false, null);
        UUID other = insertUser(false, null);
        UUID sessionId = insertSession(owner, Instant.now().plusSeconds(3600), null);

        assertThat(userRepository.findAuthSnapshot(other, sessionId, Instant.now()).orElseThrow()
                .sessionActive()).isFalse();
    }

    @Test
    @DisplayName("carries the blocked flag and its safe reason from the settings row")
    void blockedAccountIsReported() {
        UUID userId = insertUser(false, null);
        insertSettings(userId, true);

        AuthSnapshot snapshot = userRepository
                .findAuthSnapshot(userId, null, Instant.now())
                .orElseThrow();

        assertThat(snapshot.blocked()).isTrue();
        assertThat(snapshot.blockedReasonSafe()).isEqualTo("Policy review");
        // A null sessionId must not be reported as an active session; the caller decides.
        assertThat(snapshot.sessionActive()).isFalse();
    }

    @Test
    @DisplayName("a settings row without a block does not mark the account blocked")
    void settingsWithoutBlockIsNotBlocked() {
        UUID userId = insertUser(false, null);
        insertSettings(userId, false);

        assertThat(userRepository.findAuthSnapshot(userId, null, Instant.now()).orElseThrow()
                .blocked()).isFalse();
    }

    @Test
    @DisplayName("carries deactivation state and its safe reason")
    void deactivatedAccountIsReported() {
        UUID userId = insertUser(true, "Account closed by administrator");

        AuthSnapshot snapshot = userRepository
                .findAuthSnapshot(userId, null, Instant.now())
                .orElseThrow();

        assertThat(snapshot.deactivated()).isTrue();
        assertThat(snapshot.deactivationReasonSafe()).isEqualTo("Account closed by administrator");
    }

    @Test
    @DisplayName("an unknown account yields no snapshot")
    void unknownUserYieldsEmpty() {
        Optional<AuthSnapshot> snapshot = userRepository
                .findAuthSnapshot(UUID.randomUUID(), null, Instant.now());

        assertThat(snapshot).isEmpty();
    }
}
