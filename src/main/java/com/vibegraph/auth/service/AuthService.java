package com.vibegraph.auth.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.domain.UserIdentity;
import com.vibegraph.auth.dto.AuthResponse;
import com.vibegraph.auth.dto.LoginRequest;
import com.vibegraph.auth.dto.RegisterRequest;
import com.vibegraph.auth.dto.UserResponse;
import com.vibegraph.auth.oauth.OAuthAccountProfile;
import com.vibegraph.auth.repository.UserIdentityRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.common.exception.EmailAlreadyExistsException;
import com.vibegraph.common.exception.InvalidCredentialsException;
import com.vibegraph.common.exception.UnauthorizedException;

import lombok.RequiredArgsConstructor;

/**
 * Local (email + password) authentication plus OAuth profile linking.
 *
 * <p>Field validation is enforced by Bean Validation at the controller boundary (400) before any
 * method here runs, so {@link #register} can assume well-formed input and the duplicate-email
 * lookup never precedes validation. Passwords are BCrypt-hashed; the hash is never returned.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$9lW1wQ/S7o6dmA9pYH9B6ewQnyEA6LGLJfGVkQGneSAdfobYsDfkC";
    private static final String OAUTH_EMAIL_UNAVAILABLE = "oauth_email_unavailable";
    private static final String OAUTH_EMAIL_UNVERIFIED = "oauth_email_unverified";
    private static final String OAUTH_ACCOUNT_LINK_FAILED = "oauth_account_link_failed";

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUser currentUser;
    private final AccountSettingsService accountSettingsService;
    private final FeatureGateService featureGateService;
    private final AuditService auditService;

    /**
     * Create a local account and return a fresh token + safe user projection.
     *
     * @throws EmailAlreadyExistsException if the email is already registered (case-insensitive) → 409
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        featureGateService.assertEnabled(FeatureGateService.REGISTRATION);
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
        accountSettingsService.createDefaultSettings(saved);
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
        User user = userRepository.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        String passwordHash = user != null && user.getPasswordHash() != null
                ? user.getPasswordHash()
                : DUMMY_PASSWORD_HASH;
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        if (!passwordMatches || user == null || user.getPasswordHash() == null) {
            auditService.record(
                    "FAILED_LOGIN", null, null, "USER", request.email().trim(), "FAILURE",
                    java.util.Map.of("reason", "INVALID_CREDENTIALS"));
            throw new InvalidCredentialsException("Invalid email or password");
        }
        AuthResponse response = toAuthResponse(user);
        auditService.record("LOGIN", user.getId(), user.getId(), "USER", user.getId().toString(), "SUCCESS",
                java.util.Map.of("email", user.getEmail()));
        return response;
    }

    /**
     * Link or create a local account from a verified OAuth provider identity.
     *
     * @throws OAuth2AuthenticationException if the provider profile cannot be linked safely
     */
    @Transactional
    public AuthResponse oauthLogin(OAuthAccountProfile profile) {
        if (profile.email() == null || profile.email().isBlank()) {
            throw oauthError(OAUTH_EMAIL_UNAVAILABLE);
        }
        if (!profile.emailVerified()) {
            throw oauthError(OAUTH_EMAIL_UNVERIFIED);
        }
        try {
            User user = userIdentityRepository
                    .findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
                    .map(identity -> userRepository.findById(identity.getUserId())
                            .orElseThrow(() -> oauthError(OAUTH_ACCOUNT_LINK_FAILED)))
                    .orElseGet(() -> linkOrCreateUser(profile));
            updateTrustedProfileFields(user, profile);
            User saved = userRepository.save(user);
            auditService.record("OAUTH_LOGIN", saved.getId(), saved.getId(), "USER",
                    saved.getId().toString(), "SUCCESS",
                    java.util.Map.of("provider", profile.provider().name(), "email", saved.getEmail()));
            return toAuthResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            User recovered = recoverAfterConflict(profile);
            updateTrustedProfileFields(recovered, profile);
            User saved = userRepository.save(recovered);
            auditService.record("OAUTH_LOGIN", saved.getId(), saved.getId(), "USER",
                    saved.getId().toString(), "SUCCESS",
                    java.util.Map.of("provider", profile.provider().name(), "email", saved.getEmail()));
            return toAuthResponse(saved);
        }
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
        return toUserResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(jwtService.issue(user), toUserResponse(user));
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.from(user, accountSettingsService.findSettings(user.getId()));
    }

    private User linkOrCreateUser(OAuthAccountProfile profile) {
        User user = userRepository.findByEmailIgnoreCase(profile.email())
                .orElseGet(() -> createOAuthUser(profile));
        persistIdentity(profile, user);
        return user;
    }

    private User createOAuthUser(OAuthAccountProfile profile) {
        User user = User.builder()
                .email(profile.email())
                .passwordHash(null)
                .displayName(profile.displayName())
                .avatarUrl(profile.avatarUrl())
                .role(Role.USER)
                .emailVerified(true)
                .build();
        User saved = userRepository.save(user);
        accountSettingsService.createDefaultSettings(saved);
        return saved;
    }

    private void persistIdentity(OAuthAccountProfile profile, User user) {
        if (userIdentityRepository.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
                .isPresent()) {
            return;
        }
        userIdentityRepository.save(UserIdentity.builder()
                .userId(user.getId())
                .provider(profile.provider())
                .providerUserId(profile.providerUserId())
                .email(profile.email())
                .build());
    }

    private User recoverAfterConflict(OAuthAccountProfile profile) {
        return userIdentityRepository.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
                .map(identity -> userRepository.findById(identity.getUserId())
                        .orElseThrow(() -> oauthError(OAUTH_ACCOUNT_LINK_FAILED)))
                .or(() -> userRepository.findByEmailIgnoreCase(profile.email()))
                .orElseThrow(() -> oauthError(OAUTH_ACCOUNT_LINK_FAILED));
    }

    private void updateTrustedProfileFields(User user, OAuthAccountProfile profile) {
        user.setEmailVerified(true);
        if ((user.getDisplayName() == null || user.getDisplayName().isBlank())
                && profile.displayName() != null && !profile.displayName().isBlank()) {
            user.setDisplayName(profile.displayName());
        }
        if ((user.getAvatarUrl() == null || user.getAvatarUrl().isBlank())
                && profile.avatarUrl() != null && !profile.avatarUrl().isBlank()) {
            user.setAvatarUrl(profile.avatarUrl());
        }
    }

    private OAuth2AuthenticationException oauthError(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code));
    }
}
