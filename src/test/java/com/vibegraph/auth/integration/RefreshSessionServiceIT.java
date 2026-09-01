package com.vibegraph.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.config.JwtProperties;
import com.vibegraph.auth.domain.entity.RefreshSession;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.entity.User;
import com.vibegraph.auth.repository.RefreshSessionRepository;
import com.vibegraph.auth.repository.UserIdentityRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.AuditService;
import com.vibegraph.auth.service.AuthService;
import com.vibegraph.auth.service.FeatureGateService;
import com.vibegraph.auth.service.JwtService;
import com.vibegraph.auth.service.RefreshSessionService;
import com.vibegraph.common.exception.UnauthorizedException;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@SpringBootTest(
        classes = RefreshSessionServiceIT.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("Refresh session service (PostgreSQL)")
class RefreshSessionServiceIT {

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

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration"
    })
    @EntityScan(basePackageClasses = User.class)
    @EnableJpaRepositories(basePackageClasses = RefreshSessionRepository.class)
    static class TestConfig {

        @Bean
        JwtProperties jwtProperties() {
            JwtProperties properties = new JwtProperties();
            properties.setRefreshExpirationMs(604_800_000L);
            // This test replays a token the instant after it was rotated — which is also exactly
            // what two browser tabs refreshing together look like. The grace window exists so that
            // case is not punished, so it is disabled here to isolate what this test is actually
            // about: the revocation must COMMIT rather than roll back with the 401, and only a real
            // database can prove that. The grace path itself is covered by unit tests, which can
            // control the clock.
            properties.setRefreshGraceMs(0L);
            return properties;
        }

        @Bean
        AccountSettingsService accountSettingsService() {
            return mock(AccountSettingsService.class);
        }

        @Bean
        RefreshSessionService refreshSessionService(
                RefreshSessionRepository repository,
                UserRepository userRepository,
                AccountSettingsService accountSettingsService,
                JwtProperties properties) {
            return new RefreshSessionService(
                    repository, userRepository, accountSettingsService, properties);
        }

        @Bean
        AuthService authService(
                UserRepository userRepository,
                UserIdentityRepository userIdentityRepository,
                AccountSettingsService accountSettingsService,
                RefreshSessionService refreshSessionService) {
            return new AuthService(
                    userRepository,
                    userIdentityRepository,
                    mock(PasswordEncoder.class),
                    mock(JwtService.class),
                    mock(CurrentUser.class),
                    accountSettingsService,
                    mock(FeatureGateService.class),
                    mock(AuditService.class),
                    refreshSessionService);
        }
    }

    @Autowired
    private RefreshSessionService service;

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshSessionRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("replaying a rotated token commits revocation of its replacement family")
    void refreshSession_replayedToken_commitsFamilyRevocation() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("refresh-replay@test.local")
                .passwordHash("unused")
                .role(Role.USER)
                .build());
        RefreshSessionService.SessionToken original = service.issue(user);
        RefreshSessionService.RotatedSession rotated = service.rotate(original.rawToken());

        assertThat(service.isAccessSessionActive(rotated.token().sessionId(), user.getId())).isTrue();

        assertThatThrownBy(() -> authService.refreshSession(original.rawToken()))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(service.isAccessSessionActive(rotated.token().sessionId(), user.getId())).isFalse();
        RefreshSession replacement = repository.findById(rotated.token().sessionId()).orElseThrow();
        assertThat(replacement.getRevokeReason()).isEqualTo("REUSE_DETECTED");
        assertThat(replacement.getRevokedAt()).isNotNull();
    }
}
