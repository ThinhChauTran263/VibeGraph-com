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

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                currentUser,
                accountSettingsService);
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
    @DisplayName("register does not create settings for duplicate email")
    void register_duplicateEmail_doesNotCreateSettings() {
        RegisterRequest request = new RegisterRequest("taken@test.local", "Password123!", "Taken User");
        when(userRepository.existsByEmailIgnoreCase("taken@test.local")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));

        verifyNoInteractions(accountSettingsService);
    }

    @Test
    @DisplayName("login rejects blocked accounts after valid credentials")
    void login_blockedUser_throwsAccountBlocked() {
        LoginRequest request = new LoginRequest("blocked@test.local", "Password123!");
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("blocked@test.local")
                .passwordHash("hash")
                .role(Role.USER)
                .build();
        when(userRepository.findByEmailIgnoreCase("blocked@test.local")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hash")).thenReturn(true);
        doThrow(new AccountBlockedException("Account is blocked", "maintenance"))
                .when(accountSettingsService).assertNotBlocked(userId);

        AccountBlockedException ex = assertThrows(AccountBlockedException.class, () -> authService.login(request));

        assertEquals("ACCOUNT_BLOCKED", ex.getCode());
        verify(jwtService, never()).issue(any());
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
