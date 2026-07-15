package com.vibegraph.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code vibegraph.auth.jwt.*}.
 *
 * <ul>
 *   <li>{@code secret} — HS256 signing secret. MUST be at least 32 characters (256 bits);
 *       {@link com.vibegraph.auth.service.JwtService} fails fast at startup otherwise.</li>
 *   <li>{@code expirationMs} — token lifetime in milliseconds (default 24h).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "vibegraph.auth.jwt")
@Getter
@Setter
public class JwtProperties {

    /** HS256 signing secret (>=32 chars). Read from env JWT_SECRET; never logged. */
    private String secret;

    /** Token lifetime in milliseconds. */
    private long expirationMs = 86_400_000L;
}
