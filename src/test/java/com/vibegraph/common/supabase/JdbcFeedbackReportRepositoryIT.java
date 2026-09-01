package com.vibegraph.common.supabase;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.vibegraph.auth.domain.entity.FeedbackReport;
import com.vibegraph.auth.repository.FeedbackReportRepository;
import com.vibegraph.common.supabase.repository.JdbcFeedbackReportRepository;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = JdbcFeedbackReportRepositoryIT.TestConfig.class)
@DisplayName("Feedback report repository (PostgreSQL)")
class JdbcFeedbackReportRepositoryIT {

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
        registry.add("vibegraph.supabase.schema", () -> "vibegraph_realtime");
        registry.add("vibegraph.supabase.require-separate-credentials", () -> "false");
        registry.add("vibegraph.supabase.migration.jdbc-url", POSTGRES::getJdbcUrl);
        registry.add("vibegraph.supabase.migration.username", POSTGRES::getUsername);
        registry.add("vibegraph.supabase.migration.password", POSTGRES::getPassword);
    }

    @Configuration
    @EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration"
    })
    @Import({SupabaseDatabaseConfig.class, JdbcFeedbackReportRepository.class})
    static class TestConfig {
    }

    @Autowired
    FeedbackReportRepository repository;

    @Autowired
    @Qualifier("supabaseJdbcTemplate")
    NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearReports() {
        jdbcTemplate.getJdbcTemplate().update("DELETE FROM feedback_messages");
        jdbcTemplate.getJdbcTemplate().update("DELETE FROM feedback_reports");
    }

    @Test
    @DisplayName("null status and query filters return reports without an untyped SQL parameter")
    void findAllWithFilters_nullFilters_returnsReports() {
        UUID reportId = UUID.randomUUID();
        jdbcTemplate.getJdbcTemplate().update("""
                INSERT INTO feedback_reports (id, status, category, title)
                VALUES (?, 'OPEN', 'BUG', ?)
                """, reportId, "Report visible to admin");

        Page<FeedbackReport> page = repository.findAllWithFilters(
                null, null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).singleElement().satisfies(report -> {
            assertThat(report.getId()).isEqualTo(reportId);
            assertThat(report.getTitle()).isEqualTo("Report visible to admin");
        });
    }
}
