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

    /**
     * Upper bound for any configured lockout, 30 days.
     *
     * <p>Nothing legitimate needs a longer sign-in lockout, and bounding it here is what keeps every
     * later {@code now + duration} well clear of overflow.
     */
    private static final long MAX_SANE_LOCKOUT_MS = 30L * 24 * 60 * 60 * 1000;

    private final AbuseProperties.LoginThrottle settings;
    private final Clock clock;
    private final Cache<String, Attempts> attempts;
    private final long baseLockoutMs;
    private final long maxLockoutMs;

    public LoginThrottleGuard(AbuseProperties properties, Clock clock) {
        this.settings = properties.getLoginThrottle();
        this.clock = clock;
        // Bound the inputs once instead of chasing overflow through each arithmetic step. With an
        // unbounded ceiling the doubling wrapped negative, and even a correctly clamped duration
        // then overflowed `now + duration`, putting the unlock time in the past and switching the
        // lockout off entirely. Capping the ceiling removes the whole class.
        this.baseLockoutMs = Math.min(
                requirePositive(settings.getLockoutMs(), "lockout-ms"), MAX_SANE_LOCKOUT_MS);
        this.maxLockoutMs = Math.max(
                Math.min(requirePositive(settings.getMaxLockoutMs(), "max-lockout-ms"),
                        MAX_SANE_LOCKOUT_MS),
                this.baseLockoutMs);
        if (settings.getMaxLockoutMs() > MAX_SANE_LOCKOUT_MS) {
            log.warn("login-throttle.max-lockout-ms capped at {} ms; {} was configured",
                    MAX_SANE_LOCKOUT_MS, settings.getMaxLockoutMs());
        }
        // Entries must outlive the longest lockout, otherwise the escalation count would be
        // forgotten and every round would start again at the base wait.
        long retention = Math.max(settings.getWindowMs(), maxLockoutMs);
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
            return baseLockoutMs;
        }
        // Clamp BEFORE doubling, not after. `Math.min(duration * 2, max)` overflows on the
        // multiplication whenever max is large enough to keep the loop running — with an
        // unbounded max it wraps negative on the 44th round, and a negative duration puts
        // lockedUntilMs in the past, which silently turns the lockout off.
        long duration = baseLockoutMs;
        for (int i = 1; i < lockouts && duration < maxLockoutMs; i++) {
            duration = duration > maxLockoutMs / 2 ? maxLockoutMs : duration * 2;
        }
        return duration;
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

    private static long requirePositive(long value, String property) {
        if (value <= 0) {
            throw new IllegalStateException(
                    "vibegraph.abuse.login-throttle." + property + " must be positive");
        }
        return value;
    }

    /** Guarded by its own monitor; failures on one key can arrive from several threads at once. */
    private static final class Attempts {
        private int failures;
        private int lockouts;
        private long lockedUntilMs;
    }
}
