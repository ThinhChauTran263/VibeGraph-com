package com.vibegraph.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code vibegraph.auth.jwt.*}.
 *
 * <ul>
 *   <li>{@code secret} — HS512 signing secret. MUST be at least 64 UTF-8 bytes (512 bits);
 *       {@link com.vibegraph.auth.service.JwtService} fails fast at startup otherwise.</li>
 *   <li>{@code expirationMs} — access-token lifetime in milliseconds (default 30m).</li>
 *   <li>{@code refreshExpirationMs} — absolute refresh-session lifetime (default 7d).</li>
 *   <li>{@code secureCookies} — force the Secure flag on auth cookies in production.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "vibegraph.auth.jwt")
@Getter
@Setter
public class JwtProperties {

    /** HS512 signing secret (>=64 UTF-8 bytes). Read from env JWT_SECRET; never logged. */
    private String secret;

    /** Access-token lifetime in milliseconds. */
    private long expirationMs = 1_800_000L;

    /** Absolute refresh-session lifetime in milliseconds. */
    private long refreshExpirationMs = 604_800_000L;

    /**
     * How long a just-rotated refresh token still answers a concurrent refresh.
     *
     * <p>Without this window, two browser tabs refreshing at the same moment look exactly like a
     * stolen-token replay: one rotates first, the second presents the now-rotated token and the
     * whole family gets revoked, signing the user out everywhere. Inside the window the second
     * caller is handed its own sibling token instead. Outside it, replay detection is unchanged.
     *
     * <p>Keep this small — it is the period in which a genuinely stolen token can still be used.
     */
    private long refreshGraceMs = 30_000L;

    /**
     * How long expired refresh sessions are kept before the sweep deletes them.
     *
     * <p>Every rotation inserts a row, so without this the table grows without bound. The window
     * exists only so a recent incident can still be investigated; the rows are unusable once
     * {@code expiresAt} has passed.
     */
    private int refreshRetentionDays = 30;

    /** Force Secure on cookies even when the request itself is not marked HTTPS. */
    private boolean secureCookies;

    /** Cookie SameSite mode; production normally uses Lax or Strict. */
    private String sameSite = "Lax";
}
