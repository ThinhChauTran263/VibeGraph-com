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
@Table(name = "project_usage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectUsage {

    @Id
    @Column(name = "project_id", length = 64, updatable = false, nullable = false)
    private String projectId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Builder.Default
    @Column(name = "storage_bytes", nullable = false)
    private long storageBytes = 0L;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
