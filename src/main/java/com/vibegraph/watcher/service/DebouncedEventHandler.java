package com.vibegraph.watcher.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.vibegraph.watcher.config.WatcherProperties;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Debounces work keyed by an arbitrary string (e.g. {@code projectId}).
 *
 * <p>Each {@link #debounce(String, Runnable)} call (re)schedules {@code action} to run
 * after the configured {@code debounceMs} window, cancelling any still-pending action for
 * the same key. A burst of rapid calls therefore collapses into a single execution once
 * the key has been idle for the debounce window — exactly what we want when an IDE
 * format-on-save fires many MODIFY events in quick succession.
 *
 * <p>Backed by a single scheduler thread; scheduled actions must be short and must not
 * block (they only drain a buffer and dispatch).
 */
@Component
@Slf4j
public class DebouncedEventHandler {

    private final long debounceMs;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    public DebouncedEventHandler(WatcherProperties properties) {
        this.debounceMs = Math.max(0, properties.getDebounceMs());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory());
    }

    /**
     * Schedule {@code action} to run after the debounce window, replacing any pending
     * action previously scheduled for {@code key}.
     */
    public void debounce(String key, Runnable action) {
        ScheduledFuture<?> next = scheduler.schedule(
                () -> safeRun(key, action), debounceMs, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> previous = pending.put(key, next);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    /** Cancel any pending action for {@code key}. Safe to call when nothing is scheduled. */
    public void cancel(String key) {
        ScheduledFuture<?> pendingTask = pending.remove(key);
        if (pendingTask != null) {
            pendingTask.cancel(false);
        }
    }

    private void safeRun(String key, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("Debounced action for key={} failed: {}", key, e.getMessage(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        pending.values().forEach(f -> f.cancel(false));
        pending.clear();
        scheduler.shutdownNow();
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "watcher-debounce-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
