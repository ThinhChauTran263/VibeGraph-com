package com.vibegraph.abuse;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.vibegraph.common.exception.ConcurrentImportLimitException;

@Component
public class ConcurrentImportGuard {

    private final AbuseProperties properties;
    private final ConcurrentHashMap<UUID, AtomicInteger> activeImportsByUser = new ConcurrentHashMap<>();

    public ConcurrentImportGuard(AbuseProperties properties) {
        this.properties = properties;
    }

    public synchronized Lease acquire(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        AtomicInteger userCount = activeImportsByUser.computeIfAbsent(userId, ignored -> new AtomicInteger());

        int currentUser = userCount.incrementAndGet();
        if (exceedsLimit(currentUser, properties.getConcurrentImportsPerUser())) {
            releaseUser(userId, userCount);
            throw new ConcurrentImportLimitException("Concurrent active import limit reached for this account");
        }

        return new Lease(userId, userCount);
    }

    private static boolean exceedsLimit(int current, int configuredLimit) {
        return configuredLimit > 0 && current > configuredLimit;
    }

    private void releaseUser(UUID userId, AtomicInteger count) {
        if (count.decrementAndGet() == 0) {
            activeImportsByUser.remove(userId, count);
        }
    }

    public final class Lease implements AutoCloseable {
        private final UUID userId;
        private final AtomicInteger userCount;
        private boolean closed;

        private Lease(UUID userId, AtomicInteger userCount) {
            this.userId = userId;
            this.userCount = userCount;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            synchronized (ConcurrentImportGuard.this) {
                releaseUser(userId, userCount);
            }
        }
    }
}
