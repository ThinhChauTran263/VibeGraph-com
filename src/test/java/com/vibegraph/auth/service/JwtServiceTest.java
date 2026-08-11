package com.vibegraph.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vibegraph.auth.config.JwtProperties;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtService")
class JwtServiceTest {

    private static final String SECRET = "a".repeat(64);

    @Test
    @DisplayName("issues HS512 token and parses existing claims")
    void issueAndParse_validToken_returnsAuthenticatedUser() {
        JwtService jwtService = new JwtService(properties(SECRET));
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@test.local")
                .role(Role.ADMIN)
                .build();

        String token = jwtService.issue(user);

        Jws<Claims> verified = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token);
        assertThat(verified.getHeader().getAlgorithm()).isEqualTo("HS512");
        assertThat(verified.getPayload().getSubject()).isEqualTo(userId.toString());
        assertThat(verified.getPayload().get("email", String.class)).isEqualTo("user@test.local");
        assertThat(verified.getPayload().get("role", String.class)).isEqualTo("ADMIN");

        AuthenticatedUser parsed = jwtService.parse(token);

        assertThat(parsed.id()).isEqualTo(userId);
        assertThat(parsed.email()).isEqualTo("user@test.local");
        assertThat(parsed.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("fails fast when HS512 secret is too short")
    void constructor_shortSecret_throwsClearMessage() {
        assertThatThrownBy(() -> new JwtService(properties("a".repeat(63))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 64 UTF-8 bytes")
                .hasMessageContaining("HS512")
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("rejects tampered token")
    void parse_tamperedToken_throwsJwtException() {
        JwtService jwtService = new JwtService(properties(SECRET));
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@test.local")
                .role(Role.USER)
                .build();
        String token = jwtService.issue(user);

        assertThatThrownBy(() -> jwtService.parse(tamper(token)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("rejects non-HS512 token signed with same secret")
    void parse_hs256Token_throwsJwtException() {
        JwtService jwtService = new JwtService(properties(SECRET));
        UUID userId = UUID.randomUUID();
        String token = Jwts.builder()
                .subject(userId.toString())
                .claim("email", "user@test.local")
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parse(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("HS512");
    }

    private static JwtProperties properties(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setExpirationMs(86_400_000L);
        return properties;
    }

    private static String tamper(String token) {
        String[] segments = token.split("\\.", -1);
        String signature = segments[2];
        int index = signature.length() / 2;
        char current = signature.charAt(index);
        char replacement = current == 'A' ? 'B' : 'A';
        segments[2] = signature.substring(0, index) + replacement + signature.substring(index + 1);
        return String.join(".", segments);
    }
}
