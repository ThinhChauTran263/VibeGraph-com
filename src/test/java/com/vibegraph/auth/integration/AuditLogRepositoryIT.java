package com.vibegraph.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.auth.repository.AuditLogRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.auth.service.AuditLogEventPublisher;
import com.vibegraph.auth.service.AuditLogEventStream;
import com.vibegraph.auth.service.AuditLogWriter;
import com.vibegraph.auth.service.AuditRedactor;

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
        @Bean
        AuditRedactor auditRedactor() {
            return new AuditRedactor(new ObjectMapper());
        }

        @Bean
        AuditLogEventStream auditLogEventStream() {
            return new AuditLogEventStream();
        }

        @Bean
        AuditLogEventPublisher auditLogEventPublisher(AuditLogEventStream stream) {
            return new AuditLogEventPublisher(stream, java.time.Clock.systemUTC());
        }

        @Bean
        AuditLogWriter auditLogWriter(
                AuditLogRepository repository, UserRepository userRepository, AuditRedactor redactor,
                AuditLogEventPublisher publisher) {
            return new AuditLogWriter(repository, userRepository, redactor, publisher);
        }
    }

    @Autowired AuditLogRepository auditLogRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired AuditLogWriter auditLogWriter;

    @Test
    @DisplayName("audit survives rollback of the surrounding business transaction")
    void write_outerTransactionRollsBack_auditRemainsCommitted() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                transactionTemplate.executeWithoutResult(status -> {
                    auditLogWriter.write(
                            "PLAN_DELETE", null, null, "PLAN", "LEGACY", "SUCCESS", null,
                            java.util.Map.of("operation", "DELETE"));
                    throw new IllegalStateException("rollback business transaction");
                }));

        assertThat(auditLogRepository.findAll())
                .anySatisfy(log -> assertThat(log.getAction()).isEqualTo("PLAN_DELETE"));
    }

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
                .anySatisfy(log -> assertThat(log.getAction()).isEqualTo("LOGIN"));
    }
}
