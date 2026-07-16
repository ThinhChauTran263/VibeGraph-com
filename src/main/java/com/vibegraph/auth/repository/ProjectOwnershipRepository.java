package com.vibegraph.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vibegraph.auth.domain.ProjectOwnership;

/**
 * Ownership-plane access — authoritative for who owns a project.
 *
 * <p>{@link #findOwnerId(String)} is a lightweight projection used by the ownership guard
 * (no full entity load). {@link #findProjectIdsByOwnerId(UUID)} backs owner-scoped listing.
 */
public interface ProjectOwnershipRepository extends JpaRepository<ProjectOwnership, String> {

    @Query("SELECT p.ownerId FROM ProjectOwnership p WHERE p.projectId = :projectId")
    Optional<UUID> findOwnerId(@Param("projectId") String projectId);

    @Query("SELECT p.projectId FROM ProjectOwnership p WHERE p.ownerId = :ownerId")
    List<String> findProjectIdsByOwnerId(@Param("ownerId") UUID ownerId);

    Page<ProjectOwnership> findByOwnerId(UUID ownerId, Pageable pageable);
}
