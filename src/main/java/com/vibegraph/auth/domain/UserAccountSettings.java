package com.vibegraph.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_account_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAccountSettings {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "storage_quota_override_bytes")
    private Long storageQuotaOverrideBytes;

    @Builder.Default
    @Column(name = "api_key_creation_disabled", nullable = false)
    private boolean apiKeyCreationDisabled = false;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    @Column(name = "blocked_reason")
    private String blockedReason;

    @Column(name = "blocked_reason_safe")
    private String blockedReasonSafe;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public boolean isBlocked() {
        return blockedAt != null;
    }

    public void block(String reason) {
        blockedAt = Instant.now();
        blockedReason = reason;
    }

    public void block(String reason, String safeReason) {
        blockedAt = Instant.now();
        blockedReason = reason;
        blockedReasonSafe = safeReason;
    }
}
