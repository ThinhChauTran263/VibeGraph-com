package com.vibegraph.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "audit_retention_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditRetentionSetting {

    public static final short SINGLETON_ID = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Builder.Default
    @Column(name = "retention_days", nullable = false)
    private int retentionDays = 90;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
