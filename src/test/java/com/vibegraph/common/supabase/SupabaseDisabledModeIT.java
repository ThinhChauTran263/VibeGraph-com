package com.vibegraph.common.supabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.entity.User;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.auth.service.AdminSecurityMonitorService;
import com.vibegraph.auth.service.AuthenticatedUser;
import com.vibegraph.auth.service.NotificationService;
import com.vibegraph.common.supabase.repository.JdbcAnnouncementRepository;
import com.vibegraph.common.supabase.repository.JdbcNotificationRepository;
import com.vibegraph.common.supabase.repository.JdbcRequestEventRepository;
import com.vibegraph.common.supabase.repository.JdbcSecurityEventRepository;

import javax.sql.DataSource;

/**
 * Verifies that with {@code vibegraph.supabase.enabled=false} the Supabase JDBC repositories and
 * the JPA repositories share one transaction on the primary datasource, with no competing
 * resource binding and no default transaction-manager ambiguity.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = SupabaseDisabledModeIT.TestConfig.class)
@DisplayName("Supabase disabled mode (primary PostgreSQL)")
class SupabaseDisabledModeIT {

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
        registry.add("vibegraph.supabase.enabled", () -> "false");
    }

    @Configuration
    @EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration"
    })
    @EntityScan("com.vibegraph.auth.domain.entity")
    @EnableJpaRepositories("com.vibegraph.auth.repository")
    @Import({
        SupabaseDatabaseConfig.class,
        JdbcNotificationRepository.class,
        JdbcAnnouncementRepository.class,
        JdbcRequestEventRepository.class,
        JdbcSecurityEventRepository.class,
        NotificationService.class,
        AdminSecurityMonitorService.class,
        CurrentUser.class
    })
    static class TestConfig {

        @Bean
        Clock testClock() {
            return Clock.systemUTC();
        }

        @Bean
        TransactionBoundaryProbe transactionBoundaryProbe(
                com.vibegraph.auth.repository.NotificationRepository notificationRepository,
                UserRepository userRepository) {
            return new TransactionBoundaryProbe(notificationRepository, userRepository);
        }
    }

    /**
     * Exercises a Supabase JDBC write and a JPA write inside one
     * {@code supabaseTransactionManager} transaction so rollback can be asserted across both.
     */
    static class TransactionBoundaryProbe {

        private final com.vibegraph.auth.repository.NotificationRepository notificationRepository;
        private final UserRepository userRepository;

        TransactionBoundaryProbe(
                com.vibegraph.auth.repository.NotificationRepository notificationRepository,
                UserRepository userRepository) {
            this.notificationRepository = notificationRepository;
            this.userRepository = userRepository;
        }

        @Transactional(transactionManager = "supabaseTransactionManager")
        public void markReadThenFail(UUID announcementId, UUID userId, User userToRename) {
            notificationRepository.markRead(announcementId, userId, "USER", Instant.now());
            userToRename.setDisplayName("renamed-before-rollback");
            userRepository.saveAndFlush(userToRename);
            throw new IllegalStateException("rollback both halves");
        }

        @Transactional(transactionManager = "supabaseTransactionManager", readOnly = true)
        public boolean readOnlyFlowBindsSingleResource(UUID userId) {
            notificationRepository.findActiveForUser(
                    userId, "USER", Instant.now(), org.springframework.data.domain.PageRequest.of(0, 10));
            userRepository.findById(userId);
            return TransactionSynchronizationManager.isActualTransactionActive();
        }
    }

    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired DataSource dataSource;
    @Autowired UserRepository userRepository;
    @Autowired NotificationService notificationService;
    @Autowired AdminSecurityMonitorService adminSecurityMonitorService;
    @Autowired TransactionBoundaryProbe transactionBoundaryProbe;
    @Autowired TransactionTemplate transactionTemplate;

    @Autowired
    @Qualifier("supabaseTransactionManager")
    PlatformTransactionManager supabaseTransactionManager;

    private UUID adminId;
    private UUID readerId;
    private UUID announcementId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM user_notifications");
        jdbcTemplate.update("DELETE FROM announcements");
        jdbcTemplate.update("DELETE FROM request_events");
        jdbcTemplate.update("DELETE FROM security_events");

        adminId = persistUser("announcer@vibegraph.test", "Announcer", Role.ADMIN);
        readerId = persistUser("reader@vibegraph.test", "Reader", Role.USER);
        announcementId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO announcements
                    (id, type, severity, target, title, body, dismissible, active, created_by_user_id)
                VALUES (?, 'GENERAL', 'INFO', 'ALL', 'Planned maintenance', 'Body', true, true, ?)
                """, announcementId, adminId);
        authenticateAs(readerId, "reader@vibegraph.test", Role.USER);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("supabaseTransactionManager delegates to the primary transaction manager")
    void supabaseTransactionManager_disabled_delegatesToPrimary() {
        PlatformTransactionManager primary =
                context.getBean("transactionManager", PlatformTransactionManager.class);

        assertThat(supabaseTransactionManager)
                .isInstanceOf(PrimaryDelegatingTransactionManager.class);
        assertThat(((PrimaryDelegatingTransactionManager) supabaseTransactionManager).delegate())
                .isSameAs(primary);
    }

    @Test
    @DisplayName("only one PlatformTransactionManager is a default autowire candidate")
    void transactionManagers_disabled_noDefaultCandidateAmbiguity() {
        assertThat(context.getBeanNamesForType(PlatformTransactionManager.class))
                .contains("transactionManager", "supabaseTransactionManager");

        // Resolution by type must stay unambiguous; supabaseTransactionManager is not a default
        // candidate, so this would throw NoUniqueBeanDefinitionException if that were lost.
        assertThat(context.getBean(PlatformTransactionManager.class))
                .isSameAs(context.getBean("transactionManager", PlatformTransactionManager.class));
    }

    @Test
    @DisplayName("notification list mixes JDBC query and JPA enrichment without resource conflict")
    void notificationList_disabled_joinsJdbcAndJpaInOneTransaction() {
        List<com.vibegraph.auth.dto.NotificationResponse> notifications = notificationService.list(10);

        assertThat(notifications).singleElement().satisfies(notification -> {
            assertThat(notification.announcementId()).isEqualTo(announcementId);
            assertThat(notification.creatorDisplayName()).isEqualTo("Announcer");
            assertThat(notification.creatorEmail()).isEqualTo("announcer@vibegraph.test");
            assertThat(notification.read()).isFalse();
        });
    }

    @Test
    @DisplayName("notification write and subsequent read observe the same transaction")
    void notificationMarkRead_disabled_writeThenReadInSameTransaction() {
        var updated = notificationService.markRead(announcementId);

        assertThat(updated.read()).isTrue();
        assertThat(updated.readAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_notifications WHERE user_id = ? AND read_at IS NOT NULL",
                Long.class, readerId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("admin security aggregate mixes Supabase repository rows and JPA user enrichment")
    void topUsers_disabled_enrichesAggregatesFromJpa() {
        jdbcTemplate.update("""
                INSERT INTO request_events
                    (id, user_id, ip_address, route, http_method, status, event_type, occurred_at)
                VALUES (?, ?, '203.0.113.7', '/api/projects', 'GET', 200, 'REQUEST', now())
                """, UUID.randomUUID(), readerId);

        var aggregates = adminSecurityMonitorService.topUsers(60, 10);

        assertThat(aggregates).singleElement().satisfies(row -> {
            assertThat(row.userId()).isEqualTo(readerId);
            assertThat(row.userDisplayName()).isEqualTo("Reader");
            assertThat(row.userEmail()).isEqualTo("reader@vibegraph.test");
            assertThat(row.requestsPerMinute()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("read-only Supabase transaction keeps a single active transaction")
    void readOnlyFlow_disabled_runsInsideOneActiveTransaction() {
        assertThat(transactionBoundaryProbe.readOnlyFlowBindsSingleResource(readerId)).isTrue();
        assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
    }

    @Test
    @DisplayName("failure rolls back both the JDBC write and the JPA write")
    void mixedWrite_disabled_rollsBackBothHalves() {
        User reader = userRepository.findById(readerId).orElseThrow();

        assertThatThrownBy(() -> transactionBoundaryProbe.markReadThenFail(
                announcementId, readerId, reader))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_notifications WHERE user_id = ?", Long.class, readerId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT display_name FROM users WHERE id = ?", String.class, readerId))
                .isEqualTo("Reader");
    }

    @Test
    @DisplayName("primary Flyway and JPA stay functional in disabled mode")
    void primaryPersistence_disabled_remainsFunctional() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Long.class))
                .isPositive();
        Long userCount = transactionTemplate.execute(status -> userRepository.count());
        assertThat(userCount).isEqualTo(2L);
    }

    private UUID persistUser(String email, String displayName, Role role) {
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, email, display_name, email_verified, role)
                VALUES (?, ?, ?, true, ?)
                """, id, email, displayName, role.name());
        return id;
    }

    private void authenticateAs(UUID id, String email, Role role) {
        AuthenticatedUser principal = new AuthenticatedUser(id, email, role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", List.of()));
    }
}
