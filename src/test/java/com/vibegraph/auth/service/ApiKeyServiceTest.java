package com.vibegraph.auth.service;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.ApiKey;
import com.vibegraph.auth.domain.Plan;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.domain.UserAccountSettings;
import com.vibegraph.auth.dto.AdminApiKeyCreateRequest;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("API key service")
class ApiKeyServiceTest {

    @Mock
    private CurrentUser currentUser;

    @Mock
    private ApiKeyRepository apiKeyRepository;

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
                userRepository,
                accountSettingsService,
                passwordEncoder,
                featureGateService,
                auditService);

        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .email("user@test.local")
                .role(Role.USER)
                .build();

        authenticatedUser = new AuthenticatedUser(userId, "user@test.local", Role.USER);

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
    }

    @Test
    @DisplayName("createForCurrentUser returns secret key once")
    void createForCurrentUser_returnsSecretKeyOnce() {
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(accountSettingsService.findSettings(userId)).thenReturn(settings);
        when(apiKeyRepository.countByUserIdAndDisabledAtIsNull(userId)).thenReturn(0);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedValue");
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey key = invocation.getArgument(0);
            key.setId(UUID.randomUUID());
            key.setCreatedAt(java.time.Instant.now());
            return key;
        });

        ApiKeyCreateResponse response = apiKeyService.createForCurrentUser(
                new ApiKeyCreateRequest("Test Key"));

        assertNotNull(response.secretKey());
        assertTrue(response.secretKey().startsWith("vbg_"));
        assertEquals(36, response.secretKey().length()); // vbg_ (4) + 32 chars
        assertNotNull(response.keyPrefix());
        assertEquals("Test Key", response.name());
        verify(passwordEncoder).encode(argThat(secret ->
                secret.toString().startsWith("vbg_") && secret.length() == 36));
        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    @DisplayName("createForCurrentUser stores only hash, never raw key")
    void createForCurrentUser_storesHashOnly() {
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(accountSettingsService.findSettings(userId)).thenReturn(settings);
        when(apiKeyRepository.countByUserIdAndDisabledAtIsNull(userId)).thenReturn(0);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedValue");
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey key = invocation.getArgument(0);
            key.setId(UUID.randomUUID());
            key.setCreatedAt(java.time.Instant.now());
            return key;
        });

        apiKeyService.createForCurrentUser(new ApiKeyCreateRequest("Test Key"));

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
                () -> apiKeyService.createForCurrentUser(new ApiKeyCreateRequest("Test Key")));

        verifyNoInteractions(currentUser, userRepository, accountSettingsService, passwordEncoder, apiKeyRepository);
    }

    @Test
    @DisplayName("createForCurrentUser throws when blocked")
    void createForCurrentUser_blockedUser_throws() {
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        doThrow(new AccountBlockedException("Blocked", "Account is blocked"))
                .when(accountSettingsService).assertNotBlocked(userId);

        assertThrows(AccountBlockedException.class,
                () -> apiKeyService.createForCurrentUser(new ApiKeyCreateRequest("Test Key")));
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("createForCurrentUser throws when creation disabled")
    void createForCurrentUser_creationDisabled_throws() {
        settings.setApiKeyCreationDisabled(true);
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(accountSettingsService.findSettings(userId)).thenReturn(settings);

        assertThrows(ApiKeysDisabledException.class,
                () -> apiKeyService.createForCurrentUser(new ApiKeyCreateRequest("Test Key")));
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("createForCurrentUser throws when plan limit reached")
    void createForCurrentUser_planLimitReached_throws() {
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(accountSettingsService.findSettings(userId)).thenReturn(settings);
        when(apiKeyRepository.countByUserIdAndDisabledAtIsNull(userId)).thenReturn(3);

        assertThrows(ApiKeyPlanLimitReachedException.class,
                () -> apiKeyService.createForCurrentUser(new ApiKeyCreateRequest("Test Key")));
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
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(apiKeyRepository.findByUserId(userId)).thenReturn(java.util.List.of(key1, key2));

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
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(apiKeyRepository.findByIdAndUserId(keyId, userId)).thenReturn(java.util.Optional.empty());

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
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(apiKeyRepository.findByIdAndUserId(keyId, userId)).thenReturn(java.util.Optional.of(apiKey));

        apiKeyService.disableForCurrentUser(keyId);

        assertNotNull(apiKey.getDisabledAt());
        verify(apiKeyRepository).save(apiKey);
    }

    @Test
    @DisplayName("createForUser as admin succeeds")
    void createForUser_asAdmin_succeeds() {
        UUID adminId = UUID.randomUUID();
        AuthenticatedUser adminPrincipal = new AuthenticatedUser(adminId, "admin@test.local", Role.ADMIN);

        when(currentUser.principal()).thenReturn(adminPrincipal);
        when(userRepository.existsById(userId)).thenReturn(true);
        when(accountSettingsService.findSettings(userId)).thenReturn(settings);
        when(apiKeyRepository.countByUserIdAndDisabledAtIsNull(userId)).thenReturn(0);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedValue");
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey key = invocation.getArgument(0);
            key.setId(UUID.randomUUID());
            key.setCreatedAt(java.time.Instant.now());
            return key;
        });

        ApiKeyCreateResponse response = apiKeyService.createForUser(
                new AdminApiKeyCreateRequest(userId, "Admin Created Key"));

        assertNotNull(response.secretKey());
        assertTrue(response.secretKey().startsWith("vbg_"));
        verify(apiKeyRepository).save(argThat(key -> key.getUserId().equals(userId)));
    }

    @Test
    @DisplayName("createForUser as non-admin throws")
    void createForUser_asNonAdmin_throws() {
        when(currentUser.principal()).thenReturn(authenticatedUser);

        assertThrows(ForbiddenException.class,
                () -> apiKeyService.createForUser(new AdminApiKeyCreateRequest(userId, "Test")));
        verify(apiKeyRepository, never()).save(any());
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
        when(apiKeyRepository.findByUserId(userId)).thenReturn(java.util.List.of(key));

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
        when(apiKeyRepository.findById(keyId)).thenReturn(java.util.Optional.of(apiKey));

        apiKeyService.disableForAnyUser(keyId);

        assertNotNull(apiKey.getDisabledAt());
        verify(apiKeyRepository).save(apiKey);
    }

    @Test
    @DisplayName("disableForAnyUser as non-admin throws")
    void disableForAnyUser_asNonAdmin_throws() {
        when(currentUser.principal()).thenReturn(authenticatedUser);

        assertThrows(ForbiddenException.class,
                () -> apiKeyService.disableForAnyUser(UUID.randomUUID()));
    }
}
