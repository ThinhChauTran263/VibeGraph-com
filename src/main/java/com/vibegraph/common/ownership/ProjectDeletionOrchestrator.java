package com.vibegraph.common.ownership;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.repository.ApiKeyRepository;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.common.exception.ApiKeyAdminLockedException;
import com.vibegraph.common.exception.PartialDeletionException;
import com.vibegraph.graph.service.ProjectService;

import lombok.RequiredArgsConstructor;

/**
 * Fail-safe, controller-only orchestration of a public project delete across the two planes.
 *
 * <p>Order and semantics (ownership is asserted by the caller BEFORE this runs):
 * <ol>
 *   <li>Delete the data plane (Neo4j graph + in-memory registry + watcher) via
 *       {@link ProjectService#deleteProject(String)}.</li>
 *   <li>Only if that succeeds, delete the Postgres ownership row.</li>
 * </ol>
 *
 * <p>Failure handling:
 * <ul>
 *   <li>Data-plane delete fails → the exception propagates and the Postgres ownership row is
 *       <b>preserved</b> (nothing was removed from the control plane).</li>
 *   <li>Control-plane delete fails after the data plane was removed → throw
 *       {@link PartialDeletionException} ({@code DELETE_PARTIAL_FAILED}); the surviving state is
 *       logged as cleanup-needed. The public {@code DELETE} never reports success here.</li>
 * </ul>
 *
 * <p>This orchestrator is used ONLY by the public {@code DELETE} endpoint. Import rollback keeps
 * calling {@link ProjectService#deleteProject(String)} directly in best-effort mode, so import
 * cleanup never inherits these fail-safe throwing semantics.
 */
@Component
@RequiredArgsConstructor
public class ProjectDeletionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ProjectDeletionOrchestrator.class);

    private final ProjectService projectService;
    private final ProjectOwnershipRepository ownershipRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final CurrentUser currentUser;

    /**
     * Delete a project across data plane then control plane. Caller must have already asserted
     * ownership. Returns normally only when BOTH planes are removed.
     *
     * @throws PartialDeletionException if the control-plane row cannot be removed after the data
     *                                  plane was deleted (→ 500 DELETE_PARTIAL_FAILED)
     */
    @Transactional
    public void delete(String projectId) {
        apiKeyRepository.lockLiveKeysForProject(projectId);
        if (apiKeyRepository.existsAdminLockedKeyForProject(projectId)) {
            throw new ApiKeyAdminLockedException(
                    "Administrator-locked API key must be unlocked before deleting this project");
        }

        // 1) Data plane first. On failure, propagate; the Postgres ownership row is untouched.
        projectService.deleteProject(projectId);

        // 2) Control plane. If this fails, the data plane is already gone → partial, not success.
        try {
            ownershipRepository.deleteById(projectId);
        } catch (RuntimeException ex) {
            log.error("Project delete PARTIAL — data plane removed but control-plane (Postgres) delete "
                            + "failed; cleanup needed. projectId={}, userId={}, failedPlane=CONTROL_PLANE",
                    projectId, currentUserId(), ex);
            throw new PartialDeletionException(projectId, "CONTROL_PLANE", ex);
        }
    }

    /** Best-effort current user id for logging; never throws, never logs secrets. */
    private String currentUserId() {
        try {
            return String.valueOf(currentUser.id());
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }
}
