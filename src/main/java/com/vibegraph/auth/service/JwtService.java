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
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;

/**
 * Issues and verifies stateless HS512 JWTs.
 *
 * <p>Token layout: {@code sub} = user id (UUID), claim {@code email}, claim {@code role}.
 * The signing key is derived from {@code vibegraph.auth.jwt.secret}; the service fails fast
 * at construction if the secret is shorter than 64 bytes (HS512 needs a 512-bit key).
 *
 * <p>Verification is signature + expiry; any failure throws {@link JwtException}, which the
 * caller (the auth filter) treats as an unauthenticated request.
 */
@Service
public class JwtService {

    private static final MacAlgorithm SIGNATURE_ALGORITHM = Jwts.SIG.HS512;
    private static final String SIGNATURE_ALGORITHM_ID = SIGNATURE_ALGORITHM.getId();
    private static final int MIN_SECRET_BYTES = 64;

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(JwtProperties properties) {
        String secret = properties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "vibegraph.auth.jwt.secret must be at least " + MIN_SECRET_BYTES
                            + " UTF-8 bytes (512-bit HS512 key). Set JWT_SECRET to a 64+ "
                            + "character ASCII secret or equivalent.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = properties.getExpirationMs();
    }

    /** Issue a signed token bound to a refresh session. */
    public String issue(User user, UUID sessionId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole() != null ? user.getRole().name() : Role.USER.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)));
        if (sessionId != null) {
            builder.claim("sid", sessionId.toString());
        }
        return builder.signWith(key, SIGNATURE_ALGORITHM).compact();
    }

    /**
     * Verify signature + expiry and extract the principal.
     *
     * @throws JwtException if the token is missing, malformed, expired, or has a bad signature
     */
    public AuthenticatedUser parse(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
        if (!SIGNATURE_ALGORITHM_ID.equals(jws.getHeader().getAlgorithm())) {
            throw new UnsupportedJwtException("JWT alg must be " + SIGNATURE_ALGORITHM_ID + ".");
        }
        Claims claims = jws.getPayload();
        UUID id = UUID.fromString(claims.getSubject());
        String email = claims.get("email", String.class);
        Role role = parseRole(claims.get("role", String.class));
        UUID sessionId = parseSessionId(claims.get("sid", String.class));
        return new AuthenticatedUser(id, email, role, sessionId);
    }

    /** Exposes the configured access lifetime for policy and diagnostics tests. */
    public long expirationMs() {
        return expirationMs;
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

    private UUID parseSessionId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new JwtException("Invalid JWT session id", ex) { };
        }
    }
}
