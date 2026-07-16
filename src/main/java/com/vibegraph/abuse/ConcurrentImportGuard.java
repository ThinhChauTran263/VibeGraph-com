package com.vibegraph.abuse;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.vibegraph.common.exception.ConcurrentImportLimitException;

@Component
public class ConcurrentImportGuard {

    private final AbuseProperties properties;
    private final ConcurrentHashMap<UUID, AtomicInteger> activeImports = new ConcurrentHashMap<>();

    public ConcurrentImportGuard(AbuseProperties properties) {
        this.properties = properties;
    }

    public Lease acquire(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        AtomicInteger count = activeImports.computeIfAbsent(userId, ignored -> new AtomicInteger());
        int current = count.incrementAndGet();
        if (current > Math.max(1, properties.getConcurrentImportsPerUser())) {
            count.decrementAndGet();
            throw new ConcurrentImportLimitException("Concurrent active import limit reached for this account");
        }
        return new Lease(userId, count);
    }

    public final class Lease implements AutoCloseable {
        private final UUID userId;
        private final AtomicInteger count;
        private boolean closed;

        private Lease(UUID userId, AtomicInteger count) {
            this.userId = userId;
            this.count = count;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (count.decrementAndGet() == 0) {
                activeImports.remove(userId, count);
            }
        }
    }
}
