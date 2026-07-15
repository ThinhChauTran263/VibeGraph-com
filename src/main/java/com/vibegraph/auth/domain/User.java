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
 * Control-plane user account (table {@code users}).
 *
 * <p>Source of truth for identity. {@code passwordHash} is nullable: OAuth-only accounts
 * have no local password. {@code createdAt}/{@code updatedAt} are managed by the database
 * (defaults + {@code trg_users_updated} trigger) and are therefore read-only to JPA.
 *
 * <p>Case-insensitive email uniqueness is enforced by the functional index
 * {@code uq_users_email_lower}, not a column constraint.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false)
    private String email;

    /** BCrypt hash; {@code null} for OAuth-only accounts. Never exposed in any response. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.USER;

    @Builder.Default
    @Column(name = "quota_bytes", nullable = false)
    private long quotaBytes = 524_288_000L;

    @Builder.Default
    @Column(name = "used_bytes", nullable = false)
    private long usedBytes = 0L;

    @Builder.Default
    @Column(name = "deactivated", nullable = false)
    private boolean deactivated = false;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @Column(name = "deactivation_reason", length = 500)
    private String deactivationReason;

    @Column(name = "deactivation_reason_safe", length = 240)
    private String deactivationReasonSafe;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
