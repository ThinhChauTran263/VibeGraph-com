package com.vibegraph.abuse;

import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vibegraph.common.exception.TooManyLoginAttemptsException;

/**
 * Failure budget for the sign-in endpoint.
 *
 * <p>The general rate limiter allows 120 requests a minute per address, which is 120 password
 * guesses a minute — no protection for a weak password. This counts <b>failures only</b>, so normal
 * sign-ins never consume budget, and it counts them twice over: once for the calling address and
 * once for the targeted account.
 *
 * <p>Both counters are needed. Per address stops one host spraying many accounts; per account stops
 * a botnet converging on one account from many addresses. Either budget alone leaves the other
 * attack open.
 *
 * <p><b>Only the address lockout escalates.</b> Repeated lockouts from one address double the wait,
 * up to a ceiling, because punishing a persistent source is safe. The account lockout deliberately
 * stays flat: anyone who knows a victim's email can fail on purpose, so an escalating account
 * lockout would hand an attacker a way to lock a real user out for a day. A flat window keeps
 * guessing slow without turning the defence into a denial-of-service tool.
 *
 * <p>State is per instance and in memory, exactly like {@link RateLimitFilter}. Running N replicas
 * multiplies the effective budget by N, and a restart forgets every counter. That is a genuine
 * weakening against a determined distributed attacker; a shared store would be the fix, and is
 * deliberately out of scope here.
 */
@Component
public class LoginThrottleGuard {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottleGuard.class);

    private final AbuseProperties.LoginThrottle settings;
    private final Clock clock;
    private final Cache<String, Attempts> attempts;

    public LoginThrottleGuard(AbuseProperties properties, Clock clock) {
        this.settings = properties.getLoginThrottle();
        this.clock = clock;
        // Entries must outlive the longest lockout, otherwise the escalation count would be
        // forgotten and every round would start again at the base wait.
        long retention = Math.max(settings.getWindowMs(), settings.getMaxLockoutMs());
        this.attempts = Caffeine.newBuilder()
                .maximumSize(Math.max(1, settings.getMaximumTrackedKeys()))
                // Ticker in nanoseconds, driven by the injected clock so tests control expiry.
                .ticker(() -> clock.millis() * 1_000_000L)
                .expireAfterWrite(retention, TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * Refuse the attempt when either the address or the account is locked out.
     *
     * <p>Called before the password is checked, so a locked-out caller cannot keep testing guesses
     * and cannot measure how long verification takes.
     */
    public void assertAllowed(String clientIp, String email) {
        long remaining = Math.max(remainingLockMs(ipKey(clientIp)), remainingLockMs(accountKey(email)));
        if (remaining > 0) {
            throw new TooManyLoginAttemptsException((remaining + 999) / 1000);
        }
    }

    /** Charge a failed attempt to both the address and the account. */
    public void recordFailure(String clientIp, String email) {
        boolean ipLocked = registerFailure(ipKey(clientIp), settings.getMaxFailuresPerIp(), true);
        boolean accountLocked =
                registerFailure(accountKey(email), settings.getMaxFailuresPerAccount(), false);
        if (ipLocked || accountLocked) {
            // Log the address but never the email: this line would otherwise become a list of
            // valid-looking account names in the log file.
            log.warn("Sign-in lockout engaged. clientIp={}, addressLocked={}, accountLocked={}",
                    clientIp, ipLocked, accountLocked);
        }
    }

    /**
     * Clear both counters after a successful sign-in.
     *
     * <p>Without this, a user who mistypes their password a few times and then succeeds would stay
     * one slip away from being locked out for the rest of the window.
     */
    public void recordSuccess(String clientIp, String email) {
        attempts.invalidate(ipKey(clientIp));
        attempts.invalidate(accountKey(email));
    }

    /**
     * Count one failure against a key and lock it out when the budget is spent.
     *
     * @param escalating whether repeated lockouts on this key should double the wait
     * @return {@code true} when this failure is the one that engaged a lockout
     */
    private boolean registerFailure(String key, int budget, boolean escalating) {
        Attempts state = attempts.get(key, ignored -> new Attempts());
        synchronized (state) {
            state.failures++;
            if (state.failures < budget) {
                return false;
            }
            state.failures = 0;
            state.lockouts++;
            state.lockedUntilMs = clock.millis() + lockoutDuration(state.lockouts, escalating);
            return true;
        }
    }

    private long lockoutDuration(int lockouts, boolean escalating) {
        if (!escalating) {
            return settings.getLockoutMs();
        }
        // Doubling, guarded against overflow on a long-lived key rather than trusting the shift.
        long duration = settings.getLockoutMs();
        for (int i = 1; i < lockouts && duration < settings.getMaxLockoutMs(); i++) {
            duration = Math.min(duration * 2, settings.getMaxLockoutMs());
        }
        return Math.min(duration, settings.getMaxLockoutMs());
    }

    private long remainingLockMs(String key) {
        Attempts state = attempts.getIfPresent(key);
        if (state == null) {
            return 0;
        }
        synchronized (state) {
            return Math.max(0, state.lockedUntilMs - clock.millis());
        }
    }

    private String ipKey(String clientIp) {
        return "ip:" + (clientIp == null || clientIp.isBlank() ? "unknown" : clientIp);
    }

    /** Normalised so casing or padding cannot buy an attacker a fresh budget. */
    private String accountKey(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return "account:" + normalized;
    }

    /** Guarded by its own monitor; failures on one key can arrive from several threads at once. */
    private static final class Attempts {
        private int failures;
        private int lockouts;
        private long lockedUntilMs;
    }
}
