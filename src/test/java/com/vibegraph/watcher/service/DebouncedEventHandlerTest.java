package com.vibegraph.watcher.service;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vibegraph.watcher.config.WatcherProperties;

/**
 * Tests for {@link DebouncedEventHandler}: rapid calls for the same key collapse into a
 * single execution, distinct keys run independently, and pending work can be cancelled.
 */
@DisplayName("DebouncedEventHandler")
class DebouncedEventHandlerTest {

    private static final long DEBOUNCE_MS = 80;
    private static final long SETTLE_MS = 400;

    private DebouncedEventHandler handler;

    @BeforeEach
    void setUp() {
        WatcherProperties properties = new WatcherProperties();
        properties.setDebounceMs(DEBOUNCE_MS);
        handler = new DebouncedEventHandler(properties);
    }

    @AfterEach
    void tearDown() {
        handler.shutdown();
    }

    @Test
    @DisplayName("collapses a burst of calls for the same key into one execution")
    void collapsesBurst() {
        AtomicInteger runs = new AtomicInteger();

        for (int i = 0; i < 10; i++) {
            handler.debounce("k", runs::incrementAndGet);
        }

        sleep(SETTLE_MS);
        assertThat(runs.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("runs independently for distinct keys")
    void independentKeys() {
        AtomicInteger a = new AtomicInteger();
        AtomicInteger b = new AtomicInteger();

        handler.debounce("a", a::incrementAndGet);
        handler.debounce("b", b::incrementAndGet);

        sleep(SETTLE_MS);
        assertThat(a.get()).isEqualTo(1);
        assertThat(b.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("cancel prevents a pending action from running")
    void cancelPreventsRun() {
        AtomicInteger runs = new AtomicInteger();

        handler.debounce("k", runs::incrementAndGet);
        handler.cancel("k");

        sleep(SETTLE_MS);
        assertThat(runs.get()).isZero();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
