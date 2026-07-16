package com.vibegraph.auth.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** Revalidates account access at long-lived backend boundaries such as WebSocket delivery. */
@Service
@RequiredArgsConstructor
public class AccountAccessGuard {

    private final UserRepository userRepository;
    private final AccountSettingsService accountSettingsService;

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
