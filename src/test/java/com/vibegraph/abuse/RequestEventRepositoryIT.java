package com.vibegraph.abuse;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.vibegraph.auth.domain.SecurityEvent;
import com.vibegraph.auth.repository.SecurityEventRepository;
import com.vibegraph.common.supabase.SupabaseDatabaseConfig;
import com.vibegraph.common.supabase.repository.JdbcProjectRuntimeStatusRepository;
import com.vibegraph.common.supabase.repository.JdbcRequestEventRepository;
import com.vibegraph.common.supabase.repository.JdbcSecurityEventRepository;
import com.vibegraph.graph.repository.ProjectRuntimeStatusRepository;
import com.vibegraph.graph.websocket.ProjectStatusEvent;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RequestEventRepositoryIT.TestConfig.class)
@DisplayName("Request event aggregates (PostgreSQL)")
class RequestEventRepositoryIT {

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
        registry.add("vibegraph.supabase.enabled", () -> "true");
        registry.add("vibegraph.supabase.jdbc-url", POSTGRES::getJdbcUrl);
        registry.add("vibegraph.supabase.username", POSTGRES::getUsername);
        registry.add("vibegraph.supabase.password", POSTGRES::getPassword);
        registry.add("vibegraph.supabase.migration.jdbc-url", POSTGRES::getJdbcUrl);
        registry.add("vibegraph.supabase.migration.username", POSTGRES::getUsername);
        registry.add("vibegraph.supabase.migration.password", POSTGRES::getPassword);
        registry.add("vibegraph.supabase.require-separate-credentials", () -> "false");
        registry.add("vibegraph.supabase.schema", () -> "vibegraph_realtime");
    }

    @Configuration
    @EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration"
    })
    @Import({
        SupabaseDatabaseConfig.class,
        JdbcRequestEventRepository.class,
        JdbcSecurityEventRepository.class,
        JdbcProjectRuntimeStatusRepository.class
    })
    static class TestConfig {
    }

    @Autowired RequestEventRepository repository;
    @Autowired SecurityEventRepository securityEventRepository;
    @Autowired
    @Qualifier("supabaseJdbcTemplate")
    NamedParameterJdbcTemplate jdbcTemplate;
    @Autowired ProjectRuntimeStatusRepository runtimeStatusRepository;

    @BeforeEach
    void clearRequestEvents() {
        jdbcTemplate.getJdbcTemplate().update("DELETE FROM request_events");
        jdbcTemplate.getJdbcTemplate().update("DELETE FROM security_events");
    }

    @Test
    @DisplayName("saving a request event twice with the same id keeps one row and returns it")
    void saveRequestEvent_repeatedId_isIdempotent() {
        RequestEvent event = requestEvent(UUID.randomUUID());

        RequestEvent inserted = repository.save(event);
        RequestEvent replayed = repository.save(event);

        assertThat(inserted).isNotNull();
        assertThat(replayed).isNotNull();
        assertThat(replayed.getId()).isEqualTo(event.getId());
        assertThat(replayed.getRoute()).isEqualTo("/api/projects/{id}");
        assertThat(countRequestEvents(event.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("replaying a batch with already stored ids neither fails nor duplicates")
    void saveAllRequestEvents_repeatedIds_isIdempotent() {
        RequestEvent first = requestEvent(UUID.randomUUID());
        RequestEvent second = requestEvent(UUID.randomUUID());

        repository.saveAll(java.util.List.of(first, second));
        repository.saveAll(java.util.List.of(first, second));

        assertThat(countRequestEvents(first.getId())).isEqualTo(1L);
        assertThat(countRequestEvents(second.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("saving a security event twice with the same id keeps one row and returns it")
    void saveSecurityEvent_repeatedId_isIdempotent() {
        SecurityEvent event = securityEvent(UUID.randomUUID());

        SecurityEvent inserted = securityEventRepository.save(event);
        SecurityEvent replayed = securityEventRepository.save(event);

        assertThat(inserted).isNotNull();
        assertThat(replayed).isNotNull();
        assertThat(replayed.getId()).isEqualTo(event.getId());
        assertThat(replayed.getEventType()).isEqualTo("RATE_LIMIT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM security_events WHERE id = :id",
                java.util.Map.of("id", event.getId()), Long.class)).isEqualTo(1L);
    }

    @Test
    @DisplayName("replaying a security batch with already stored ids neither fails nor duplicates")
    void saveAllSecurityEvents_repeatedIds_isIdempotent() {
        SecurityEvent event = securityEvent(UUID.randomUUID());

        securityEventRepository.saveAll(java.util.List.of(event));
        securityEventRepository.saveAll(java.util.List.of(event, event));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM security_events WHERE id = :id",
                java.util.Map.of("id", event.getId()), Long.class)).isEqualTo(1L);
    }

    private RequestEvent requestEvent(UUID id) {
        return RequestEvent.builder()
                .id(id)
                .ipAddress("203.0.113.77")
                .route("/api/projects/{id}")
                .method("GET")
                .status(200)
                .eventType("REQUEST")
                .occurredAt(Instant.now())
                .build();
    }

    private SecurityEvent securityEvent(UUID id) {
        return SecurityEvent.builder()
                .id(id)
                .eventType("RATE_LIMIT")
                .severity("WARNING")
                .source("HTTP")
                .description("Request rate limit exceeded")
                .build();
    }

    private long countRequestEvents(UUID id) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM request_events WHERE id = :id",
                java.util.Map.of("id", id), Long.class);
    }

    @Test
    @DisplayName("top users keep API keys and no-key traffic in separate groups")
    void topUsers_groupsByUserAndApiKey() {
        UUID userId = UUID.randomUUID();
        seedRequests(userId, "key-1:vbg_ab12safe", "203.0.113.10", 2);
        seedRequests(userId, "key-2:vbg_cd34safe", "203.0.113.10", 1);
        seedRequests(userId, null, "203.0.113.10", 3);

        var rows = repository.topUsers(Instant.now().minusSeconds(300), PageRequest.of(0, 20));

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(RequestAggregateProjection::getApiKeyRef)
                .containsExactlyInAnyOrder("key-1:vbg_ab12safe", "key-2:vbg_cd34safe", null);
        assertThat(rows).map(RequestAggregateResponse::from)
                .extracting(RequestAggregateResponse::apiKeyRef)
                .containsExactlyInAnyOrder("vbg_ab12****", "vbg_cd34****", null);
        assertThat(rows).filteredOn(row -> "key-1:vbg_ab12safe".equals(row.getApiKeyRef()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getRequestCount()).isEqualTo(2);
                    assertThat(row.getUserId()).isEqualTo(userId);
                    assertThat(row.getUserDisplayName()).isNull();
                    assertThat(row.getUserEmail()).isNull();
                });
    }

    @Test
    @DisplayName("network aggregate includes distinct counts and user-key breakdown")
    void topIps_returnsCollapsedCountsAndBreakdown() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        seedRequests(adminId, null, "198.51.100.42", 3);
        seedRequests(userId, "key-1:vbg_ab12safe", "198.51.100.42", 2);

        Instant since = Instant.now().minusSeconds(300);
        var networks = repository.suspiciousNetworks(since, PageRequest.of(0, 20));
        var breakdown = repository.networkBreakdowns(since, java.util.List.of("198.51.100.42"));

        assertThat(networks).singleElement().satisfies(network -> {
            assertThat(network.getTotalRequests()).isEqualTo(5);
            assertThat(network.getUniqueUsers()).isEqualTo(2);
            assertThat(network.getUniqueApiKeys()).isEqualTo(1);
        });
        assertThat(breakdown).hasSize(2);
        assertThat(breakdown).anySatisfy(row -> {
            assertThat(row.getUserId()).isEqualTo(adminId);
            assertThat(row.getUserDisplayName()).isNull();
            assertThat(row.getApiKeyRef()).isNull();
            assertThat(row.getRequests()).isEqualTo(3);
        });
        assertThat(breakdown).map(SuspiciousNetworkBreakdownResponse::from)
                .extracting(SuspiciousNetworkBreakdownResponse::apiKeyRef)
                .containsExactlyInAnyOrder(null, "vbg_ab12****");
    }

    @Test
    @DisplayName("suspicious networks exclude internal and anonymous normal traffic")
    void suspiciousNetworks_filtersTelemetryNoise() {
        UUID userId = UUID.randomUUID();
        seedRequests(userId, null, "203.0.113.18", 2);
        seedRequests(null, null, "203.0.113.19", 20);
        seedRequests(null, null, "172.18.0.6", 20);
        seedRequests(userId, null, "172.18.0.1", 20);
        seedRequests(null, null, "0:0:0:0:0:0:0:1", 20);
        seedHealthchecks("198.51.100.77", 20);
        seedRateLimit(null, null, "198.51.100.99", 3);

        var networks = repository.suspiciousNetworks(Instant.now().minusSeconds(300), PageRequest.of(0, 20));

        assertThat(networks).extracting(NetworkAggregateProjection::getIpAddress)
                .containsExactlyInAnyOrder("203.0.113.19", "203.0.113.18", "198.51.100.99");

        var breakdown = repository.networkBreakdowns(Instant.now().minusSeconds(300),
                java.util.List.of("198.51.100.77", "203.0.113.19"));
        assertThat(breakdown).extracting(NetworkBreakdownProjection::getIpAddress)
                .containsExactly("203.0.113.19");
    }

    @Test
    @DisplayName("runtime status keeps one latest row per project")
    void runtimeStatus_upsertsLatestValue() {
        Instant first = Instant.parse("2026-08-08T10:00:00Z");
        Instant second = first.plusSeconds(30);
        runtimeStatusRepository.upsert(new ProjectStatusEvent(
                "project-1", "ANALYZING", 20, null, first));
        runtimeStatusRepository.upsert(new ProjectStatusEvent(
                "project-1", "ANALYZED", 100, "Complete", second));

        var row = jdbcTemplate.queryForMap(
                "SELECT status, progress, message FROM project_runtime_status WHERE project_id = :id",
                java.util.Map.of("id", "project-1"));
        assertThat(row).containsEntry("status", "ANALYZED")
                .containsEntry("progress", 100)
                .containsEntry("message", "Complete");
    }

    private void seedRequests(UUID userId, String apiKeyRef, String ipAddress, int count) {
        for (int index = 0; index < count; index++) {
            jdbcTemplate.getJdbcTemplate().update("""
                    INSERT INTO request_events
                        (user_id, api_key_ref, ip_address, route, http_method, status, event_type, occurred_at)
                    VALUES (?, ?, ?, '/api/projects', 'GET', 200, 'REQUEST', now())
                    """, userId, apiKeyRef, ipAddress);
        }
    }

    private void seedRateLimit(UUID userId, String apiKeyRef, String ipAddress, int count) {
        for (int index = 0; index < count; index++) {
            jdbcTemplate.getJdbcTemplate().update("""
                    INSERT INTO request_events
                        (user_id, api_key_ref, ip_address, route, http_method, status, event_type, occurred_at)
                    VALUES (?, ?, ?, '/api/login', 'POST', 429, 'RATE_LIMIT', now())
                    """, userId, apiKeyRef, ipAddress);
        }
    }

    private void seedHealthchecks(String ipAddress, int count) {
        for (int index = 0; index < count; index++) {
            jdbcTemplate.getJdbcTemplate().update("""
                    INSERT INTO request_events
                        (ip_address, route, http_method, status, event_type, occurred_at)
                    VALUES (?, '/actuator/health', 'GET', 200, 'REQUEST', now())
                    """, ipAddress);
        }
    }
}
