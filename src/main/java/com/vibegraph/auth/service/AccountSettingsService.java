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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountSettingsService {

    private static final String FREE_PLAN_CODE = "FREE";
    private static final String DEFAULT_BLOCKED_REASON = "Account is blocked";

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

    @Transactional(readOnly = true)
    public AccountQuotaSnapshot quotaSnapshot(UUID userId) {
        UserAccountSettings settings = findSettings(userId);
        long usedBytes = projectUsageRepository.sumStorageBytesByOwnerId(userId);
        Long override = settings.getStorageQuotaOverrideBytes();
        long limitBytes = override != null ? override : settings.getPlan().getStorageLimitBytes();
        return new AccountQuotaSnapshot(
                usedBytes,
                limitBytes,
                Math.max(0L, limitBytes - usedBytes),
                settings.getPlan().getCode(),
                settings.getPlan().getName(),
                override);
    }
}
