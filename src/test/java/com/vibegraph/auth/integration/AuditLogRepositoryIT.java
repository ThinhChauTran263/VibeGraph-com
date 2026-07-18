package com.vibegraph.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.vibegraph.auth.repository.AuditLogRepository;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = AuditLogRepositoryIT.TestConfig.class)
@DisplayName("Audit log repository (PostgreSQL)")
class AuditLogRepositoryIT {

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

    @Autowired AuditLogRepository auditLogRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("listing without optional filters works on PostgreSQL")
    void listWithoutOptionalFilters_succeeds() {
        jdbcTemplate.update("""
                INSERT INTO audit_logs (action, outcome, target_type, target_id, details)
                VALUES ('LOGIN', 'SUCCESS', 'USER', 'admin@vibegraph.com', '{}')
                """);

        var result = auditLogRepository.findAll(
                (root, query, criteriaBuilder) -> criteriaBuilder.conjunction(),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(result.getContent())
                .hasSize(1)
                .first()
                .satisfies(log -> assertThat(log.getAction()).isEqualTo("LOGIN"));
    }
}
