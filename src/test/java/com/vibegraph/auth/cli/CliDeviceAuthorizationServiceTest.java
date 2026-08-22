package com.vibegraph.auth.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.dto.ApiKeyCreateResponse;
import com.vibegraph.auth.dto.ProjectBindingResponse;
import com.vibegraph.auth.service.ApiKeyService;
import com.vibegraph.auth.service.ApiKeySecretProtector;
import com.vibegraph.graph.service.CliRepositoryService;

@ExtendWith(MockitoExtension.class)
class CliDeviceAuthorizationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T00:00:00Z");

    @Mock CliDeviceAuthorizationRepository repository;
    @Mock CurrentUser currentUser;
    @Mock ApiKeyService apiKeyService;
    @Mock CliRepositoryService cliRepositoryService;
    @Mock ApiKeySecretProtector secretProtector;

    private CliDeviceAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new CliDeviceAuthorizationService(
                repository,
                currentUser,
                apiKeyService,
                cliRepositoryService,
                new CliDeviceAuthorizationProperties("https://app.vibegraph.com", 600, 2),
                Clock.fixed(NOW, ZoneOffset.UTC),
                secretProtector);
        lenient().when(secretProtector.encrypt(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> "encrypted:" + invocation.getArgument(0));
        lenient().when(secretProtector.decrypt(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.<String>getArgument(0).replaceFirst("^encrypted:", ""));
    }

    @Test
    void start_ValidPkceChallenge_ReturnsExpiringVerificationRequest() {
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            CliDeviceAuthorization value = invocation.getArgument(0);
            value.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            return value;
        });

        CliDeviceStartResponse response = service.start(new CliDeviceStartRequest(
                "A".repeat(43), "Workstation", "demo", "INIT"));

        assertThat(response.deviceCode()).isNotBlank();
        assertThat(response.userCode()).matches("[A-Z0-9]{4}-[A-Z0-9]{4}");
        assertThat(response.verificationUriComplete())
                .startsWith("https://app.vibegraph.com/cli/authorize?request=");
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(response.intervalSeconds()).isEqualTo(2);
    }

    @Test
    void start_WithPreferredKey_EmbedsOnlyKeyIdInVerificationUrl() {
        UUID preferredKeyId = UUID.randomUUID();
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            CliDeviceAuthorization value = invocation.getArgument(0);
            value.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            return value;
        });

        CliDeviceStartResponse response = service.start(new CliDeviceStartRequest(
                "A".repeat(43), "Workstation", "demo", "CHANGE_KEY", preferredKeyId));

        assertThat(response.verificationUriComplete()).contains("&key=" + preferredKeyId);
        assertThat(response.verificationUriComplete()).doesNotContain("vbg_");
    }

    @Test
    void approve_ExistingProject_BindsReusableProjectCredential() {
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        CliDeviceAuthorization authorization = pending(requestId, "ABCD-EFGH");
        authorization.setBrowserSecretHash(CliDeviceAuthorizationService.sha256("browser-secret"));
        when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(authorization));
        when(currentUser.id()).thenReturn(userId);
        when(apiKeyService.getOrCreateCliCredential("project-1", "Workstation CLI"))
                .thenReturn(new ApiKeyCreateResponse(
                        keyId, "vbg_prefix", "Workstation CLI", "vbg_secret",
                        new ProjectBindingResponse("project-1", "Demo", null, null), null, null));

        CliDeviceApprovalResponse response = service.approve(
                requestId,
                new CliDeviceApprovalRequest("browser-secret", "EXISTING", "project-1", null));

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(authorization.getUserId()).isEqualTo(userId);
        assertThat(authorization.getProjectId()).isEqualTo("project-1");
        assertThat(authorization.getApiKeyId()).isEqualTo(keyId);
        verify(repository).save(authorization);
    }

    @Test
    void approve_SelectedOwnedKey_BindsSelectedCredential() {
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        CliDeviceAuthorization authorization = pending(requestId, "ABCD-EFGH");
        authorization.setBrowserSecretHash(CliDeviceAuthorizationService.sha256("browser-secret"));
        when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(authorization));
        when(currentUser.id()).thenReturn(userId);
        when(apiKeyService.getCliCredentialForCurrentUser(keyId))
                .thenReturn(new ApiKeyCreateResponse(
                        keyId, "vbg_prefix", "Selected", "vbg_secret",
                        new ProjectBindingResponse("project-1", "Demo", null, null), null, null));

        service.approve(requestId, new CliDeviceApprovalRequest(
                "browser-secret", "KEY", null, null, keyId));

        assertThat(authorization.getApiKeyId()).isEqualTo(keyId);
        assertThat(authorization.getProjectId()).isEqualTo("project-1");
        verify(apiKeyService).getCliCredentialForCurrentUser(keyId);
    }

    @Test
    void exchange_ApprovedRequest_VerifiesPkceAndConsumesSecretOnce() {
        UUID keyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String verifier = "v".repeat(48);
        CliDeviceAuthorization authorization = approved(keyId, userId, verifier);
        when(repository.findByDeviceCodeHashForUpdate(authorization.getDeviceCodeHash()))
                .thenReturn(Optional.of(authorization));
        CliDeviceTokenResponse response = service.exchange(
                "device-code-value", "poll-token-value", verifier);

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.apiKey()).isEqualTo("vbg_secret12345678");
        assertThat(response.projectId()).isEqualTo("project-1");
        assertThat(response.apiKeyId()).isEqualTo(keyId);
        assertThat(authorization.getStatus()).isEqualTo(CliDeviceAuthorizationStatus.CONSUMED);
        assertThat(authorization.getConsumedAt()).isEqualTo(NOW);
    }

    @Test
    void exchange_WrongVerifier_DoesNotConsumeAuthorization() {
        CliDeviceAuthorization authorization = approved(UUID.randomUUID(), UUID.randomUUID(), "v".repeat(48));
        when(repository.findByDeviceCodeHashForUpdate(authorization.getDeviceCodeHash()))
                .thenReturn(Optional.of(authorization));

        assertThatThrownBy(() -> service.exchange(
                "device-code-value", "poll-token-value", "x".repeat(48)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verifier");

        assertThat(authorization.getStatus()).isEqualTo(CliDeviceAuthorizationStatus.APPROVED);
    }

    private CliDeviceAuthorization pending(UUID id, String userCode) {
        return CliDeviceAuthorization.builder()
                .id(id)
                .deviceCodeHash("hash")
                .codeChallenge("A".repeat(43))
                .userCode(userCode)
                .deviceName("Workstation")
                .status(CliDeviceAuthorizationStatus.PENDING)
                .expiresAt(NOW.plusSeconds(600))
                .build();
    }

    private CliDeviceAuthorization approved(UUID keyId, UUID userId, String verifier) {
        String rawDeviceCode = "device-code-value";
        return CliDeviceAuthorization.builder()
                .id(UUID.randomUUID())
                .deviceCodeHash(CliDeviceAuthorizationService.sha256(rawDeviceCode))
                .pollSecretHash(CliDeviceAuthorizationService.sha256("poll-token-value"))
                .codeChallenge(CliDeviceAuthorizationService.pkceChallenge(verifier))
                .userCode("ABCD-EFGH")
                .deviceName("Workstation")
                .status(CliDeviceAuthorizationStatus.APPROVED)
                .userId(userId)
                .projectId("project-1")
                .projectName("Demo")
                .apiKeyId(keyId)
                .credentialCipher("vbg_secret12345678")
                .expiresAt(NOW.plusSeconds(600))
                .build();
    }
}
