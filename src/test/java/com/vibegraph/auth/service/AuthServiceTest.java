package com.vibegraph.auth.service;

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
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.dto.AuthResponse;
import com.vibegraph.auth.dto.LoginRequest;
import com.vibegraph.auth.dto.RegisterRequest;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.common.exception.AccountBlockedException;
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

    @Mock
    private UserRepository userRepository;

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

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                currentUser,
                accountSettingsService,
                featureGateService,
                auditService);
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
        when(jwtService.issue(any(User.class))).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertEquals("jwt-token", response.token());
        assertEquals("new@test.local", response.user().email());
        verify(accountSettingsService).createDefaultSettings(any(User.class));
    }

    @Test
    @DisplayName("register is blocked by the global registration feature flag before persistence")
    void register_featureDisabled_blocksBeforePersistence() {
        doThrow(new FeatureDisabledException(FeatureGateService.REGISTRATION))
                .when(featureGateService).assertEnabled(FeatureGateService.REGISTRATION);
        RegisterRequest request = new RegisterRequest("new@test.local", "Password123!", "New User");

        assertThrows(FeatureDisabledException.class, () -> authService.register(request));

        verifyNoInteractions(userRepository, passwordEncoder, jwtService, accountSettingsService);
    }

    @Test
    @DisplayName("register does not create settings for duplicate email")
    void register_duplicateEmail_doesNotCreateSettings() {
        RegisterRequest request = new RegisterRequest("taken@test.local", "Password123!", "Taken User");
        when(userRepository.existsByEmailIgnoreCase("taken@test.local")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));

        verifyNoInteractions(accountSettingsService);
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
        when(jwtService.issue(user)).thenReturn("restricted-token");

        AuthResponse response = authService.login(request);

        assertEquals("restricted-token", response.token());
        assertEquals("BLOCKED", response.user().accountStatus());
        assertEquals("Policy review", response.user().safeReason());
        assertFalse(response.user().toString().contains("private fraud note"));
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
        when(jwtService.issue(user)).thenReturn("restricted-token");

        AuthResponse response = authService.login(request);

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

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));

        verifyNoInteractions(accountSettingsService);
        verify(jwtService, never()).issue(any());
    }

    @Test
    @DisplayName("login uses password check path for unknown email")
    void login_unknownEmail_checksDummyPasswordHash() {
        LoginRequest request = new LoginRequest("missing@test.local", "Password123!");
        when(userRepository.findByEmailIgnoreCase("missing@test.local")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq("Password123!"), any())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));

        verify(passwordEncoder).matches(eq("Password123!"), any());
        verifyNoInteractions(accountSettingsService);
        verify(jwtService, never()).issue(any());
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

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));

        verify(passwordEncoder).matches(eq("Password123!"), any());
        verifyNoInteractions(accountSettingsService);
        verify(jwtService, never()).issue(any());
    }
}
