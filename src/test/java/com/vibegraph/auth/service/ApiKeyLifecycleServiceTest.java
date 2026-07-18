package com.vibegraph.auth.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.ApiKey;
import com.vibegraph.auth.domain.ApiKeyDisabledBy;
import com.vibegraph.auth.domain.Plan;
import com.vibegraph.auth.domain.ProjectOwnership;
import com.vibegraph.auth.domain.ProjectOwnershipStatus;
import com.vibegraph.auth.domain.ProjectSourceType;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.domain.UserAccountSettings;
import com.vibegraph.auth.dto.ApiKeyCreateRequest;
import com.vibegraph.auth.repository.ApiKeyRepository;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.common.exception.ApiKeyAdminLockedException;
import com.vibegraph.common.exception.ApiKeyProjectConflictException;

@ExtendWith(MockitoExtension.class)
@DisplayName("API key lifecycle service")
class ApiKeyLifecycleServiceTest {
    @Mock private CurrentUser currentUser;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private ProjectOwnershipRepository projectOwnershipRepository;
    @Mock private UserRepository userRepository;
    @Mock private AccountSettingsService accountSettingsService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private FeatureGateService featureGateService;
    @Mock private AuditService auditService;

    private ApiKeyService service;
    private UUID userId;
    private User user;
    private ProjectOwnership project;

    @BeforeEach
    void setUp() {
        service = new ApiKeyService(currentUser, apiKeyRepository, projectOwnershipRepository,
                userRepository, accountSettingsService, passwordEncoder, featureGateService, auditService);
        userId = UUID.randomUUID();
        user = User.builder().id(userId).email("user@test.local").role(Role.USER).build();
        project = ProjectOwnership.builder().projectId("project-1").ownerId(userId).name("Project One")
                .sourceType(ProjectSourceType.LOCAL).status(ProjectOwnershipStatus.ANALYZED).build();
        Plan plan = Plan.builder().id(UUID.randomUUID()).code("FREE").name("Free").apiKeyLimit(3).build();
        UserAccountSettings settings = UserAccountSettings.builder()
                .userId(userId).plan(plan).apiKeyCreationDisabled(false).build();
        lenient().when(currentUser.id()).thenReturn(userId);
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        lenient().when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        lenient().when(accountSettingsService.findSettings(userId)).thenReturn(settings);
    }

    @Test
    void create_projectWithoutExistingKey_succeeds() {
        stubCreate();

        var response = service.createForCurrentUser(new ApiKeyCreateRequest("CLI", "project-1"));

        assertEquals("project-1", response.project().id());
        verify(apiKeyRepository).findByUserIdAndProjectIdAndDeletedAtIsNull(userId, "project-1");
        verify(auditService).recordCurrentUser("API_KEY_CREATE", userId, "API_KEY",
                response.id().toString(), java.util.Map.of(
                        "keyPrefix", response.keyPrefix(), "name", "CLI", "projectId", "project-1"));
    }

    @Test
    void create_secondKeyForProject_rejected() {
        stubCreationChecks();
        when(apiKeyRepository.findByUserIdAndProjectIdAndDeletedAtIsNull(userId, "project-1"))
                .thenReturn(Optional.of(key(null)));

        assertThrows(ApiKeyProjectConflictException.class,
                () -> service.createForCurrentUser(new ApiKeyCreateRequest("Replacement", "project-1")));
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void create_afterUserDisableStillRequiresDelete() {
        stubCreationChecks();
        ApiKey disabled = key(ApiKeyDisabledBy.USER);
        disabled.setDisabledAt(Instant.now());
        when(apiKeyRepository.findByUserIdAndProjectIdAndDeletedAtIsNull(userId, "project-1"))
                .thenReturn(Optional.of(disabled));

        assertThrows(ApiKeyProjectConflictException.class,
                () -> service.createForCurrentUser(new ApiKeyCreateRequest("Replacement", "project-1")));
    }

    @Test
    void delete_ownedNonLockedKey_softDeletesAndAudits() {
        UUID keyId = UUID.randomUUID();
        ApiKey apiKey = key(ApiKeyDisabledBy.USER);
        apiKey.setId(keyId);
        when(apiKeyRepository.findByIdAndUserIdAndDeletedAtIsNull(keyId, userId)).thenReturn(Optional.of(apiKey));
        when(apiKeyRepository.softDeleteByOwnerUnlessAdminLocked(any(), any(), any())).thenReturn(1);

        service.deleteForCurrentUser(keyId);

        verify(apiKeyRepository).softDeleteByOwnerUnlessAdminLocked(any(), any(), any());
        verify(auditService).recordCurrentUser("API_KEY_DELETE", userId, "API_KEY", keyId.toString(),
                java.util.Map.of("projectId", "project-1", "keyPrefix", apiKey.getKeyPrefix()));
    }
    @Test
    void create_afterDelete_succeeds() {
        stubCreate();

        var response = service.createForCurrentUser(new ApiKeyCreateRequest("Replacement", "project-1"));

        assertNotNull(response.secretKey());
    }

    @Test
    void delete_adminLockedKey_rejected() {
        UUID keyId = UUID.randomUUID();
        ApiKey apiKey = key(ApiKeyDisabledBy.ADMIN);
        apiKey.setId(keyId);
        apiKey.setDisabledAt(Instant.now());
        when(apiKeyRepository.findByIdAndUserIdAndDeletedAtIsNull(keyId, userId)).thenReturn(Optional.of(apiKey));

        assertThrows(ApiKeyAdminLockedException.class, () -> service.deleteForCurrentUser(keyId));
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void create_whenProjectHasAdminLockedKey_rejectedClearly() {
        stubCreationChecks();
        ApiKey locked = key(ApiKeyDisabledBy.ADMIN);
        locked.setDisabledAt(Instant.now());
        when(apiKeyRepository.findByUserIdAndProjectIdAndDeletedAtIsNull(userId, "project-1"))
                .thenReturn(Optional.of(locked));

        assertThrows(ApiKeyAdminLockedException.class,
                () -> service.createForCurrentUser(new ApiKeyCreateRequest("Replacement", "project-1")));
    }

    @Test
    void adminDisable_locksKeyAndAudits() {
        UUID adminId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        ApiKey apiKey = key(null);
        apiKey.setId(keyId);
        when(currentUser.principal()).thenReturn(new AuthenticatedUser(adminId, "admin@test.local", Role.ADMIN));
        when(apiKeyRepository.findByIdAndDeletedAtIsNull(keyId)).thenReturn(Optional.of(apiKey));
        when(apiKeyRepository.disableByAdmin(any(), any(), anyString(), anyString())).thenReturn(1);

        service.disableForAnyUser(keyId);

        verify(apiKeyRepository).disableByAdmin(any(), any(), anyString(), anyString());
        verify(auditService).recordCurrentUser("API_KEY_DISABLE", userId, "API_KEY", keyId.toString(),
                java.util.Map.of("disabledBy", "ADMIN", "projectId", "project-1"));
    }

    @Test
    void disableForCurrentUser_marksDisabledByUser() {
        UUID keyId = UUID.randomUUID();
        ApiKey apiKey = key(null);
        apiKey.setId(keyId);
        when(apiKeyRepository.findByIdAndUserIdAndDeletedAtIsNull(keyId, userId)).thenReturn(Optional.of(apiKey));
        when(apiKeyRepository.disableByOwnerUnlessAdminLocked(any(), any(), any())).thenReturn(1);

        service.disableForCurrentUser(keyId);

        verify(apiKeyRepository).disableByOwnerUnlessAdminLocked(any(), any(), any());
    }

    @Test
    void enableForCurrentUser_clearsUserDisabledKeyAndAudits() {
        UUID keyId = UUID.randomUUID();
        ApiKey apiKey = key(ApiKeyDisabledBy.USER);
        apiKey.setId(keyId);
        apiKey.setDisabledAt(Instant.now());
        when(apiKeyRepository.findByIdAndUserIdAndDeletedAtIsNull(keyId, userId)).thenReturn(Optional.of(apiKey));
        when(apiKeyRepository.enableByOwnerIfUserDisabled(keyId, userId)).thenReturn(1);

        service.enableForCurrentUser(keyId);

        verify(apiKeyRepository).enableByOwnerIfUserDisabled(keyId, userId);
        verify(auditService).recordCurrentUser("API_KEY_ENABLE", userId, "API_KEY", keyId.toString(),
                java.util.Map.of("projectId", "project-1"));
    }

    @Test
    void enableForCurrentUser_rejectsAdminLockedKey() {
        UUID keyId = UUID.randomUUID();
        ApiKey apiKey = key(ApiKeyDisabledBy.ADMIN);
        apiKey.setId(keyId);
        apiKey.setDisabledAt(Instant.now());
        when(apiKeyRepository.findByIdAndUserIdAndDeletedAtIsNull(keyId, userId)).thenReturn(Optional.of(apiKey));

        assertThrows(ApiKeyAdminLockedException.class, () -> service.enableForCurrentUser(keyId));
        verify(apiKeyRepository, never()).enableByOwnerIfUserDisabled(any(), any());
    }

    @Test
    void adminUnlock_clearsLockSoUserCanDeleteBeforeReplacement() {
        UUID adminId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        ApiKey locked = key(ApiKeyDisabledBy.ADMIN);
        locked.setId(keyId);
        locked.setDisabledAt(Instant.now());
        when(currentUser.principal()).thenReturn(new AuthenticatedUser(adminId, "admin@test.local", Role.ADMIN));
        when(apiKeyRepository.findByIdAndDeletedAtIsNull(keyId)).thenReturn(Optional.of(locked));
        when(apiKeyRepository.unlockByAdmin(keyId)).thenReturn(1);

        service.unlockForAnyUser(keyId);

        verify(apiKeyRepository).unlockByAdmin(keyId);
        verify(auditService).recordCurrentUser("API_KEY_UNLOCK", userId, "API_KEY", keyId.toString(),
                java.util.Map.of("projectId", "project-1"));
    }

    private void stubCreate() {
        stubCreationChecks();
        when(apiKeyRepository.findByUserIdAndProjectIdAndDeletedAtIsNull(userId, "project-1"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        AtomicReference<ApiKey> persisted = new AtomicReference<>();
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey value = invocation.getArgument(0);
            value.setId(UUID.randomUUID());
            value.setCreatedAt(Instant.now());
            persisted.set(value);
            return value;
        });
    }

    private void stubCreationChecks() {
        lenient().when(apiKeyRepository.countByUserIdAndDeletedAtIsNull(userId)).thenReturn(0);
        when(projectOwnershipRepository.findByProjectIdAndOwnerId("project-1", userId))
                .thenReturn(Optional.of(project));
    }

    private ApiKey key(ApiKeyDisabledBy disabledBy) {
        return ApiKey.builder().userId(userId).projectId("project-1").keyHash("hash")
                .keyPrefix("vbg_12345678").name("CLI").disabledBy(disabledBy).build();
    }
}
