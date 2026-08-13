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

/** Unit tests for the sign-up budget, using the shared {@link MutableClock}. */
@DisplayName("Registration throttle guard")
class RegistrationThrottleGuardTest {

    private static final String IP = "203.0.113.10";

    private MutableClock clock;
    private AbuseProperties properties;
    private RegistrationThrottleGuard guard;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-08-12T09:00:00Z"));
        properties = new AbuseProperties();
        properties.getRegistrationThrottle().setMaxPerIp(10);
        properties.getRegistrationThrottle().setWindowMs(3_600_000L);
        guard = new RegistrationThrottleGuard(properties, clock);
    }

    private void signUp(String ip, int times) {
        for (int i = 0; i < times; i++) {
            guard.assertAllowed(ip);
        }
    }

    @Test
    @DisplayName("attempts up to the budget are allowed")
    void allowsUpToTheBudget() {
        assertThatCode(() -> signUp(IP, 10)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the attempt past the budget is refused")
    void refusesPastTheBudget() {
        signUp(IP, 10);

        assertThatThrownBy(() -> guard.assertAllowed(IP))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    @DisplayName("every attempt counts, not only successful ones")
    void countsEveryAttempt() {
        // Sign-up abuse is bulk creation, but counting failures too caps the other trick this
        // endpoint enables: probing which addresses are already registered.
        signUp(IP, 10);

        assertThatThrownBy(() -> guard.assertAllowed(IP))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    @DisplayName("another address is unaffected")
    void budgetIsPerAddress() {
        signUp(IP, 10);

        assertThatCode(() -> guard.assertAllowed("198.51.100.7")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the budget refreshes once the window passes")
    void windowExpires() {
        signUp(IP, 10);
        assertThatThrownBy(() -> guard.assertAllowed(IP))
                .isInstanceOf(TooManyLoginAttemptsException.class);

        clock.advance(Duration.ofMinutes(61));

        assertThatCode(() -> guard.assertAllowed(IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the rejection says how long to wait")
    void reportsRetryAfter() {
        signUp(IP, 10);

        assertThatThrownBy(() -> guard.assertAllowed(IP))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .satisfies(ex -> assertThat(
                        ((TooManyLoginAttemptsException) ex).getRetryAfterSeconds())
                        .isBetween(1L, 3600L));
    }

    @Test
    @DisplayName("a missing address still gets a budget rather than an exemption")
    void unknownAddressIsStillCounted() {
        signUp(null, 10);

        assertThatThrownBy(() -> guard.assertAllowed(null))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }
}
