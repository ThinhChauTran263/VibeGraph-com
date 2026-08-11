package com.vibegraph.auth.service;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.vibegraph.auth.config.JwtProperties;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.User;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSessionClaimTest {

    @Test
    void issue_withSessionId_roundTripsSessionIdAndThirtyMinuteExpiry() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("a".repeat(64));
        properties.setExpirationMs(1_800_000L);
        JwtService service = new JwtService(properties);
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("session@test.local")
                .role(Role.USER)
                .build();
        UUID sessionId = UUID.randomUUID();

        String token = service.issue(user, sessionId);
        AuthenticatedUser parsed = service.parse(token);

        assertThat(parsed.sessionId()).isEqualTo(sessionId);
        assertThat(service.expirationMs()).isEqualTo(1_800_000L);
    }
}
