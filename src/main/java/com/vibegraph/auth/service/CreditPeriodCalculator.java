package com.vibegraph.auth.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class CreditPeriodCalculator {

    public CreditPeriod currentPeriod(Instant registeredAt, LocalDate today) {
        Objects.requireNonNull(registeredAt, "registeredAt must not be null");
        Objects.requireNonNull(today, "today must not be null");

        int registrationDay = registeredAt.atZone(ZoneOffset.UTC).getDayOfMonth();
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate currentAnchor = anchor(currentMonth, registrationDay);
        LocalDate periodStart = today.isBefore(currentAnchor)
                ? anchor(currentMonth.minusMonths(1), registrationDay)
                : currentAnchor;
        LocalDate nextPeriodStart = anchor(YearMonth.from(periodStart).plusMonths(1), registrationDay);
        return new CreditPeriod(periodStart, nextPeriodStart.minusDays(1));
    }

    private LocalDate anchor(YearMonth month, int registrationDay) {
        return month.atDay(Math.min(registrationDay, month.lengthOfMonth()));
    }

    public record CreditPeriod(LocalDate periodStart, LocalDate periodEnd) {
    }
}
