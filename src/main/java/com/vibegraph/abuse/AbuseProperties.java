package com.vibegraph.abuse;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "vibegraph.abuse")
@Validated
public class AbuseProperties {

    @Min(0)
    private int concurrentImportsPerUser = 1;
    @Min(0)
    private int requestsPerMinutePerIp = 120;
    @Min(0)
    private int requestsPerMinutePerUser = 240;
    @Min(0)
    private int requestsPerMinutePerApiKey = 240;
    private boolean trustProxy = false;
    private List<String> trustedProxies = List.of();

    @Valid
    private final RateLimit rateLimit = new RateLimit();

    /** Credential-stuffing controls for the sign-in endpoint. */
    private final LoginThrottle loginThrottle = new LoginThrottle();

    public LoginThrottle getLoginThrottle() {
        return loginThrottle;
    }

    /**
     * Failure budgets for {@code POST /api/auth/login}.
     *
     * <p>The general request rate limit is far too generous to protect a password: 120 requests a
     * minute per IP is 120 guesses a minute. These budgets count only <b>failed</b> attempts, so a
     * user who signs in normally never touches them.
     *
     * <p>Two independent counters, because they stop different attacks. The per-IP budget stops one
     * host spraying many accounts; the per-account budget stops a distributed attack converging on
     * one account from many addresses.
     *
     * <p>Enforcement is per instance, like the request rate limiter: N replicas allow up to N times
     * these budgets. That is a real weakening against a distributed attacker and is the reason to
     * keep the numbers conservative.
     */
    public static class LoginThrottle {

        /** Failed attempts from one address before it is locked out. */
        private int maxFailuresPerIp = 10;

        /** Failed attempts against one account before it is locked out, from any address. */
        private int maxFailuresPerAccount = 5;

        /** How long failures are remembered; a quiet window lets the count lapse. */
        private long windowMs = 900_000L;

        /** How long a caller is refused once a budget is exhausted. */
        private long lockoutMs = 900_000L;

        /**
         * Ceiling for the escalating address lockout.
         *
         * <p>Each further lockout from the same address doubles the wait, so a source that keeps
         * grinding becomes uneconomic quickly. Applies to addresses only — see
         * {@code LoginThrottleGuard} for why the account lockout stays flat.
         */
        private long maxLockoutMs = 86_400_000L;

        /** Bounds the counter cache so a spray across many addresses cannot exhaust memory. */
        private int maximumTrackedKeys = 100_000;

        public int getMaxFailuresPerIp() {
            return maxFailuresPerIp;
        }

        public void setMaxFailuresPerIp(int maxFailuresPerIp) {
            this.maxFailuresPerIp = maxFailuresPerIp;
        }

        public int getMaxFailuresPerAccount() {
            return maxFailuresPerAccount;
        }

        public void setMaxFailuresPerAccount(int maxFailuresPerAccount) {
            this.maxFailuresPerAccount = maxFailuresPerAccount;
        }

        public long getWindowMs() {
            return windowMs;
        }

        public void setWindowMs(long windowMs) {
            this.windowMs = windowMs;
        }

        public long getLockoutMs() {
            return lockoutMs;
        }

        public void setLockoutMs(long lockoutMs) {
            this.lockoutMs = lockoutMs;
        }

        public long getMaxLockoutMs() {
            return maxLockoutMs;
        }

        public void setMaxLockoutMs(long maxLockoutMs) {
            this.maxLockoutMs = maxLockoutMs;
        }

        public int getMaximumTrackedKeys() {
            return maximumTrackedKeys;
        }

        public void setMaximumTrackedKeys(int maximumTrackedKeys) {
            this.maximumTrackedKeys = maximumTrackedKeys;
        }
    }

    /**
     * Bounds for the in-memory rate-limit window store.
     *
     * <p>Enforcement is per instance: every replica keeps its own windows, so the effective
     * allowance across a deployment grows with the replica count. This is not a cluster-wide
     * limiter.
     */
    @Getter
    @Setter
    public static class RateLimit {

        /**
         * Maximum number of tracked windows held by one instance. Once reached, Caffeine evicts
         * the least recently used windows, so enforcement degrades to best-effort under
         * cardinality pressure instead of growing the heap without bound.
         */
        @Min(100)
        private int windowMaximumSize = 100_000;

        /** How long a window is retained after its last update before it expires. */
        @Min(30_000)
        private long windowTtlMs = 180_000;
    }
}
