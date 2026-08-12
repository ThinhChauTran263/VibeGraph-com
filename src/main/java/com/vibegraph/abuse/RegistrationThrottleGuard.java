package com.vibegraph.abuse;

import java.time.Clock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vibegraph.common.exception.TooManyLoginAttemptsException;

/**
 * Caps how many accounts one address may create.
 *
 * <p>Separate from {@link LoginThrottleGuard} because the two measure different things. Sign-in
 * counts failures and locks the account as well as the address; sign-up counts <b>every</b> attempt
 * and only ever keys on the address, since there is no account to key on until the request succeeds.
 *
 * <p>A fixed window, not the escalating lockout used for sign-in. Escalation there punishes a source
 * that keeps guessing at one account; here the wrong behaviour is bulk creation, and a plain ceiling
 * per hour expresses that directly.
 *
 * <p>State is per instance and in memory, like the other guards in this package: N replicas allow N
 * times the budget and a restart forgets every counter.
 */
@Component
public class RegistrationThrottleGuard {

    private static final Logger log = LoggerFactory.getLogger(RegistrationThrottleGuard.class);

    private final AbuseProperties.RegistrationThrottle settings;
    private final Clock clock;
    private final Cache<String, Window> windows;

    public RegistrationThrottleGuard(AbuseProperties properties, Clock clock) {
        this.settings = properties.getRegistrationThrottle();
        this.clock = clock;
        this.windows = Caffeine.newBuilder()
                .maximumSize(Math.max(1, settings.getMaximumTrackedKeys()))
                // Ticker in nanoseconds, driven by the injected clock so tests control expiry.
                .ticker(() -> clock.millis() * 1_000_000L)
                .expireAfterWrite(settings.getWindowMs(), TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * Count this sign-up attempt and refuse it once the address is over budget.
     *
     * <p>Counted before the account is created, so a caller cannot spend the request and then be
     * told about the limit.
     */
    public void assertAllowed(String clientIp) {
        String key = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        Window window = windows.get(key, ignored -> new Window(clock.millis()));
        int used = window.count.incrementAndGet();
        if (used <= settings.getMaxPerIp()) {
            return;
        }
        long resetAt = window.startedAtMs + settings.getWindowMs();
        long remaining = Math.max(1, resetAt - clock.millis());
        if (used == settings.getMaxPerIp() + 1) {
            log.warn("Sign-up budget exhausted. clientIp={}, attempts={}", key, used);
        }
        throw new TooManyLoginAttemptsException((remaining + 999) / 1000);
    }

    private static final class Window {
        private final AtomicInteger count = new AtomicInteger();
        private final long startedAtMs;

        private Window(long startedAtMs) {
            this.startedAtMs = startedAtMs;
        }
    }
}
