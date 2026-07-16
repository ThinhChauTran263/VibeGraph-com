package com.vibegraph.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CreditPeriodCalculator")
class CreditPeriodCalculatorTest {

    private final CreditPeriodCalculator calculator = new CreditPeriodCalculator();

    @Test
    @DisplayName("registration day 15 resets credits on day 15 every month")
    void registrationDay15_resetsOnDay15() {
        Instant registeredAt = Instant.parse("2025-01-15T23:30:00Z");

        assertThat(calculator.currentPeriod(registeredAt, LocalDate.of(2025, 3, 14)))
                .isEqualTo(new CreditPeriodCalculator.CreditPeriod(
                        LocalDate.of(2025, 2, 15), LocalDate.of(2025, 3, 14)));
        assertThat(calculator.currentPeriod(registeredAt, LocalDate.of(2025, 3, 15)))
                .isEqualTo(new CreditPeriodCalculator.CreditPeriod(
                        LocalDate.of(2025, 3, 15), LocalDate.of(2025, 4, 14)));
    }

    @Test
    @DisplayName("registration day 31 clamps February reset to month end")
    void registrationDay31_clampsToFebruaryEnd() {
        Instant registeredAt = Instant.parse("2025-01-31T08:00:00Z");

        assertThat(calculator.currentPeriod(registeredAt, LocalDate.of(2025, 2, 27)))
                .isEqualTo(new CreditPeriodCalculator.CreditPeriod(
                        LocalDate.of(2025, 1, 31), LocalDate.of(2025, 2, 27)));
        assertThat(calculator.currentPeriod(registeredAt, LocalDate.of(2025, 2, 28)))
                .isEqualTo(new CreditPeriodCalculator.CreditPeriod(
                        LocalDate.of(2025, 2, 28), LocalDate.of(2025, 3, 30)));
        assertThat(calculator.currentPeriod(registeredAt, LocalDate.of(2025, 3, 31)))
                .isEqualTo(new CreditPeriodCalculator.CreditPeriod(
                        LocalDate.of(2025, 3, 31), LocalDate.of(2025, 4, 29)));
    }

    @Test
    @DisplayName("registration day 30 clamps safely in leap and non-leap February")
    void registrationDay30_clampsAcrossFebruaryVariants() {
        Instant registeredAt = Instant.parse("2024-01-30T08:00:00Z");

        assertThat(calculator.currentPeriod(registeredAt, LocalDate.of(2024, 2, 29)))
                .isEqualTo(new CreditPeriodCalculator.CreditPeriod(
                        LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 29)));
        assertThat(calculator.currentPeriod(registeredAt, LocalDate.of(2025, 2, 28)))
                .isEqualTo(new CreditPeriodCalculator.CreditPeriod(
                        LocalDate.of(2025, 2, 28), LocalDate.of(2025, 3, 29)));
    }

    @Test
    @DisplayName("leap-day registration resets on February 28 in non-leap years")
    void leapDayRegistration_clampsInNonLeapYear() {
        Instant registeredAt = Instant.parse("2024-02-29T12:00:00Z");

        assertThat(calculator.currentPeriod(registeredAt, LocalDate.of(2025, 2, 28)))
                .isEqualTo(new CreditPeriodCalculator.CreditPeriod(
                        LocalDate.of(2025, 2, 28), LocalDate.of(2025, 3, 28)));
    }
}
