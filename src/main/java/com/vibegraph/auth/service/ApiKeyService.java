package com.vibegraph.auth.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.ApiKey;
import com.vibegraph.auth.domain.ApiKeyDisabledBy;
import com.vibegraph.auth.domain.ProjectOwnership;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.domain.UserAccountSettings;
import com.vibegraph.auth.dto.ApiKeyCreateRequest;
import com.vibegraph.auth.dto.ApiKeyCreateResponse;
import com.vibegraph.auth.dto.ApiKeyResponse;
import com.vibegraph.auth.dto.ProjectBindingResponse;
import com.vibegraph.auth.repository.ApiKeyRepository;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.common.exception.ApiKeyAdminLockedException;
import com.vibegraph.common.exception.ApiKeyPlanLimitReachedException;
import com.vibegraph.common.exception.ApiKeyProjectConflictException;
import com.vibegraph.common.exception.ApiKeysDisabledException;
import com.vibegraph.common.exception.ForbiddenException;
import com.vibegraph.common.exception.UnauthorizedException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String KEY_PREFIX = "vbg_";
    private static final String BASE62_ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int SECRET_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CurrentUser currentUser;
    private final ApiKeyRepository apiKeyRepository;
    private final ProjectOwnershipRepository projectOwnershipRepository;
    private final UserRepository userRepository;
    private final AccountSettingsService accountSettingsService;
    private final PasswordEncoder passwordEncoder;
    private final FeatureGateService featureGateService;
    private final AuditService auditService;
    @Transactional
    public ApiKeyCreateResponse createForCurrentUser(ApiKeyCreateRequest request) {
        featureGateService.assertEnabled(FeatureGateService.API_KEYS_CREATE_GLOBAL);
        UUID userId = currentUserEntityForUpdate().getId();
        accountSettingsService.assertNotBlocked(userId);
        assertApiKeyCreationEnabled(userId);

        ProjectOwnership project = requireOwnedProject(request.projectId(), userId);
        assertProjectHasNoLiveKey(userId, project.getProjectId());
        assertPlanLimitNotReached(userId);
        ApiKeyCreateResponse response = createApiKey(userId, request.name(), project);
        auditService.recordCurrentUser("API_KEY_CREATE", userId, "API_KEY", response.id().toString(),
                Map.of("keyPrefix", response.keyPrefix(), "name", response.name(),
                        "projectId", project.getProjectId()));
        return response;
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listForCurrentUser() {
        return responses(apiKeyRepository.findByUserIdAndDeletedAtIsNull(currentUserEntity().getId()));
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listForUser(UUID userId) {
        assertCurrentUserIsAdmin();
        return responses(apiKeyRepository.findByUserIdAndDeletedAtIsNull(userId));
    }

    @Transactional
    public void disableForCurrentUser(UUID keyId) {
        UUID userId = currentUserEntity().getId();
        ApiKey apiKey = apiKeyRepository.findByIdAndUserIdAndDeletedAtIsNull(keyId, userId)
                .orElseThrow(() -> new ForbiddenException("Access denied"));
        if (apiKey.getDisabledBy() == ApiKeyDisabledBy.ADMIN) {
            throw new ApiKeyAdminLockedException("Administrator-locked API keys cannot be changed");
        }
        int disabled = apiKeyRepository.disableByOwnerUnlessAdminLocked(keyId, userId, Instant.now());
        if (disabled == 0) {
            throw new ApiKeyAdminLockedException("Administrator-locked API keys cannot be changed");
        }
        auditService.recordCurrentUser("API_KEY_DISABLE", userId, "API_KEY", keyId.toString(),
                Map.of("disabledBy", "USER", "projectId", safeProjectId(apiKey)));
    }

    @Transactional
    public void enableForCurrentUser(UUID keyId) {
        UUID userId = currentUserEntity().getId();
        ApiKey apiKey = apiKeyRepository.findByIdAndUserIdAndDeletedAtIsNull(keyId, userId)
                .orElseThrow(() -> new ForbiddenException("Access denied"));
        if (apiKey.getDisabledBy() == ApiKeyDisabledBy.ADMIN) {
            throw new ApiKeyAdminLockedException("Administrator-locked API keys cannot be changed");
        }
        if (apiKey.getDisabledAt() == null) {
            return;
        }
        if (apiKey.getDisabledBy() != ApiKeyDisabledBy.USER) {
            throw new ForbiddenException("API key cannot be enabled by owner");
        }
        int enabled = apiKeyRepository.enableByOwnerIfUserDisabled(keyId, userId);
        if (enabled == 0) {
            throw new ForbiddenException("API key cannot be enabled by owner");
        }
        auditService.recordCurrentUser("API_KEY_ENABLE", userId, "API_KEY", keyId.toString(),
                Map.of("projectId", safeProjectId(apiKey)));
    }

    @Transactional
    public void disableForAnyUser(UUID keyId) {
        assertCurrentUserIsAdmin();
        ApiKey apiKey = apiKeyRepository.findByIdAndDeletedAtIsNull(keyId)
                .orElseThrow(() -> new ForbiddenException("Access denied"));
        int disabled = apiKeyRepository.disableByAdmin(
                keyId, Instant.now(), "Disabled by administrator", currentUser.principal().email());
        if (disabled == 0) {
            throw new ForbiddenException("Access denied");
        }
        auditService.recordCurrentUser("API_KEY_DISABLE", apiKey.getUserId(), "API_KEY", keyId.toString(),
                Map.of("disabledBy", "ADMIN", "projectId", safeProjectId(apiKey)));
    }

    @Transactional
    public void unlockForAnyUser(UUID keyId) {
        assertCurrentUserIsAdmin();
        ApiKey apiKey = apiKeyRepository.findByIdAndDeletedAtIsNull(keyId)
                .orElseThrow(() -> new ForbiddenException("Access denied"));
        if (apiKey.getDisabledBy() != ApiKeyDisabledBy.ADMIN || apiKeyRepository.unlockByAdmin(keyId) == 0) {
            throw new ForbiddenException("API key is not administrator-locked");
        }
        auditService.recordCurrentUser("API_KEY_UNLOCK", apiKey.getUserId(), "API_KEY", keyId.toString(),
                Map.of("projectId", safeProjectId(apiKey)));
    }

    @Transactional
    public void deleteForCurrentUser(UUID keyId) {
        UUID userId = currentUserEntity().getId();
        ApiKey apiKey = apiKeyRepository.findByIdAndUserIdAndDeletedAtIsNull(keyId, userId)
                .orElseThrow(() -> new ForbiddenException("Access denied"));
        if (apiKey.getDisabledBy() == ApiKeyDisabledBy.ADMIN) {
            throw new ApiKeyAdminLockedException("Administrator-locked API keys cannot be deleted");
        }
        int deleted = apiKeyRepository.softDeleteByOwnerUnlessAdminLocked(keyId, userId, Instant.now());
        if (deleted == 0) {
            throw new ApiKeyAdminLockedException("Administrator-locked API keys cannot be deleted");
        }
        auditService.recordCurrentUser("API_KEY_DELETE", userId, "API_KEY", keyId.toString(),
                Map.of("projectId", safeProjectId(apiKey), "keyPrefix", apiKey.getKeyPrefix()));
    }

    private List<ApiKeyResponse> responses(List<ApiKey> apiKeys) {
        return apiKeys.stream()
                .map(apiKey -> ApiKeyResponse.from(apiKey, findBoundProject(apiKey)))
                .toList();
    }
    private ApiKeyCreateResponse createApiKey(UUID userId, String name, ProjectOwnership project) {
        String secretKey = KEY_PREFIX + generateSecretKey();
        ApiKey apiKey = ApiKey.builder()
                .userId(userId)
                .projectId(project.getProjectId())
                .keyHash(passwordEncoder.encode(secretKey))
                .keyPrefix(secretKey.substring(0, Math.min(12, secretKey.length())))
                .name(name)
                .build();
        try {
            ApiKey saved = apiKeyRepository.save(apiKey);
            apiKeyRepository.flush();
            return new ApiKeyCreateResponse(saved.getId(), saved.getKeyPrefix(), saved.getName(), secretKey,
                    ProjectBindingResponse.from(project), saved.getCreatedAt(), saved.getExpiresAt());
        } catch (DataIntegrityViolationException ex) {
            throw new ApiKeyProjectConflictException("An API key already exists for this project");
        }
    }

    private void assertProjectHasNoLiveKey(UUID userId, String projectId) {
        apiKeyRepository.findByUserIdAndProjectIdAndDeletedAtIsNull(userId, projectId).ifPresent(existing -> {
            if (existing.getDisabledBy() == ApiKeyDisabledBy.ADMIN) {
                throw new ApiKeyAdminLockedException(
                        "This project has an administrator-locked API key and cannot receive a replacement");
            }
            throw new ApiKeyProjectConflictException(
                    "Delete the existing API key before creating a replacement for this project");
        });
    }

    private ProjectOwnership requireOwnedProject(String projectId, UUID userId) {
        return projectOwnershipRepository.findByProjectIdAndOwnerId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException("Access denied"));
    }

    private ProjectOwnership findBoundProject(ApiKey apiKey) {
        return apiKey.getProjectId() == null
                ? null
                : projectOwnershipRepository.findById(apiKey.getProjectId()).orElse(null);
    }

    private String safeProjectId(ApiKey apiKey) {
        return apiKey.getProjectId() == null ? "UNBOUND" : apiKey.getProjectId();
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
        int activeCount = apiKeyRepository.countByUserIdAndDeletedAtIsNull(userId);
        int limit = settings.getPlan().getApiKeyLimit();
        if (activeCount >= limit) {
            throw new ApiKeyPlanLimitReachedException("API key limit reached: " + activeCount + "/" + limit);
        }
    }

    private void assertCurrentUserIsAdmin() {
        if (currentUser.principal().role() != Role.ADMIN) {
            throw new ForbiddenException("Access denied");
        }
    }

    private User currentUserEntity() {
        UUID userId = currentUser.id();
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
    }

    private User currentUserEntityForUpdate() {
        UUID userId = currentUser.id();
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
    }
}
