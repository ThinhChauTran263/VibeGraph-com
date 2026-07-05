package com.vibegraph.auth.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.dto.AuthResponse;
import com.vibegraph.auth.dto.LoginRequest;
import com.vibegraph.auth.dto.RegisterRequest;
import com.vibegraph.auth.dto.UserResponse;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.common.exception.EmailAlreadyExistsException;
import com.vibegraph.common.exception.InvalidCredentialsException;
import com.vibegraph.common.exception.UnauthorizedException;

import lombok.RequiredArgsConstructor;

/**
 * Local (email + password) authentication. Google OAuth is deferred.
 *
 * <p>Field validation is enforced by Bean Validation at the controller boundary (400) before any
 * method here runs, so {@link #register} can assume well-formed input and the duplicate-email
 * lookup never precedes validation. Passwords are BCrypt-hashed; the hash is never returned.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUser currentUser;

    /**
     * Create a local account and return a fresh token + safe user projection.
     *
     * @throws EmailAlreadyExistsException if the email is already registered (case-insensitive) → 409
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException("Email already registered");
        }
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .role(Role.USER)
                .emailVerified(false)
                .build();
        User saved = userRepository.save(user);
        return toAuthResponse(saved);
    }

    /**
     * Verify credentials and return a fresh token + safe user projection.
     *
     * @throws InvalidCredentialsException on unknown email, OAuth-only account (no local password),
     *                                     or wrong password → 401 (generic, no user enumeration)
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        return toAuthResponse(user);
    }

    /**
     * Resolve the current authenticated user's safe projection for {@code GET /api/auth/me}.
     *
     * @throws UnauthorizedException if the principal is missing or its user no longer exists → 401
     */
    @Transactional(readOnly = true)
    public UserResponse currentUser() {
        UUID id = currentUser.id();
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
        return UserResponse.from(user);
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(jwtService.issue(user), UserResponse.from(user));
    }
}
