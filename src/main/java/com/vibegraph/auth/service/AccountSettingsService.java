package com.vibegraph.auth.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.Plan;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.domain.UserAccountSettings;
import com.vibegraph.auth.repository.PlanRepository;
import com.vibegraph.auth.repository.ProjectUsageRepository;
import com.vibegraph.auth.repository.UserAccountSettingsRepository;
import com.vibegraph.common.exception.AccountBlockedException;
import com.vibegraph.common.exception.QuotaExceededException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountSettingsService {

    private static final String FREE_PLAN_CODE = "FREE";
    private static final String DEFAULT_BLOCKED_REASON = "Account is blocked";
    static final String QUOTA_EXCEEDED_MESSAGE =
            "Source storage quota exceeded. Free up storage or ask an admin for a quota override.";

    private final PlanRepository planRepository;
    private final UserAccountSettingsRepository settingsRepository;
    private final ProjectUsageRepository projectUsageRepository;

    @Transactional
    public UserAccountSettings createDefaultSettings(User user) {
        UUID userId = user.getId();
        if (settingsRepository.existsById(userId)) {
            return settingsRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("Account settings not found for user"));
        }
        Plan freePlan = planRepository.findByCode(FREE_PLAN_CODE)
                .orElseThrow(() -> new IllegalStateException("FREE plan is not configured"));
        return settingsRepository.save(UserAccountSettings.builder()
                .userId(userId)
                .plan(freePlan)
                .build());
    }

    @Transactional(readOnly = true)
    public UserAccountSettings findSettings(UUID userId) {
        return settingsRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Account settings not found for user"));
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(UUID userId) {
        return settingsRepository.findById(userId)
                .map(UserAccountSettings::isBlocked)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public void assertNotBlocked(UUID userId) {
        settingsRepository.findById(userId)
                .filter(UserAccountSettings::isBlocked)
                .ifPresent(settings -> {
                    String reason = settings.getBlockedReasonSafe() == null || settings.getBlockedReasonSafe().isBlank()
                            ? DEFAULT_BLOCKED_REASON
                            : settings.getBlockedReasonSafe();
                    throw new AccountBlockedException("Account is blocked", reason);
                });
    }

    /**
     * Assert that adding {@code additionalBytes} would not exceed the effective storage quota for
     * the given user. The effective limit is the plan's storage limit, unless the admin has set a
     * higher override on the account settings.
     *
     * <p>Call this <em>before</em> any import or patch that writes source files to disk.
     * Always call {@link #assertNotBlocked(UUID)} first so that a blocked account receives
     * {@code ACCOUNT_BLOCKED}, not {@code QUOTA_EXCEEDED}.
     *
     * @param userId          the user whose quota to check
     * @param additionalBytes the net byte increase that the operation would cause
     * @throws QuotaExceededException if {@code usedBytes + additionalBytes > limitBytes}
     */
    @Transactional(readOnly = true)
    public void assertQuotaNotExceeded(UUID userId, long additionalBytes) {
        if (additionalBytes <= 0) {
            return;
        }
        AccountQuotaSnapshot snapshot = quotaSnapshot(userId);
        if (snapshot.usedBytes() >= snapshot.limitBytes()
                || additionalBytes > snapshot.limitBytes() - snapshot.usedBytes()) {
            throw new QuotaExceededException(QUOTA_EXCEEDED_MESSAGE);
        }
    }

    @Transactional(readOnly = true)
    public AccountQuotaSnapshot quotaSnapshot(UUID userId) {
        UserAccountSettings settings = findSettings(userId);
        long usedBytes = projectUsageRepository.sumStorageBytesByOwnerId(userId);
        long limitBytes = effectiveLimitBytes(settings);
        Long override = settings.getStorageQuotaOverrideBytes();
        long remainingBytes = usedBytes >= limitBytes ? 0L : limitBytes - usedBytes;
        return new AccountQuotaSnapshot(
                usedBytes,
                limitBytes,
                remainingBytes,
                settings.getPlan().getCode(),
                settings.getPlan().getName(),
                override);
    }

    static long effectiveLimitBytes(UserAccountSettings settings) {
        Long override = settings.getStorageQuotaOverrideBytes();
        long limitBytes = override != null ? override : settings.getPlan().getStorageLimitBytes();
        if (limitBytes < 0) {
            throw new IllegalStateException("Storage quota must be non-negative");
        }
        return limitBytes;
    }
}
