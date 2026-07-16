package com.vibegraph.auth.service;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.ApiKey;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.domain.UserAccountSettings;
import com.vibegraph.auth.dto.AdminApiKeyCreateRequest;
import com.vibegraph.auth.dto.ApiKeyCreateRequest;
import com.vibegraph.auth.dto.ApiKeyCreateResponse;
import com.vibegraph.auth.dto.ApiKeyResponse;
import com.vibegraph.auth.repository.ApiKeyRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.common.exception.ApiKeyPlanLimitReachedException;
import com.vibegraph.common.exception.ApiKeysDisabledException;
import com.vibegraph.common.exception.ForbiddenException;
import com.vibegraph.common.exception.UnauthorizedException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String KEY_PREFIX = "vbg_";
    private static final String BASE62_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int SECRET_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CurrentUser currentUser;
    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final AccountSettingsService accountSettingsService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ApiKeyCreateResponse createForCurrentUser(ApiKeyCreateRequest request) {
        UUID userId = currentUserEntity().getId();
        accountSettingsService.assertNotBlocked(userId);
        assertApiKeyCreationEnabled(userId);
        assertPlanLimitNotReached(userId);

        return createApiKey(userId, request.name());
    }

    @Transactional
    public ApiKeyCreateResponse createForUser(AdminApiKeyCreateRequest request) {
        assertCurrentUserIsAdmin();
        UUID targetUserId = request.userId();
        assertUserExists(targetUserId);
        accountSettingsService.assertNotBlocked(targetUserId);
        assertApiKeyCreationEnabled(targetUserId);
        assertPlanLimitNotReached(targetUserId);

        return createApiKey(targetUserId, request.name());
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listForCurrentUser() {
        UUID userId = currentUserEntity().getId();
        return apiKeyRepository.findByUserId(userId).stream()
                .map(ApiKeyResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listForUser(UUID userId) {
        assertCurrentUserIsAdmin();
        return apiKeyRepository.findByUserId(userId).stream()
                .map(ApiKeyResponse::from)
                .toList();
    }

    @Transactional
    public void disableForCurrentUser(UUID keyId) {
        UUID userId = currentUserEntity().getId();
        ApiKey apiKey = apiKeyRepository.findByIdAndUserId(keyId, userId)
                .orElseThrow(() -> new ForbiddenException("Access denied"));
        apiKey.setDisabledAt(java.time.Instant.now());
        apiKeyRepository.save(apiKey);
    }

    @Transactional
    public void disableForAnyUser(UUID keyId) {
        assertCurrentUserIsAdmin();
        ApiKey apiKey = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new ForbiddenException("Access denied"));
        apiKey.setDisabledAt(java.time.Instant.now());
        apiKeyRepository.save(apiKey);
    }

    private ApiKeyCreateResponse createApiKey(UUID userId, String name) {
        String secretKey = KEY_PREFIX + generateSecretKey();
        String keyHash = passwordEncoder.encode(secretKey);
        String keyPrefix = secretKey.substring(0, Math.min(12, secretKey.length()));

        ApiKey apiKey = ApiKey.builder()
                .userId(userId)
                .keyHash(keyHash)
                .keyPrefix(keyPrefix)
                .name(name)
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);

        return new ApiKeyCreateResponse(
                saved.getId(),
                saved.getKeyPrefix(),
                saved.getName(),
                secretKey,
                saved.getCreatedAt(),
                saved.getExpiresAt());
    }

    private String generateSecretKey() {
        StringBuilder secret = new StringBuilder(SECRET_LENGTH);
        for (int i = 0; i < SECRET_LENGTH; i++) {
            secret.append(BASE62_ALPHABET.charAt(SECURE_RANDOM.nextInt(BASE62_ALPHABET.length())));
        }
        return secret.toString();
    }

    private void assertApiKeyCreationEnabled(UUID userId) {
        UserAccountSettings settings = accountSettingsService.findSettings(userId);
        if (settings.isApiKeyCreationDisabled()) {
            throw new ApiKeysDisabledException("API key creation is disabled for this account");
        }
    }

    private void assertPlanLimitNotReached(UUID userId) {
        UserAccountSettings settings = accountSettingsService.findSettings(userId);
        int activeCount = apiKeyRepository.countByUserIdAndDisabledAtIsNull(userId);
        int limit = settings.getPlan().getApiKeyLimit();
        if (activeCount >= limit) {
            throw new ApiKeyPlanLimitReachedException(
                    "API key limit reached: " + activeCount + "/" + limit);
        }
    }

    private void assertCurrentUserIsAdmin() {
        if (currentUser.principal().role() != Role.ADMIN) {
            throw new ForbiddenException("Access denied");
        }
    }

    private void assertUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ForbiddenException("Access denied");
        }
    }

    private User currentUserEntity() {
        UUID userId = currentUser.id();
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
    }
}
