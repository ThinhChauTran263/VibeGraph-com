package com.vibegraph.abuse;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.vibegraph.common.exception.ConcurrentImportLimitException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConcurrentImportGuardTest {

    @Test
    void acquire_sameUserBeyondConfiguredLimit_rejectsUntilLeaseCloses() {
        AbuseProperties properties = new AbuseProperties();
        properties.setConcurrentImportsPerUser(1);
        ConcurrentImportGuard guard = new ConcurrentImportGuard(properties);
        UUID userId = UUID.randomUUID();

        ConcurrentImportGuard.Lease lease = guard.acquire(userId);

        assertThatThrownBy(() -> guard.acquire(userId))
                .isInstanceOf(ConcurrentImportLimitException.class)
                .hasMessageContaining("active import");

        lease.close();
        guard.acquire(userId).close();
    }

    @Test
    void acquire_differentUsers_tracksLimitsIndependently() {
        AbuseProperties properties = new AbuseProperties();
        properties.setConcurrentImportsPerUser(1);
        ConcurrentImportGuard guard = new ConcurrentImportGuard(properties);

        ConcurrentImportGuard.Lease first = guard.acquire(UUID.randomUUID());
        ConcurrentImportGuard.Lease second = guard.acquire(UUID.randomUUID());

        first.close();
        second.close();
    }

    @Test
    void acquire_zeroLimit_disablesThatDimension() {
        AbuseProperties properties = new AbuseProperties();
        properties.setConcurrentImportsPerUser(0);
        ConcurrentImportGuard guard = new ConcurrentImportGuard(properties);
        UUID userId = UUID.randomUUID();

        ConcurrentImportGuard.Lease first = guard.acquire(userId);
        ConcurrentImportGuard.Lease second = guard.acquire(userId);
        ConcurrentImportGuard.Lease third = guard.acquire(userId);

        first.close();
        second.close();
        third.close();
    }
}
