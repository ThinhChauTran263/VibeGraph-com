package com.vibegraph.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ownership record (table {@code projects}) — the single source of truth for who owns a
 * project. {@code projectId} equals the Neo4j {@code :Project.id}; it is assigned by the
 * import/create flow, not generated here. Ownership is never stored on Neo4j nodes.
 *
 * <p>{@code createdAt}/{@code updatedAt} are DB-managed (defaults + {@code trg_projects_updated}).
 */
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectOwnership {

    @Id
    @Column(name = "project_id", length = 64, updatable = false, nullable = false)
    private String projectId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private ProjectSourceType sourceType;

    @Builder.Default
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes = 0L;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectOwnershipStatus status = ProjectOwnershipStatus.ANALYZING;

    /**
     * Commit SHA of the source at import time (GitHub imports only), or {@code null} for
     * other sources / imports predating the column. Re-imports compare the repository HEAD
     * against this value: equal means "up to date" (blocked), different means refresh the
     * existing project in place instead of creating a duplicate.
     */
    @Column(name = "source_ref", length = 64)
    private String sourceRef;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    /**
     * When the owner moved this project to trash, or {@code null} while it is live.
     *
     * <p>Owner-scoped queries filter on this, so a trashed project vanishes from listings while its
     * graph and extracted sources stay intact for the retention window. It keeps counting toward
     * the owner's quota until the scheduled purge removes it for real.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isTrashed() {
        return deletedAt != null;
    }
}
