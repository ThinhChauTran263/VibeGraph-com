package com.vibegraph.auth.cli;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Persisted, single-use browser authorization request for a CLI installation. */
@Entity
@Table(name = "cli_device_authorizations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CliDeviceAuthorization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "device_code_hash", nullable = false, unique = true, length = 64)
    private String deviceCodeHash;

    @Column(name = "browser_secret_hash", nullable = false, length = 64)
    private String browserSecretHash;

    @Column(name = "poll_secret_hash", nullable = false, length = 64)
    private String pollSecretHash;

    @Column(name = "code_challenge", nullable = false, length = 128)
    private String codeChallenge;

    @Column(name = "user_code", nullable = false, length = 16)
    private String userCode;

    @Column(name = "device_name", length = 120)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CliDeviceAuthorizationStatus status;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "project_id", length = 64)
    private String projectId;

    @Column(name = "project_name", length = 255)
    private String projectName;

    @Column(name = "api_key_id")
    private UUID apiKeyId;

    @Column(name = "preferred_api_key_id")
    private UUID preferredApiKeyId;

    @Column(name = "credential_cipher", columnDefinition = "TEXT")
    private String credentialCipher;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
