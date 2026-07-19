package com.vibegraph.auth.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.common.exception.AccountDeactivatedException;
import com.vibegraph.common.exception.UnauthorizedException;

import lombok.RequiredArgsConstructor;

/** Revalidates account access at long-lived backend boundaries such as WebSocket delivery. */
@Service
@RequiredArgsConstructor
public class AccountAccessGuard {

    private static final String DEFAULT_DEACTIVATED_REASON = "Account closed by administrator";

    private final UserRepository userRepository;
    private final AccountSettingsService accountSettingsService;

    @Transactional(readOnly = true)
    public void assertProductAccess(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
        accountSettingsService.assertNotBlocked(userId);
        if (user.isDeactivated()) {
            throw new AccountDeactivatedException(
                    "Account is deactivated",
                    safeDeactivationReason(user.getDeactivationReasonSafe()));
        }
    }

    private String safeDeactivationReason(String reason) {
        return reason == null || reason.isBlank() ? DEFAULT_DEACTIVATED_REASON : reason;
    }

    @Transactional(readOnly = true)
    public boolean canAccessRealtime(UUID userId) {
        return canAccessSupportRealtime(userId)
                && !accountSettingsService.isBlocked(userId);
    }

    @Transactional(readOnly = true)
    public boolean canAccessSupportRealtime(UUID userId) {
        return userId != null
                && userRepository.findById(userId)
                        .filter(user -> !user.isDeactivated())
                        .isPresent();
    }
}
