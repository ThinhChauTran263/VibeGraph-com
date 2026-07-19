package com.vibegraph.auth.domain;

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

/**
 * CLI / MCP API key (table {@code api_keys}). Only the key HASH is stored, never the raw key.
 *
 * <p>Only non-deleted keys participate in authentication and project uniqueness. A key
 * disabled by an administrator is locked and cannot be deleted by its owner.
 */
@Entity
@Table(name = "api_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "project_id", length = 64)
    private String projectId;

    @Column(name = "key_hash", nullable = false)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    @Column(name = "name")
    private String name;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "disabled_by", length = 16)
    private ApiKeyDisabledBy disabledBy;

    @Column(name = "disabled_reason", length = 255)
    private String disabledReason;

    @Column(name = "locked_by", length = 255)
    private String lockedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
