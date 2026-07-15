package com.vibegraph.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Account access guard")
class AccountAccessGuardTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountSettingsService accountSettingsService;

    @Test
    @DisplayName("realtime access is allowed only for an active, unblocked user")
    void canAccessRealtime_activeUnblockedUser_returnsTrue() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.builder().id(userId).deactivated(false).build()));
        when(accountSettingsService.isBlocked(userId)).thenReturn(false);

        AccountAccessGuard guard = new AccountAccessGuard(userRepository, accountSettingsService);

        assertThat(guard.canAccessRealtime(userId)).isTrue();
    }

    @Test
    @DisplayName("realtime access is denied when the account is blocked")
    void canAccessRealtime_blockedUser_returnsFalse() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.builder().id(userId).deactivated(false).build()));
        when(accountSettingsService.isBlocked(userId)).thenReturn(true);

        AccountAccessGuard guard = new AccountAccessGuard(userRepository, accountSettingsService);

        assertThat(guard.canAccessRealtime(userId)).isFalse();
    }

    @Test
    @DisplayName("realtime access is denied when the account is deactivated")
    void canAccessRealtime_deactivatedUser_returnsFalse() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.builder().id(userId).deactivated(true).build()));

        AccountAccessGuard guard = new AccountAccessGuard(userRepository, accountSettingsService);

        assertThat(guard.canAccessRealtime(userId)).isFalse();
    }

    @Test
    @DisplayName("realtime access fails closed when the user no longer exists")
    void canAccessRealtime_missingUser_returnsFalse() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        AccountAccessGuard guard = new AccountAccessGuard(userRepository, accountSettingsService);

        assertThat(guard.canAccessRealtime(userId)).isFalse();
    }
}
