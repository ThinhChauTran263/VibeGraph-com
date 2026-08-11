package com.vibegraph.abuse;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vibegraph.common.exception.TooManyLoginAttemptsException;

/**
 * Unit tests for the sign-in failure budget.
 *
 * <p>Uses the shared {@link MutableClock} so lockout expiry can be tested without sleeping.
 */
@DisplayName("Login throttle guard")
class LoginThrottleGuardTest {

    private static final String IP = "203.0.113.10";
    private static final String EMAIL = "victim@test.local";

    private MutableClock clock;
    private AbuseProperties properties;
    private LoginThrottleGuard guard;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-08-11T09:00:00Z"));
        properties = new AbuseProperties();
        properties.getLoginThrottle().setMaxFailuresPerIp(10);
        properties.getLoginThrottle().setMaxFailuresPerAccount(5);
        properties.getLoginThrottle().setWindowMs(900_000L);
        properties.getLoginThrottle().setLockoutMs(900_000L);
        guard = new LoginThrottleGuard(properties, clock);
    }

    private void fail(String ip, String email, int times) {
        for (int i = 0; i < times; i++) {
            guard.recordFailure(ip, email);
        }
    }

    @Test
    @DisplayName("a fresh caller is allowed")
    void allowsFirstAttempt() {
        assertThatCode(() -> guard.assertAllowed(IP, EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("attempts below the account budget still pass")
    void allowsUpToTheAccountBudget() {
        fail(IP, EMAIL, 4);

        assertThatCode(() -> guard.assertAllowed(IP, EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the account budget locks the account out from every address")
    void accountBudgetLocksOutEveryAddress() {
        fail(IP, EMAIL, 5);

        // A botnet rotating addresses must not get a fresh budget per address.
        assertThatThrownBy(() -> guard.assertAllowed("198.51.100.7", EMAIL))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    @DisplayName("the address budget locks the address out across different accounts")
    void addressBudgetLocksOutEveryAccount() {
        // Ten failures spread over ten accounts: never trips the per-account budget, so only the
        // per-address budget can stop a spray.
        for (int i = 0; i < 10; i++) {
            guard.recordFailure(IP, "user" + i + "@test.local");
        }

        assertThatThrownBy(() -> guard.assertAllowed(IP, "user99@test.local"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    @DisplayName("an unrelated caller is unaffected by someone else's lockout")
    void lockoutDoesNotLeakToOtherCallers() {
        fail(IP, EMAIL, 10);

        assertThatCode(() -> guard.assertAllowed("198.51.100.7", "someone.else@test.local"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the rejection carries a Retry-After hint")
    void reportsRetryAfter() {
        fail(IP, EMAIL, 5);

        assertThatThrownBy(() -> guard.assertAllowed(IP, EMAIL))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .satisfies(ex -> assertThat(
                        ((TooManyLoginAttemptsException) ex).getRetryAfterSeconds())
                        .isBetween(1L, 900L));
    }

    @Test
    @DisplayName("the lockout lapses once its window passes")
    void lockoutExpires() {
        fail(IP, EMAIL, 5);
        assertThatThrownBy(() -> guard.assertAllowed(IP, EMAIL))
                .isInstanceOf(TooManyLoginAttemptsException.class);

        clock.advance(Duration.ofMinutes(16));

        assertThatCode(() -> guard.assertAllowed(IP, EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a successful sign-in clears the counters")
    void successResetsTheBudget() {
        fail(IP, EMAIL, 4);

        guard.recordSuccess(IP, EMAIL);

        // Without the reset, a user who mistypes a few times then succeeds would stay one slip
        // away from a lockout for the rest of the window.
        fail(IP, EMAIL, 4);
        assertThatCode(() -> guard.assertAllowed(IP, EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("repeated address lockouts double the wait")
    void addressLockoutEscalates() {
        // First round: ten failures spread across accounts so only the address budget trips.
        for (int i = 0; i < 10; i++) {
            guard.recordFailure(IP, "user" + i + "@test.local");
        }
        clock.advance(Duration.ofMinutes(16));
        assertThatCode(() -> guard.assertAllowed(IP, "fresh@test.local")).doesNotThrowAnyException();

        // Second round from the same address: the wait should now be 30 minutes, not 15.
        for (int i = 10; i < 20; i++) {
            guard.recordFailure(IP, "user" + i + "@test.local");
        }
        clock.advance(Duration.ofMinutes(16));

        assertThatThrownBy(() -> guard.assertAllowed(IP, "fresh@test.local"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        clock.advance(Duration.ofMinutes(15));
        assertThatCode(() -> guard.assertAllowed(IP, "fresh@test.local")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the address lockout never exceeds its ceiling")
    void addressLockoutIsCapped() {
        properties.getLoginThrottle().setMaxLockoutMs(1_800_000L);
        guard = new LoginThrottleGuard(properties, clock);

        for (int round = 0; round < 6; round++) {
            for (int i = 0; i < 10; i++) {
                guard.recordFailure(IP, "user" + round + "-" + i + "@test.local");
            }
            clock.advance(Duration.ofMinutes(31));
        }

        // Six rounds of doubling would be hours without the cap; 31 minutes must always clear it.
        assertThatCode(() -> guard.assertAllowed(IP, "fresh@test.local")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the account lockout stays flat so it cannot be used to lock a victim out")
    void accountLockoutDoesNotEscalate() {
        // An attacker who knows the email can fail on purpose. If this escalated, they could keep a
        // real user locked out for a day; the wait must stay at the base window every round.
        //
        // Each round uses a different address on purpose: reusing one would trip the (escalating)
        // address budget after two rounds and the test would be measuring that instead.
        for (int round = 0; round < 4; round++) {
            String attackerIp = "198.51.100." + (round + 1);
            fail(attackerIp, EMAIL, 5);
            assertThatThrownBy(() -> guard.assertAllowed(IP, EMAIL))
                    .isInstanceOf(TooManyLoginAttemptsException.class);
            clock.advance(Duration.ofMinutes(16));
            assertThatCode(() -> guard.assertAllowed(IP, EMAIL)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("email casing and padding cannot buy a fresh budget")
    void accountKeyIsNormalised() {
        fail(IP, EMAIL, 5);

        assertThatThrownBy(() -> guard.assertAllowed("198.51.100.7", "  VICTIM@TEST.LOCAL  "))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

}
