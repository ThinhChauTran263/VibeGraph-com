package com.vibegraph.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.vibegraph.common.exception.AccountBlockedException;
import com.vibegraph.common.exception.AccountDeactivatedException;
import com.vibegraph.common.exception.UnauthorizedException;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.domain.entity.User;
import com.vibegraph.auth.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Account access guard")
class AccountAccessGuardTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountSettingsService accountSettingsService;

    @Test
    @DisplayName("product access is allowed for an active, unblocked user")
    void assertProductAccess_activeUnblockedUser_allowsAccess() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.builder().id(userId).deactivated(false).build()));

        AccountAccessGuard guard = new AccountAccessGuard(userRepository, accountSettingsService);

        guard.assertProductAccess(userId);
    }

    @Test
    @DisplayName("product access returns the safe blocked account contract")
    void assertProductAccess_blockedUser_throwsAccountBlocked() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.builder().id(userId).deactivated(false).build()));
        doThrow(new AccountBlockedException("private fraud note", "Policy review"))
                .when(accountSettingsService).assertNotBlocked(userId);

        AccountAccessGuard guard = new AccountAccessGuard(userRepository, accountSettingsService);

        assertThatThrownBy(() -> guard.assertProductAccess(userId))
                .isInstanceOf(AccountBlockedException.class)
                .satisfies(error -> {
                    AccountBlockedException blocked = (AccountBlockedException) error;
                    assertThat(blocked.getCode()).isEqualTo("ACCOUNT_BLOCKED");
                    assertThat(blocked.getSafeReason()).isEqualTo("Policy review");
                });
    }

    @Test
    @DisplayName("product access returns a distinct safe deactivated account contract")
    void assertProductAccess_deactivatedUser_throwsAccountDeactivated() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder()
                .id(userId)
                .deactivated(true)
                .deactivationReason("private admin note")
                .deactivationReasonSafe("Account closed by administrator")
                .build()));

        AccountAccessGuard guard = new AccountAccessGuard(userRepository, accountSettingsService);

        assertThatThrownBy(() -> guard.assertProductAccess(userId))
                .isInstanceOf(AccountDeactivatedException.class)
                .satisfies(error -> {
                    AccountDeactivatedException deactivated = (AccountDeactivatedException) error;
                    assertThat(deactivated.getCode()).isEqualTo("ACCOUNT_DEACTIVATED");
                    assertThat(deactivated.getSafeReason()).isEqualTo("Account closed by administrator");
                });
    }

    @Test
    @DisplayName("product access fails closed when the authenticated user no longer exists")
    void assertProductAccess_missingUser_throwsUnauthorized() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        AccountAccessGuard guard = new AccountAccessGuard(userRepository, accountSettingsService);

        assertThatThrownBy(() -> guard.assertProductAccess(userId))
                .isInstanceOf(UnauthorizedException.class);
    }

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
