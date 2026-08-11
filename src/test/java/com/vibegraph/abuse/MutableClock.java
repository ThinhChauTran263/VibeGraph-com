package com.vibegraph.abuse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test clock that only moves when a test moves it, so time-dependent behaviour is deterministic. */
final class MutableClock extends Clock {

    private Instant instant;

    MutableClock(Instant instant) {
        this.instant = instant;
    }

    void advance(Duration amount) {
        this.instant = this.instant.plus(amount);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
