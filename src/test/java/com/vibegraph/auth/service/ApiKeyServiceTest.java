package com.vibegraph.auth.service;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.entity.ApiKey;
import com.vibegraph.auth.domain.entity.Plan;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.entity.User;
import com.vibegraph.auth.domain.entity.UserAccountSettings;
import com.vibegraph.auth.dto.ApiKeyCreateRequest;
import com.vibegraph.auth.dto.ApiKeyCreateResponse;
import com.vibegraph.auth.dto.ApiKeyResponse;
import com.vibegraph.auth.repository.ApiKeyRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.common.exception.AccountBlockedException;
import com.vibegraph.common.exception.ApiKeyPlanLimitReachedException;
import com.vibegraph.common.exception.ApiKeysDisabledException;
import com.vibegraph.common.exception.FeatureDisabledException;
import com.vibegraph.common.exception.ForbiddenException;

@ExtendWith(MockitoExtension.class)
@DisplayName("API key service")
class ApiKeyServiceTest {

    @Mock
    private CurrentUser currentUser;

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private com.vibegraph.auth.repository.ProjectOwnershipRepository projectOwnershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountSettingsService accountSettingsService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private FeatureGateService featureGateService;

    @Mock
    private AuditService auditService;

    @Mock
    private ApiKeySecretProtector secretProtector;

    private ApiKeyService apiKeyService;

    private UUID userId;
    private User user;
    private AuthenticatedUser authenticatedUser;
    private Plan freePlan;
    private UserAccountSettings settings;

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(
                currentUser,
                apiKeyRepository,
                projectOwnershipRepository,
                userRepository,
                accountSettingsService,
                passwordEncoder,
                featureGateService,
                auditService,
                secretProtector);

        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .email("user@test.local")
                .role(Role.USER)
                .build();

        authenticatedUser = new AuthenticatedUser(userId, "user@test.local", Role.USER);
        lenient().when(userRepository.findByIdForUpdate(userId)).thenReturn(java.util.Optional.of(user));

        freePlan = Plan.builder()
                .id(UUID.randomUUID())
                .code("FREE")
                .name("Free")
                .apiKeyLimit(3)
                .build();

        settings = UserAccountSettings.builder()
                .userId(userId)
                .plan(freePlan)
                .apiKeyCreationDisabled(false)
                .build();
        lenient().when(projectOwnershipRepository.findByProjectIdAndOwnerIdAndDeletedAtIsNull("project-1", userId))
                .thenReturn(java.util.Optional.of(com.vibegraph.auth.domain.entity.ProjectOwnership.builder()
                        .projectId("project-1")
                        .ownerId(userId)
                        .name("Project One")
                        .sourceType(com.vibegraph.auth.domain.ProjectSourceType.LOCAL)
                        .status(com.vibegraph.auth.domain.ProjectOwnershipStatus.ANALYZED)
                        .build()));
    }

    @Test
    @DisplayName("createForCurrentUser returns secret key once")
    void createForCurrentUser_returnsSecretKeyOnce() {
        when(currentUser.id()).thenReturn(userId);
        when(accountSettingsService.findSettings(userId)).thenReturn(settings);
        when(apiKeyRepository.countByUserIdAndDeletedAtIsNull(userId)).thenReturn(0);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedValue");
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey key = invocation.getArgument(0);
            key.setId(UUID.randomUUID());
            key.setCreatedAt(java.time.Instant.now());
            return key;
        });

        ApiKeyCreateResponse response = apiKeyService.createForCurrentUser(
                new ApiKeyCreateRequest("Test Key", "project-1"));

        assertNotNull(response.secretKey());
        assertTrue(response.secretKey().startsWith("vbg_"));
        assertEquals(36, response.secretKey().length()); // vbg_ (4) + 32 chars
        assertNotNull(response.keyPrefix());
        assertEquals("Test Key", response.name());
        assertEquals("project-1", response.project().id());
        assertEquals("Project One", response.project().name());
        verify(passwordEncoder).encode(argThat(secret ->
                secret.toString().startsWith("vbg_") && secret.length() == 36));
        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    @DisplayName("createForCurrentUser rejects a project not owned by the user")
    void createForCurrentUser_wrongProjectOwner_throws() {
        when(currentUser.id()).thenReturn(userId);
        when(accountSettingsService.findSettings(userId)).thenReturn(settings);
        when(projectOwnershipRepository.findByProjectIdAndOwnerIdAndDeletedAtIsNull("other-project", userId))
                .thenReturn(java.util.Optional.empty());

        assertThrows(ForbiddenException.class, () -> apiKeyService.createForCurrentUser(
                new ApiKeyCreateRequest("Test Key", "other-project")));

        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("createForCurrentUser stores only hash, never raw key")
    void createForCurrentUser_storesHashOnly() {
        when(currentUser.id()).thenReturn(userId);
        when(accountSettingsService.findSettings(userId)).thenReturn(settings);
        when(apiKeyRepository.countByUserIdAndDeletedAtIsNull(userId)).thenReturn(0);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedValue");
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey key = invocation.getArgument(0);
            key.setId(UUID.randomUUID());
            key.setCreatedAt(java.time.Instant.now());
            return key;
        });

        apiKeyService.createForCurrentUser(new ApiKeyCreateRequest("Test Key", "project-1"));

        verify(apiKeyRepository).save(argThat(apiKey ->
                apiKey.getKeyHash().equals("$2a$10$hashedValue") &&
                !apiKey.getKeyHash().contains("vbg_")));
    }

    @Test
    @DisplayName("createForCurrentUser is blocked by the global API key feature flag before user lookup")
    void createForCurrentUser_globalFeatureDisabled_throws() {
        doThrow(new FeatureDisabledException(FeatureGateService.API_KEYS_CREATE_GLOBAL))
                .when(featureGateService).assertEnabled(FeatureGateService.API_KEYS_CREATE_GLOBAL);

        assertThrows(FeatureDisabledException.class,
                () -> apiKeyService.createForCurrentUser(new ApiKeyCreateRequest("Test Key", "project-1")));

        verifyNoInteractions(currentUser, userRepository, accountSettingsService, passwordEncoder, apiKeyRepository);
    }

    @Test
    @DisplayName("createForCurrentUser throws when blocked")
    void createForCurrentUser_blockedUser_throws() {
        when(currentUser.id()).thenReturn(userId);
        doThrow(new AccountBlockedException("Blocked", "Account is blocked"))
                .when(accountSettingsService).assertNotBlocked(userId);

        assertThrows(AccountBlockedException.class,
                () -> apiKeyService.createForCurrentUser(new ApiKeyCreateRequest("Test Key", "project-1")));
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("createForCurrentUser throws when creation disabled")
    void createForCurrentUser_creationDisabled_throws() {
        settings.setApiKeyCreationDisabled(true);
        when(currentUser.id()).thenReturn(userId);
        when(accountSettingsService.findSettings(userId)).thenReturn(settings);

        assertThrows(ApiKeysDisabledException.class,
                () -> apiKeyService.createForCurrentUser(new ApiKeyCreateRequest("Test Key", "project-1")));
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("createForCurrentUser throws when plan limit reached")
    void createForCurrentUser_planLimitReached_throws() {
        when(currentUser.id()).thenReturn(userId);
        when(accountSettingsService.findSettings(userId)).thenReturn(settings);
        when(apiKeyRepository.countByUserIdAndDeletedAtIsNull(userId)).thenReturn(3);

        assertThrows(ApiKeyPlanLimitReachedException.class,
                () -> apiKeyService.createForCurrentUser(new ApiKeyCreateRequest("Test Key", "project-1")));
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("listForCurrentUser returns keys without secrets")
    void listForCurrentUser_returnsKeysWithoutSecrets() {
        ApiKey key1 = ApiKey.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .keyHash("$2a$10$hash1")
                .keyPrefix("vbg_abcd1234")
                .name("Key 1")
                .createdAt(java.time.Instant.now())
                .build();
        ApiKey key2 = ApiKey.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .keyHash("$2a$10$hash2")
                .keyPrefix("vbg_efgh5678")
                .name("Key 2")
                .createdAt(java.time.Instant.now())
                .build();

        when(currentUser.id()).thenReturn(userId);
        lenient().when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(apiKeyRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(java.util.List.of(key1, key2));

        java.util.List<ApiKeyResponse> responses = apiKeyService.listForCurrentUser();

        assertEquals(2, responses.size());
        responses.forEach(response -> {
            assertNotNull(response.keyPrefix());
            assertNotNull(response.name());
        });
    }

    @Test
    @DisplayName("disableForCurrentUser enforces ownership")
    void disableForCurrentUser_wrongOwner_throws() {
        UUID keyId = UUID.randomUUID();
        when(currentUser.id()).thenReturn(userId);
        lenient().when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(apiKeyRepository.findByIdAndUserIdAndDeletedAtIsNull(keyId, userId))
                .thenReturn(java.util.Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> apiKeyService.disableForCurrentUser(keyId));
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("disableForCurrentUser sets disabled timestamp")
    void disableForCurrentUser_setsDisabledAt() {
        UUID keyId = UUID.randomUUID();
        ApiKey apiKey = ApiKey.builder()
                .id(keyId)
                .userId(userId)
                .keyHash("$2a$10$hash")
                .keyPrefix("vbg_test1234")
                .name("Test")
                .createdAt(java.time.Instant.now())
                .build();

        when(currentUser.id()).thenReturn(userId);
        lenient().when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(apiKeyRepository.findByIdAndUserIdAndDeletedAtIsNull(keyId, userId))
                .thenReturn(java.util.Optional.of(apiKey));
        when(apiKeyRepository.disableByOwnerUnlessAdminLocked(eq(keyId), eq(userId), any()))
                .thenReturn(1);

        apiKeyService.disableForCurrentUser(keyId);

        verify(apiKeyRepository).disableByOwnerUnlessAdminLocked(eq(keyId), eq(userId), any());
    }

    @Test
    @DisplayName("listForUser as admin succeeds")
    void listForUser_asAdmin_succeeds() {
        UUID adminId = UUID.randomUUID();
        AuthenticatedUser adminPrincipal = new AuthenticatedUser(adminId, "admin@test.local", Role.ADMIN);

        ApiKey key = ApiKey.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .keyHash("$2a$10$hash")
                .keyPrefix("vbg_test1234")
                .name("Test")
                .createdAt(java.time.Instant.now())
                .build();

        when(currentUser.principal()).thenReturn(adminPrincipal);
        when(apiKeyRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(java.util.List.of(key));

        java.util.List<ApiKeyResponse> responses = apiKeyService.listForUser(userId);

        assertEquals(1, responses.size());
        assertEquals("Test", responses.get(0).name());
    }

    @Test
    @DisplayName("listForUser as non-admin throws")
    void listForUser_asNonAdmin_throws() {
        when(currentUser.principal()).thenReturn(authenticatedUser);

        assertThrows(ForbiddenException.class,
                () -> apiKeyService.listForUser(userId));
    }

    @Test
    @DisplayName("disableForAnyUser as admin succeeds")
    void disableForAnyUser_asAdmin_succeeds() {
        UUID adminId = UUID.randomUUID();
        AuthenticatedUser adminPrincipal = new AuthenticatedUser(adminId, "admin@test.local", Role.ADMIN);
        UUID keyId = UUID.randomUUID();
        ApiKey apiKey = ApiKey.builder()
                .id(keyId)
                .userId(userId)
                .keyHash("$2a$10$hash")
                .keyPrefix("vbg_test1234")
                .name("Test")
                .createdAt(java.time.Instant.now())
                .build();

        when(currentUser.principal()).thenReturn(adminPrincipal);
        when(apiKeyRepository.findByIdAndDeletedAtIsNull(keyId)).thenReturn(java.util.Optional.of(apiKey));
        when(apiKeyRepository.disableByAdmin(eq(keyId), any(), anyString(), anyString())).thenReturn(1);

        apiKeyService.disableForAnyUser(keyId);

        verify(apiKeyRepository).disableByAdmin(eq(keyId), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("disableForAnyUser as non-admin throws")
    void disableForAnyUser_asNonAdmin_throws() {
        when(currentUser.principal()).thenReturn(authenticatedUser);

        assertThrows(ForbiddenException.class,
                () -> apiKeyService.disableForAnyUser(UUID.randomUUID()));
    }

    @Test
    @DisplayName("revealForCurrentUser returns the decrypted secret for the owner")
    void revealForCurrentUser_owner_decryptsStoredCipher() {
        UUID keyId = UUID.randomUUID();
        ApiKey apiKey = ApiKey.builder()
                .id(keyId)
                .userId(userId)
                .keyHash("$2a$10$hash")
                .keyPrefix("vbg_test1234")
                .secretCipher("cipher-blob")
                .name("Test")
                .createdAt(java.time.Instant.now())
                .build();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(apiKeyRepository.findByIdAndUserIdAndDeletedAtIsNull(keyId, userId))
                .thenReturn(java.util.Optional.of(apiKey));
        when(secretProtector.decrypt("cipher-blob")).thenReturn("vbg_plainSecret");

        assertEquals("vbg_plainSecret", apiKeyService.revealForCurrentUser(keyId));
        verify(auditService).recordCurrentUser(eq("API_KEY_REVEAL"), eq(userId), eq("API_KEY"),
                eq(keyId.toString()), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    @DisplayName("revealForCurrentUser rejects keys created before reveal support")
    void revealForCurrentUser_legacyKeyWithoutCipher_throws() {
        UUID keyId = UUID.randomUUID();
        ApiKey apiKey = ApiKey.builder()
                .id(keyId)
                .userId(userId)
                .keyHash("$2a$10$hash")
                .keyPrefix("vbg_test1234")
                .name("Test")
                .createdAt(java.time.Instant.now())
                .build();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(apiKeyRepository.findByIdAndUserIdAndDeletedAtIsNull(keyId, userId))
                .thenReturn(java.util.Optional.of(apiKey));

        assertThrows(ForbiddenException.class, () -> apiKeyService.revealForCurrentUser(keyId));
        verify(secretProtector, never()).decrypt(org.mockito.ArgumentMatchers.anyString());
    }
}
