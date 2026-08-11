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
import org.springframework.security.crypto.password.PasswordEncoder;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.AuthProvider;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.domain.UserIdentity;
import com.vibegraph.auth.dto.LoginRequest;
import com.vibegraph.auth.dto.RegisterRequest;
import com.vibegraph.auth.oauth.OAuthAccountProfile;
import com.vibegraph.auth.repository.UserIdentityRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.common.exception.EmailAlreadyExistsException;
import com.vibegraph.common.exception.FeatureDisabledException;
import com.vibegraph.common.exception.InvalidCredentialsException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Auth service")
class AuthServiceTest {

    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String REFRESH_TOKEN = "opaque-refresh-token";
    private static final Instant REFRESH_EXPIRES_AT = Instant.parse("2026-08-17T00:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private AccountSettingsService accountSettingsService;

    @Mock
    private FeatureGateService featureGateService;

    @Mock
    private AuditService auditService;

    @Mock
    private RefreshSessionService refreshSessionService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                userIdentityRepository,
                passwordEncoder,
                jwtService,
                currentUser,
                accountSettingsService,
                featureGateService,
                auditService,
                refreshSessionService);
    }

    @Test
    @DisplayName("register creates default account settings")
    void register_success_createsDefaultSettings() {
        RegisterRequest request = new RegisterRequest("new@test.local", "Password123!", "New User");
        UUID userId = UUID.randomUUID();
        when(userRepository.existsByEmailIgnoreCase("new@test.local")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(userId);
            return user;
        });
        stubSessionIssue("jwt-token");

        AuthenticationResult response = authService.registerSession(request);

        assertEquals("jwt-token", response.accessToken());
        assertEquals(REFRESH_TOKEN, response.refreshToken());
        assertEquals("new@test.local", response.user().email());
        verify(accountSettingsService).createDefaultSettings(any(User.class));
        verify(jwtService).issue(any(User.class), eq(SESSION_ID));
    }

    @Test
    @DisplayName("register is blocked by the global registration feature flag before persistence")
    void register_featureDisabled_blocksBeforePersistence() {
        doThrow(new FeatureDisabledException(FeatureGateService.REGISTRATION))
                .when(featureGateService).assertEnabled(FeatureGateService.REGISTRATION);
        RegisterRequest request = new RegisterRequest("new@test.local", "Password123!", "New User");

        assertThrows(FeatureDisabledException.class, () -> authService.registerSession(request));

        verifyNoInteractions(
                userRepository,
                passwordEncoder,
                jwtService,
                accountSettingsService,
                refreshSessionService);
    }

    @Test
    @DisplayName("register does not create settings for duplicate email")
    void register_duplicateEmail_doesNotCreateSettings() {
        RegisterRequest request = new RegisterRequest("taken@test.local", "Password123!", "Taken User");
        when(userRepository.existsByEmailIgnoreCase("taken@test.local")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.registerSession(request));

        verifyNoInteractions(accountSettingsService);
        verifyNoInteractions(jwtService, refreshSessionService);
    }

    @Test
    @DisplayName("login issues a report-only token for blocked accounts after valid credentials")
    void login_blockedUser_returnsSafeRestrictedStatus() {
        LoginRequest request = new LoginRequest("blocked@test.local", "Password123!");
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("blocked@test.local")
                .passwordHash("hash")
                .role(Role.USER)
                .build();
        var settings = com.vibegraph.auth.domain.UserAccountSettings.builder()
                .userId(userId)
                .blockedAt(java.time.Instant.now())
                .blockedReason("private fraud note")
                .blockedReasonSafe("Policy review")
                .build();
        when(userRepository.findByEmailIgnoreCase("blocked@test.local")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hash")).thenReturn(true);
        when(accountSettingsService.findSettings(userId)).thenReturn(settings);
        stubSessionIssue(user, "restricted-token");

        AuthenticationResult response = authService.loginSession(request);

        assertEquals("restricted-token", response.accessToken());
        assertEquals("BLOCKED", response.user().accountStatus());
        assertEquals("Policy review", response.user().safeReason());
        assertFalse(response.user().toString().contains("private fraud note"));
        verify(refreshSessionService).issue(user);
        verify(jwtService).issue(user, SESSION_ID);
    }

    @Test
    @DisplayName("login issues a report-only token for deactivated accounts after valid credentials")
    void login_deactivatedUser_returnsSafeRestrictedStatus() {
        LoginRequest request = new LoginRequest("deactivated@test.local", "Password123!");
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("deactivated@test.local")
                .passwordHash("hash")
                .role(Role.USER)
                .deactivated(true)
                .deactivationReason("private note")
                .deactivationReasonSafe("Account closed by administrator")
                .build();
        when(userRepository.findByEmailIgnoreCase("deactivated@test.local")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hash")).thenReturn(true);
        when(accountSettingsService.findSettings(userId))
                .thenReturn(com.vibegraph.auth.domain.UserAccountSettings.builder().userId(userId).build());
        stubSessionIssue(user, "restricted-token");

        AuthenticationResult response = authService.loginSession(request);

        assertEquals("DEACTIVATED", response.user().accountStatus());
        assertEquals("Account closed by administrator", response.user().safeReason());
        assertFalse(response.user().toString().contains("private note"));
    }

    @Test
    @DisplayName("login preserves invalid credentials for wrong password")
    void login_wrongPassword_doesNotRevealBlockedStatus() {
        LoginRequest request = new LoginRequest("blocked@test.local", "wrong");
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("blocked@test.local")
                .passwordHash("hash")
                .role(Role.USER)
                .build();
        when(userRepository.findByEmailIgnoreCase("blocked@test.local")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.loginSession(request));

        verifyNoInteractions(accountSettingsService);
        verifyNoInteractions(jwtService, refreshSessionService);
    }

    @Test
    @DisplayName("login uses password check path for unknown email")
    void login_unknownEmail_checksDummyPasswordHash() {
        LoginRequest request = new LoginRequest("missing@test.local", "Password123!");
        when(userRepository.findByEmailIgnoreCase("missing@test.local")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq("Password123!"), any())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.loginSession(request));

        verify(passwordEncoder).matches(eq("Password123!"), any());
        verifyNoInteractions(accountSettingsService);
        verifyNoInteractions(jwtService, refreshSessionService);
    }

    @Test
    @DisplayName("login preserves invalid credentials for OAuth-only account")
    void login_oauthOnlyAccount_checksDummyPasswordHash() {
        LoginRequest request = new LoginRequest("oauth@test.local", "Password123!");
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("oauth@test.local")
                .passwordHash(null)
                .role(Role.USER)
                .build();
        when(userRepository.findByEmailIgnoreCase("oauth@test.local")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(eq("Password123!"), any())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.loginSession(request));

        verify(passwordEncoder).matches(eq("Password123!"), any());
        verifyNoInteractions(accountSettingsService);
        verifyNoInteractions(jwtService, refreshSessionService);
    }

    @Test
    @DisplayName("OAuth login creates a local account and provider identity for verified email")
    void oauthLogin_newVerifiedGoogleAccount_createsUserAndIdentity() {
        OAuthAccountProfile profile = new OAuthAccountProfile(
                AuthProvider.GOOGLE,
                "google-subject",
                "oauth@test.local",
                true,
                "OAuth User",
                "https://avatar.test/user.png");
        UUID userId = UUID.randomUUID();
        when(userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-subject"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("oauth@test.local")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(userId);
            }
            return user;
        });
        stubSessionIssue("oauth-jwt");

        AuthenticationResult response = authService.oauthLoginSession(profile);

        assertEquals("oauth-jwt", response.accessToken());
        assertEquals("oauth@test.local", response.user().email());
        verify(accountSettingsService).createDefaultSettings(any(User.class));
        verify(userIdentityRepository).save(argThat(identity ->
                identity.getProvider() == AuthProvider.GOOGLE
                        && identity.getProviderUserId().equals("google-subject")
                        && identity.getUserId().equals(userId)));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("OAuth login links verified provider identity to an existing email account")
    void oauthLogin_existingEmail_linksIdentityWithoutCreatingSettings() {
        UUID userId = UUID.randomUUID();
        User existing = User.builder()
                .id(userId)
                .email("existing@test.local")
                .displayName("Existing")
                .passwordHash("hash")
                .role(Role.USER)
                .emailVerified(false)
                .build();
        OAuthAccountProfile profile = new OAuthAccountProfile(
                AuthProvider.GITHUB,
                "12345",
                "existing@test.local",
                true,
                "GitHub Name",
                "https://avatar.test/github.png");
        when(userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GITHUB, "12345"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("existing@test.local")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        stubSessionIssue(existing, "linked-jwt");

        AuthenticationResult response = authService.oauthLoginSession(profile);

        assertEquals("linked-jwt", response.accessToken());
        assertTrue(existing.isEmailVerified());
        assertEquals("Existing", existing.getDisplayName());
        verify(userIdentityRepository).save(any(UserIdentity.class));
        verify(accountSettingsService, never()).createDefaultSettings(any(User.class));
    }

    @Test
    @DisplayName("OAuth login rejects unverified provider email before linking")
    void oauthLogin_unverifiedEmail_rejectsBeforePersistence() {
        OAuthAccountProfile profile = new OAuthAccountProfile(
                AuthProvider.GOOGLE,
                "google-subject",
                "oauth@test.local",
                false,
                "OAuth User",
                null);

        assertThrows(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class,
                () -> authService.oauthLoginSession(profile));

        verifyNoInteractions(userIdentityRepository, jwtService, refreshSessionService);
        verify(userRepository, never()).save(any());
    }

    private void stubSessionIssue(String accessToken) {
        when(refreshSessionService.issue(any(User.class)))
                .thenReturn(new RefreshSessionService.SessionToken(
                        SESSION_ID, REFRESH_TOKEN, REFRESH_EXPIRES_AT));
        when(jwtService.issue(any(User.class), eq(SESSION_ID))).thenReturn(accessToken);
    }

    private void stubSessionIssue(User user, String accessToken) {
        when(refreshSessionService.issue(user))
                .thenReturn(new RefreshSessionService.SessionToken(
                        SESSION_ID, REFRESH_TOKEN, REFRESH_EXPIRES_AT));
        when(jwtService.issue(user, SESSION_ID)).thenReturn(accessToken);
    }
}
