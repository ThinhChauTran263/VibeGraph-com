package com.vibegraph.abuse;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
    }

    @Configuration
    @EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration"
    })
    @EntityScan("com.vibegraph.abuse")
    @EnableJpaRepositories("com.vibegraph.abuse")
    static class TestConfig {
    }

    @Autowired RequestEventRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearRequestEvents() {
        jdbcTemplate.update("DELETE FROM request_events");
    }

    @Test
    @DisplayName("top users keep API keys and no-key traffic in separate groups")
    void topUsers_groupsByUserAndApiKey() {
        UUID userId = UUID.randomUUID();
        String email = "admin-" + userId + "@example.com";
        seedUser(userId, "VibeGraph Admin", email);
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
                    assertThat(row.getUserDisplayName()).isEqualTo("VibeGraph Admin");
                    assertThat(row.getUserEmail()).isEqualTo(email);
                });
    }

    @Test
    @DisplayName("network aggregate includes distinct counts and user-key breakdown")
    void topIps_returnsCollapsedCountsAndBreakdown() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        seedUser(adminId, "VibeGraph Admin", "admin-" + adminId + "@example.com");
        seedUser(userId, "VibeGraph User", "user-" + userId + "@example.com");
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
            assertThat(row.getUserDisplayName()).isEqualTo("VibeGraph Admin");
            assertThat(row.getApiKeyRef()).isNull();
            assertThat(row.getRequests()).isEqualTo(3);
        });
        assertThat(breakdown).map(SuspiciousNetworkBreakdownResponse::from)
                .extracting(SuspiciousNetworkBreakdownResponse::apiKeyRef)
                .containsExactlyInAnyOrder(null, "vbg_ab12****");
    }

    private void seedUser(UUID userId, String displayName, String email) {
        jdbcTemplate.update("INSERT INTO users (id, email, display_name) VALUES (?, ?, ?)",
                userId, email, displayName);
    }

    private void seedRequests(UUID userId, String apiKeyRef, String ipAddress, int count) {
        for (int index = 0; index < count; index++) {
            jdbcTemplate.update("""
                    INSERT INTO request_events
                        (user_id, api_key_ref, ip_address, route, http_method, status, event_type, occurred_at)
                    VALUES (?, ?, ?, '/api/projects', 'GET', 200, 'REQUEST', now())
                    """, userId, apiKeyRef, ipAddress);
        }
    }
}
