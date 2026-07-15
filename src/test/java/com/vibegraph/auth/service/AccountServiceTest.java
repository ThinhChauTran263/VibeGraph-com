package com.vibegraph.auth.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.CreditLedger;
import com.vibegraph.auth.domain.ProjectOwnership;
import com.vibegraph.auth.domain.ProjectOwnershipStatus;
import com.vibegraph.auth.domain.ProjectSourceType;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.domain.UserAccountSettings;
import com.vibegraph.auth.domain.UserCreditBalance;
import com.vibegraph.auth.dto.AccountCreditLedgerResponse;
import com.vibegraph.auth.dto.AccountProfileUpdateRequest;
import com.vibegraph.auth.dto.AccountPasswordChangeRequest;
import com.vibegraph.auth.dto.AccountProjectPageRequest;
import com.vibegraph.auth.dto.AccountProjectResponse;
import com.vibegraph.auth.dto.AccountProjectsPageResponse;
import com.vibegraph.auth.dto.AccountUsageResponse;
import com.vibegraph.auth.dto.UserResponse;
import com.vibegraph.auth.repository.CreditLedgerRepository;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.common.exception.UnauthorizedException;
import com.vibegraph.common.exception.InvalidCredentialsException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Account service")
class AccountServiceTest {

    @Mock
    private CurrentUser currentUser;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountSettingsService accountSettingsService;

    @Mock
    private CreditBalanceService creditBalanceService;

    @Mock
    private CreditLedgerRepository creditLedgerRepository;

    @Mock
    private ProjectOwnershipRepository projectOwnershipRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(
                currentUser,
                userRepository,
                accountSettingsService,
                creditBalanceService,
                creditLedgerRepository,
                projectOwnershipRepository,
                passwordEncoder);
    }

    @Test
    @DisplayName("profile returns the current authenticated user only")
    void profile_currentUser_returnsSafeProjection() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("me@test.local")
                .displayName("Me")
                .role(Role.USER)
                .passwordHash("hash")
                .build();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountSettingsService.findSettings(userId))
                .thenReturn(com.vibegraph.auth.domain.UserAccountSettings.builder().userId(userId).build());

        UserResponse profile = accountService.profile();

        assertEquals(userId.toString(), profile.id());
        assertEquals("me@test.local", profile.email());
        assertEquals("Me", profile.displayName());
        assertEquals("USER", profile.role());
    }

    @Test
    @DisplayName("session state returns an active user's safe identity projection")
    void sessionState_activeUser_returnsSafeIdentityProjection() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("active@test.local")
                .displayName("Active User")
                .role(Role.USER)
                .passwordHash("secret-hash")
                .build();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountSettingsService.findSettings(userId))
                .thenReturn(UserAccountSettings.builder().userId(userId).build());

        var state = accountService.sessionState();

        assertEquals(userId.toString(), state.id());
        assertEquals("active@test.local", state.email());
        assertEquals("Active User", state.displayName());
        assertEquals("USER", state.role());
        assertEquals("ACTIVE", state.accountStatus());
        assertNull(state.safeReason());
        assertFalse(state.toString().contains("secret-hash"));
    }

    @Test
    @DisplayName("session state returns only the blocked account safe reason")
    void sessionState_blockedUser_returnsOnlySafeReason() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("blocked@test.local")
                .displayName("Blocked User")
                .role(Role.USER)
                .passwordHash("secret-hash")
                .build();
        UserAccountSettings settings = UserAccountSettings.builder()
                .userId(userId)
                .blockedAt(Instant.parse("2026-07-14T12:00:00Z"))
                .blockedReason("internal fraud score")
                .blockedReasonSafe("Policy review")
                .build();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountSettingsService.findSettings(userId)).thenReturn(settings);

        var state = accountService.sessionState();

        assertEquals("BLOCKED", state.accountStatus());
        assertEquals("Policy review", state.safeReason());
        assertFalse(state.toString().contains("internal fraud score"));
        assertFalse(state.toString().contains("secret-hash"));
    }

    @Test
    @DisplayName("session state uses a safe fallback when blocked reason is missing")
    void sessionState_blockedUserWithoutSafeReason_usesFallback() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("blocked@test.local").role(Role.USER).build();
        UserAccountSettings settings = UserAccountSettings.builder()
                .userId(userId)
                .blockedAt(Instant.parse("2026-07-14T12:00:00Z"))
                .blockedReason("internal note")
                .blockedReasonSafe("   ")
                .build();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountSettingsService.findSettings(userId)).thenReturn(settings);

        var state = accountService.sessionState();

        assertEquals("BLOCKED", state.accountStatus());
        assertEquals("Account access is restricted", state.safeReason());
        assertFalse(state.toString().contains("internal note"));
    }

    @Test
    @DisplayName("session state returns only the deactivated account safe reason")
    void sessionState_deactivatedUser_returnsOnlySafeReason() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("closed@test.local")
                .displayName("Closed User")
                .role(Role.USER)
                .deactivated(true)
                .deactivationReason("internal admin note")
                .deactivationReasonSafe("Account closed by administrator")
                .build();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountSettingsService.findSettings(userId))
                .thenReturn(UserAccountSettings.builder().userId(userId).build());

        var state = accountService.sessionState();

        assertEquals("DEACTIVATED", state.accountStatus());
        assertEquals("Account closed by administrator", state.safeReason());
        assertFalse(state.toString().contains("internal admin note"));
    }

    @Test
    @DisplayName("profile throws unauthorized when the authenticated user no longer exists")
    void profile_missingCurrentUser_throwsUnauthorized() {
        UUID userId = UUID.randomUUID();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> accountService.profile());
    }

    @Test
    @DisplayName("updateProfile updates displayName only")
    void updateProfile_displayNameOnly_preservesEmailAndRole() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("original@test.local")
                .displayName("Old")
                .role(Role.ADMIN)
                .passwordHash("hash")
                .build();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountSettingsService.findSettings(userId))
                .thenReturn(com.vibegraph.auth.domain.UserAccountSettings.builder().userId(userId).build());

        UserResponse profile = accountService.updateProfile(new AccountProfileUpdateRequest("  New Name  "));

        assertEquals("New Name", profile.displayName());
        assertEquals("original@test.local", profile.email());
        assertEquals("ADMIN", profile.role());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("changePassword requires the old password and stores a new hash")
    void changePassword_validOldPassword_updatesHash() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("me@test.local")
                .passwordHash("old-hash")
                .role(Role.USER)
                .build();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        accountService.changePassword(new AccountPasswordChangeRequest(
                "old-password", "new-password", "new-password"));

        assertEquals("new-hash", user.getPasswordHash());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("changePassword rejects an invalid old password")
    void changePassword_invalidOldPassword_throwsInvalidCredentials() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("me@test.local")
                .passwordHash("old-hash")
                .role(Role.USER)
                .build();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> accountService.changePassword(
                new AccountPasswordChangeRequest("wrong", "new-password", "new-password")));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("usage returns the current user's quota snapshot")
    void usage_currentUser_returnsQuotaSnapshot() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("me@test.local").role(Role.USER).build();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountSettingsService.quotaSnapshot(userId))
                .thenReturn(new AccountQuotaSnapshot(128L, 512L, 384L, "FREE", "Free", null));
        when(creditBalanceService.findOrCreateCurrentPeriod(userId))
                .thenReturn(UserCreditBalance.builder()
                        .creditsLimitSnapshot(100)
                        .creditsUsed(25)
                        .creditsAdjustment(10)
                        .build());

        AccountUsageResponse usage = accountService.usage();

        assertEquals(128L, usage.usedBytes());
        assertEquals(512L, usage.limitBytes());
        assertEquals(384L, usage.remainingBytes());
        assertEquals("FREE", usage.planCode());
        assertEquals("Free", usage.planName());
        assertNull(usage.quotaOverrideBytes());
        assertEquals(25, usage.creditsUsed());
        assertEquals(110, usage.creditsLimit());
        assertEquals(85, usage.creditsRemaining());
    }

    @Test
    @DisplayName("usage throws unauthorized when the authenticated user no longer exists")
    void usage_missingCurrentUser_throwsUnauthorized() {
        UUID userId = UUID.randomUUID();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> accountService.usage());
        verifyNoInteractions(accountSettingsService);
    }

    @Test
    @DisplayName("creditLedger returns recent entries for the current user only")
    void creditLedger_currentUser_returnsRecentLedger() {
        UUID userId = UUID.randomUUID();
        UUID ledgerId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-14T12:00:00Z");
        User user = User.builder().id(userId).email("me@test.local").role(Role.USER).build();
        CreditLedger ledger = CreditLedger.builder()
                .id(ledgerId)
                .userId(userId)
                .source("CLI")
                .operationCode("CLI_PUSH")
                .creditsDelta(-2)
                .projectId("project-1")
                .createdAt(createdAt)
                .metadata("{\"internal\":\"not returned by DTO\"}")
                .build();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(creditLedgerRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(java.util.List.of(ledger));

        java.util.List<AccountCreditLedgerResponse> entries = accountService.creditLedger(10);

        assertEquals(1, entries.size());
        AccountCreditLedgerResponse entry = entries.getFirst();
        assertEquals(ledgerId, entry.id());
        assertEquals("CLI", entry.source());
        assertEquals("CLI_PUSH", entry.operationCode());
        assertEquals(-2, entry.creditsDelta());
        assertEquals("project-1", entry.projectId());
        assertEquals(createdAt, entry.createdAt());
    }

    @Test
    @DisplayName("projects returns only projects owned by the current user")
    void projects_currentUser_returnsOwnedProjects() {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        ProjectOwnership owned = ProjectOwnership.builder()
                .projectId("owned")
                .ownerId(userId)
                .name("Owned Project")
                .sourceType(ProjectSourceType.LOCAL)
                .sizeBytes(1024L)
                .status(ProjectOwnershipStatus.ANALYZED)
                .createdAt(createdAt)
                .build();
        User user = User.builder().id(userId).email("me@test.local").role(Role.USER).build();
        AccountProjectPageRequest request = new AccountProjectPageRequest(0, 20);
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(projectOwnershipRepository.findByOwnerId(userId, request.toPageable()))
                .thenReturn(new PageImpl<>(java.util.List.of(owned), PageRequest.of(0, 20), 1));

        AccountProjectsPageResponse projects = accountService.projects(request);

        assertEquals(1, projects.items().size());
        assertEquals(0, projects.page());
        assertEquals(20, projects.size());
        assertEquals(1L, projects.totalElements());
        AccountProjectResponse project = projects.items().getFirst();
        assertEquals("owned", project.id());
        assertEquals("Owned Project", project.name());
        assertEquals("LOCAL", project.sourceType());
        assertEquals(1024L, project.sizeBytes());
        assertEquals("ANALYZED", project.status());
        assertEquals(createdAt, project.createdAt());
    }

    @Test
    @DisplayName("projects throws unauthorized when the authenticated user no longer exists")
    void projects_missingCurrentUser_throwsUnauthorized() {
        UUID userId = UUID.randomUUID();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> accountService.projects(new AccountProjectPageRequest(0, 20)));
        verifyNoInteractions(projectOwnershipRepository);
    }
}
