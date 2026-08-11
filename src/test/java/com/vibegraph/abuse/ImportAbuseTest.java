package com.vibegraph.abuse;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.vibegraph.common.exception.ConcurrentImportLimitException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportAbuseTest {

    @Test
    void concurrentImportLease_validationFailureRelease_allowsRetry() {
        AbuseProperties properties = new AbuseProperties();
        properties.setConcurrentImportsPerUser(1);
        ConcurrentImportGuard guard = new ConcurrentImportGuard(properties);
        UUID userId = UUID.randomUUID();

        try (ConcurrentImportGuard.Lease ignored = guard.acquire(userId)) {
            // Represents validation/preparation failure before async worker submission.
        }

        guard.acquire(userId).close();
    }

    @Test
    void concurrentImportLease_exceptionFailureRelease_allowsRetry() {
        AbuseProperties properties = new AbuseProperties();
        properties.setConcurrentImportsPerUser(1);
        ConcurrentImportGuard guard = new ConcurrentImportGuard(properties);
        UUID userId = UUID.randomUUID();

        try {
            try (ConcurrentImportGuard.Lease ignored = guard.acquire(userId)) {
                throw new IllegalStateException("import failed");
            }
        } catch (IllegalStateException ignored) {
            // expected
        }

        guard.acquire(userId).close();
    }

    @Test
    void concurrentImportLease_activeWorker_rejectsSecondAttempt() {
        ConcurrentImportGuard guard = new ConcurrentImportGuard(new AbuseProperties());
        UUID userId = UUID.randomUUID();
        ConcurrentImportGuard.Lease active = guard.acquire(userId);

        assertThatThrownBy(() -> guard.acquire(userId))
                .isInstanceOf(ConcurrentImportLimitException.class);

        active.close();
    }
}
