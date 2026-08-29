package com.vibegraph.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vibegraph.auth.domain.ProjectOwnership;
import com.vibegraph.auth.domain.ProjectSourceType;

/**
 * Ownership-plane access — authoritative for who owns a project.
 *
 * <p>{@link #findOwnerId(String)} is a lightweight projection used by the ownership guard
 * (no full entity load). {@link #findProjectIdsByOwnerId(UUID)} backs owner-scoped listing.
 */
public interface ProjectOwnershipRepository extends JpaRepository<ProjectOwnership, String> {

    /**
     * Owner of a <b>live</b> project. A trashed project resolves to empty, so the ownership guard
     * rejects it exactly like a project that never existed — the owner should not be able to read
     * or mutate something they have deleted.
     */
    @Query("SELECT p.ownerId FROM ProjectOwnership p WHERE p.projectId = :projectId "
            + "AND p.deletedAt IS NULL")
    Optional<UUID> findOwnerId(@Param("projectId") String projectId);

    /**
     * Live project ids for an owner. This is the single filter that keeps trashed projects out of
     * every owner-scoped listing (REST, MCP tools, API-key binding).
     */
    @Query("SELECT p.projectId FROM ProjectOwnership p WHERE p.ownerId = :ownerId "
            + "AND p.deletedAt IS NULL")
    List<String> findProjectIdsByOwnerId(@Param("ownerId") UUID ownerId);

    Page<ProjectOwnership> findByOwnerIdAndDeletedAtIsNull(UUID ownerId, Pageable pageable);

    Optional<ProjectOwnership> findByProjectIdAndOwnerIdAndDeletedAtIsNull(
            String projectId, UUID ownerId);

    /** Trash listing for the owner, newest deletion first. */
    List<ProjectOwnership> findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(UUID ownerId);

    /** A trashed project the owner may restore or purge. */
    Optional<ProjectOwnership> findByProjectIdAndOwnerIdAndDeletedAtIsNotNull(
            String projectId, UUID ownerId);

    /**
     * One batch of rows whose retention window has expired; the scheduled sweep pages through
     * these (B-M14) instead of pulling the whole trash into a single query/transaction.
     */
    Page<ProjectOwnership> findByDeletedAtLessThan(Instant cutoff, Pageable pageable);

    /**
     * Trashed GitHub imports of the same repository for this owner. Re-importing a GitHub repo
     * purges these first, so a re-import never leaves a duplicate consuming quota. Matching by name
     * is only safe for GitHub, where the name is the globally unique {@code owner/repo}.
     */
    List<ProjectOwnership> findByOwnerIdAndSourceTypeAndNameAndDeletedAtIsNotNull(
            UUID ownerId, ProjectSourceType sourceType, String name);

    /**
     * Live (non-trashed) imports of the same repository for this owner. Re-importing a GitHub
     * repo with a changed HEAD refreshes these in place; an unchanged HEAD is blocked as
     * up-to-date. Matching by name is only safe for GitHub, where the name is the globally
     * unique {@code owner/repo}.
     */
    List<ProjectOwnership> findByOwnerIdAndSourceTypeAndNameAndDeletedAtIsNull(
            UUID ownerId, ProjectSourceType sourceType, String name);
}
