package com.vibegraph.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.vibegraph.auth.config.JwtProperties;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and verifies stateless HS256 JWTs.
 *
 * <p>Token layout: {@code sub} = user id (UUID), claim {@code email}, claim {@code role}.
 * The signing key is derived from {@code vibegraph.auth.jwt.secret}; the service fails fast
 * at construction if the secret is shorter than 32 bytes (HS256 needs a 256-bit key).
 *
 * <p>Verification is signature + expiry; any failure throws {@link JwtException}, which the
 * caller (the auth filter) treats as an unauthenticated request.
 */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(JwtProperties properties) {
        String secret = properties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "vibegraph.auth.jwt.secret must be at least " + MIN_SECRET_BYTES
                            + " characters (256-bit HS256 key). Set a longer JWT_SECRET.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = properties.getExpirationMs();
    }

    /** Issue a signed token for the given user. */
    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole() != null ? user.getRole().name() : Role.USER.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    /**
     * Verify signature + expiry and extract the principal.
     *
     * @throws JwtException if the token is missing, malformed, expired, or has a bad signature
     */
    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        UUID id = UUID.fromString(claims.getSubject());
        String email = claims.get("email", String.class);
        Role role = parseRole(claims.get("role", String.class));
        return new AuthenticatedUser(id, email, role);
    }

    private Role parseRole(String raw) {
        if (raw == null) {
            return Role.USER;
        }
        try {
            return Role.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return Role.USER;
        }
    }
}
