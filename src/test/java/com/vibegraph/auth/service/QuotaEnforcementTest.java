package com.vibegraph.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.domain.entity.Plan;
import com.vibegraph.auth.domain.entity.UserAccountSettings;
import com.vibegraph.auth.repository.PlanRepository;
import com.vibegraph.auth.repository.ProjectUsageRepository;
import com.vibegraph.auth.repository.UserAccountSettingsRepository;
import com.vibegraph.common.exception.AccountBlockedException;
import com.vibegraph.common.exception.QuotaExceededException;

/**
 * Unit tests for quota and blocked-account enforcement in {@link AccountSettingsService}.
 *
 * Key rules under test:
 *  - Adding bytes that keep total ≤ limit → no exception
 *  - Adding bytes that push total > limit → QuotaExceededException
 *  - Admin override > plan limit → allows the import
 *  - Blocked account → AccountBlockedException (not QuotaExceededException)
 *  - Delta ≤ 0 (deletion/replacement smaller) → always allowed
 *
 * Run: mvnw test -Dtest=QuotaEnforcementTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Quota enforcement — AccountSettingsService")
class QuotaEnforcementTest {

    @Mock PlanRepository planRepository;
    @Mock UserAccountSettingsRepository settingsRepository;
    @Mock ProjectUsageRepository projectUsageRepository;

    private AccountSettingsService service;

    private final UUID userId = UUID.randomUUID();
    private Plan freePlan;
    private UserAccountSettings settings;

    /** 100 MB plan limit, 50 MB currently used. */
    @BeforeEach
    void setUp() {
        service = new AccountSettingsService(planRepository, settingsRepository, projectUsageRepository);

        freePlan = Plan.builder()
                .id(UUID.randomUUID())
                .code("FREE")
                .name("Free")
                .storageLimitBytes(100_000_000L) // 100 MB
                .apiKeyLimit(3)
                .build();

        settings = UserAccountSettings.builder()
                .userId(userId)
                .plan(freePlan)
                .build();

        lenient().when(settingsRepository.findById(userId)).thenReturn(Optional.of(settings));
        // 50 MB used → 50 MB remaining
        lenient().when(projectUsageRepository.sumStorageBytesByOwnerId(userId)).thenReturn(50_000_000L);
    }

    // ── happy paths ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Adding bytes below the remaining quota → no exception")
    void belowQuota_passes() {
        // 50 MB used + 10 MB new = 60 MB → below 100 MB limit
        assertDoesNotThrow(() -> service.assertQuotaNotExceeded(userId, 10_000_000L));
    }

    @Test
    @DisplayName("Adding exactly the remaining bytes → no exception (boundary)")
    void exactBoundary_passes() {
        // 50 MB used + 50 MB new = 100 MB = limit exactly → allowed
        assertDoesNotThrow(() -> service.assertQuotaNotExceeded(userId, 50_000_000L));
    }

    @Test
    @DisplayName("Delta ≤ 0 (deletion / shrinking replace) → always allowed")
    void negativeOrZeroDelta_alwaysPasses() {
        assertDoesNotThrow(() -> service.assertQuotaNotExceeded(userId, 0L));
        assertDoesNotThrow(() -> service.assertQuotaNotExceeded(userId, -1_000_000L));
    }

    // ── quota exceeded ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Adding bytes that push total > limit → QuotaExceededException")
    void aboveQuota_throwsQuotaExceeded() {
        // 50 MB used + 60 MB new = 110 MB → exceeds 100 MB limit
        assertThrows(QuotaExceededException.class,
                () -> service.assertQuotaNotExceeded(userId, 60_000_000L));
    }

    @Test
    @DisplayName("One byte over the limit → QuotaExceededException")
    void oneByteOver_throwsQuotaExceeded() {
        // 50 MB used + 50 MB + 1 byte = limit + 1 → rejected
        assertThrows(QuotaExceededException.class,
                () -> service.assertQuotaNotExceeded(userId, 50_000_001L));
    }

    @Test
    @DisplayName("Byte comparison rejects huge additions without long overflow")
    void hugeAddition_doesNotOverflowOpen() {
        assertThrows(QuotaExceededException.class,
                () -> service.assertQuotaNotExceeded(userId, Long.MAX_VALUE));
    }

    // ── admin override ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Admin override > plan limit → import allowed even if it exceeds plan")
    void adminOverride_allowsExceedingPlanLimit() {
        // Admin sets 200 MB override on a 100 MB plan
        settings.setStorageQuotaOverrideBytes(200_000_000L);
        // 50 MB used + 60 MB new = 110 MB → below 200 MB override → OK
        assertDoesNotThrow(() -> service.assertQuotaNotExceeded(userId, 60_000_000L));
    }

    // ── blocked account ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Blocked account → AccountBlockedException, NOT QuotaExceededException")
    void blockedAccount_throwsAccountBlocked_notQuota() {
        settings.block("Violated ToS", "Account suspended");
        // assertNotBlocked must throw BEFORE quota is even checked
        assertThrows(AccountBlockedException.class,
                () -> service.assertNotBlocked(userId));
    }
}
