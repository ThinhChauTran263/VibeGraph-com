package com.vibegraph.auth.cli;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.dto.ApiKeyCreateResponse;
import com.vibegraph.auth.service.ApiKeySecretProtector;
import com.vibegraph.auth.service.ApiKeyService;
import com.vibegraph.common.exception.ForbiddenException;
import com.vibegraph.graph.dto.request.CliRepositoryCreateRequest;
import com.vibegraph.graph.dto.response.CliRepositorySetupResponse;
import com.vibegraph.graph.service.CliRepositoryService;


/** Coordinates short-lived browser authorization and one-time CLI credential exchange. */
@Service
public class CliDeviceAuthorizationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final CliDeviceAuthorizationRepository repository;
    private final CurrentUser currentUser;
    private final ApiKeyService apiKeyService;
    private final CliRepositoryService cliRepositoryService;
    private final CliDeviceAuthorizationProperties properties;
    private final Clock clock;
    private final ApiKeySecretProtector secretProtector;

    public CliDeviceAuthorizationService(
            CliDeviceAuthorizationRepository repository,
            CurrentUser currentUser,
            ApiKeyService apiKeyService,
            CliRepositoryService cliRepositoryService,
            CliDeviceAuthorizationProperties properties,
            Clock clock,
            ApiKeySecretProtector secretProtector) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.apiKeyService = apiKeyService;
        this.cliRepositoryService = cliRepositoryService;
        this.properties = properties;
        this.clock = clock;
        this.secretProtector = secretProtector;
    }

    @Transactional
    public CliDeviceStartResponse start(CliDeviceStartRequest request) {
        Instant expiresAt = now().plus(properties.getTtlSeconds(), ChronoUnit.SECONDS);
        String deviceCode = randomToken(32);
        String browserSecret = randomToken(24);
        String pollToken = randomToken(24);
        CliDeviceAuthorization authorization = CliDeviceAuthorization.builder()
                .deviceCodeHash(sha256(deviceCode))
                .browserSecretHash(sha256(browserSecret))
                .pollSecretHash(sha256(pollToken))
                .codeChallenge(request.codeChallenge())
                .userCode(userCode())
                .deviceName(normalize(request.deviceName(), "VibeGraph CLI"))
                .status(CliDeviceAuthorizationStatus.PENDING)
                .preferredApiKeyId(request.preferredApiKeyId())
                .expiresAt(expiresAt)
                .build();
        CliDeviceAuthorization saved = repository.save(authorization);
        String verificationUri = trimTrailingSlash(properties.getFrontendUrl()) + "/cli/authorize";
        String preferredKey = saved.getPreferredApiKeyId() == null
                ? "" : "&key=" + saved.getPreferredApiKeyId();
        String complete = verificationUri + "?request=" + saved.getId() + preferredKey
                + "#secret=" + browserSecret;
        return new CliDeviceStartResponse(
                saved.getId(), deviceCode, saved.getUserCode(), verificationUri, complete,
                pollToken, properties.getPollIntervalSeconds(), expiresAt);
    }

    /** Removes expired one-time requests so browser auth metadata cannot grow without bound. */
    @Transactional
    @Scheduled(cron = "${vibegraph.cli.device.cleanup-cron:0 20 3 * * ?}")
    public void cleanupExpiredAuthorizations() {
        repository.deleteByExpiresAtBefore(now());
    }

    @Transactional
    public CliDeviceApprovalResponse approve(UUID requestId, CliDeviceApprovalRequest request) {
        CliDeviceAuthorization authorization = getActive(requestId);
        if (!constantTimeEquals(authorization.getBrowserSecretHash(), sha256(request.browserSecret()))) {
            throw new ForbiddenException("Invalid CLI authorization secret");
        }
        if (authorization.getStatus() != CliDeviceAuthorizationStatus.PENDING) {
            throw new IllegalArgumentException("CLI authorization is no longer pending");
        }
        String mode = request.projectMode().trim().toUpperCase(java.util.Locale.ROOT);
        ApiKeyCreateResponse credential;
        if ("KEY".equals(mode)) {
            UUID selectedKeyId = request.apiKeyId() != null
                    ? request.apiKeyId() : authorization.getPreferredApiKeyId();
            if (selectedKeyId == null) {
                throw new IllegalArgumentException("apiKeyId is required for a selected API key");
            }
            credential = apiKeyService.getCliCredentialForCurrentUser(selectedKeyId);
        } else if ("EXISTING".equals(mode)) {
            if (request.projectId() == null || request.projectId().isBlank()) {
                throw new IllegalArgumentException("projectId is required for an existing project");
            }
            credential = apiKeyService.getOrCreateCliCredential(
                    request.projectId().trim(), authorization.getDeviceName() + " CLI");
        } else if ("NEW".equals(mode)) {
            CliRepositorySetupResponse setup = cliRepositoryService.create(
                    new CliRepositoryCreateRequest(request.projectName()));
            credential = setup.apiKey();
        } else {
            throw new IllegalArgumentException("projectMode must be KEY, EXISTING or NEW");
        }

        authorization.setUserId(currentUser.id());
        authorization.setProjectId(credential.project().id());
        authorization.setProjectName(credential.project().name());
        authorization.setApiKeyId(credential.id());
        authorization.setCredentialCipher(protect(credential.secretKey()));
        authorization.setStatus(CliDeviceAuthorizationStatus.APPROVED);
        authorization.setApprovedAt(now());
        repository.save(authorization);
        return new CliDeviceApprovalResponse(
                authorization.getStatus().name(), authorization.getProjectId(),
                authorization.getProjectName(), authorization.getExpiresAt());
    }

    @Transactional
    public CliDeviceTokenResponse exchange(String deviceCode, String pollToken, String codeVerifier) {
        CliDeviceAuthorization authorization = repository
                .findByDeviceCodeHashForUpdate(sha256(deviceCode))
                .orElseThrow(() -> new IllegalArgumentException("Invalid CLI device code"));
        expireIfNeeded(authorization);
        if (!constantTimeEquals(authorization.getPollSecretHash(), sha256(pollToken))) {
            throw new IllegalArgumentException("Invalid CLI polling secret");
        }
        return exchangeApproved(authorization, codeVerifier);
    }

    @Transactional
    public CliDeviceTokenResponse status(String deviceCode, String pollToken) {
        CliDeviceAuthorization authorization = repository
                .findByDeviceCodeHashForUpdate(sha256(deviceCode))
                .orElseThrow(() -> new IllegalArgumentException("Invalid CLI device code"));
        expireIfNeeded(authorization);
        if (!constantTimeEquals(authorization.getPollSecretHash(), sha256(pollToken))) {
            throw new IllegalArgumentException("Invalid CLI polling secret");
        }
        if (authorization.getStatus() == CliDeviceAuthorizationStatus.PENDING) {
            return CliDeviceTokenResponse.pending(authorization.getExpiresAt());
        }
        return new CliDeviceTokenResponse(
                authorization.getStatus().name(), null, authorization.getApiKeyId(),
                authorization.getProjectId(), authorization.getProjectName(), authorization.getExpiresAt(),
                java.util.List.of());
    }

    private CliDeviceTokenResponse exchangeApproved(
            CliDeviceAuthorization authorization, String verifier) {
        if (authorization.getStatus() != CliDeviceAuthorizationStatus.APPROVED) {
            return new CliDeviceTokenResponse(
                    authorization.getStatus().name(), null, authorization.getApiKeyId(),
                    authorization.getProjectId(), authorization.getProjectName(), authorization.getExpiresAt(),
                    java.util.List.of());
        }
        if (!constantTimeEquals(authorization.getCodeChallenge(), pkceChallenge(verifier))) {
            throw new IllegalArgumentException("Invalid code verifier");
        }
        String secret = unprotect(authorization.getCredentialCipher());
        authorization.setCredentialCipher(null);
        authorization.setStatus(CliDeviceAuthorizationStatus.CONSUMED);
        authorization.setConsumedAt(now());
        repository.save(authorization);
        return new CliDeviceTokenResponse(
                "APPROVED", secret, authorization.getApiKeyId(), authorization.getProjectId(), authorization.getProjectName(),
                authorization.getExpiresAt(), apiKeyService.listForCliUser(authorization.getUserId()));
    }

    private CliDeviceAuthorization getActive(UUID id) {
        CliDeviceAuthorization authorization = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("CLI authorization request not found"));
        expireIfNeeded(authorization);
        return authorization;
    }

    private void expireIfNeeded(CliDeviceAuthorization authorization) {
        if (authorization.getExpiresAt().isBefore(now())
                && authorization.getStatus() != CliDeviceAuthorizationStatus.CONSUMED) {
            authorization.setStatus(CliDeviceAuthorizationStatus.EXPIRED);
            repository.save(authorization);
            throw new IllegalArgumentException("CLI authorization expired");
        }
    }

    private String protect(String secret) {
        return secretProtector.encrypt(secret);
    }

    private String unprotect(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("CLI credential is unavailable");
        }
        return secretProtector.decrypt(value);
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private String userCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder value = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) value.append('-');
            value.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return value.toString();
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return BASE64_URL.encodeToString(value);
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    static String pkceChallenge(String verifier) {
        return BASE64_URL.encodeToString(hash(verifier));
    }

    private static byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
