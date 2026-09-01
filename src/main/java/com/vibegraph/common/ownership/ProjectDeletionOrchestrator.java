package com.vibegraph.common.ownership;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.entity.ProjectOwnership;
import com.vibegraph.auth.repository.ApiKeyRepository;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.common.exception.ApiKeyAdminLockedException;
import com.vibegraph.common.exception.PartialDeletionException;
import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;
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
    private final Clock clock;
    private final ArchiveImportProperties importProperties;

    /**
     * Move a project to trash. Caller must have already asserted ownership.
     *
     * <p>Only the control plane changes: the graph and the extracted sources stay untouched so
     * {@link #restore(String)} is a pure flag flip. Marking the row is enough to hide the project
     * everywhere, because every owner-scoped query filters on {@code deletedAt}.
     *
     * <p>The API-key guard from the old immediate delete is kept, and it matters more here: trashing
     * schedules an irreversible purge, so allowing it while an administrator has locked a key would
     * let the retention sweep destroy a project that is under investigation.
     *
     * <p>API keys themselves need no state change. They stay bound to the project, and because
     * ownership lookups now skip trashed projects, any call made with such a key fails while the
     * project sits in trash and works again after a restore.
     */
    @Transactional
    public void moveToTrash(String projectId) {
        assertNoAdminLockedApiKey(projectId);

        ProjectOwnership ownership = ownershipRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found: " + projectId));
        if (ownership.isTrashed()) {
            return;
        }
        ownership.setDeletedAt(Instant.now(clock));
        ownershipRepository.save(ownership);
        log.info("Project moved to trash. projectId={}, userId={}", projectId, currentUserId());
    }

    /** Bring a trashed project back. The data plane was never touched, so nothing to rebuild. */
    @Transactional
    public void restore(String projectId) {
        ProjectOwnership ownership = ownershipRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found: " + projectId));
        if (!ownership.isTrashed()) {
            return;
        }
        ownership.setDeletedAt(null);
        ownershipRepository.save(ownership);
        log.info("Project restored from trash. projectId={}, userId={}", projectId, currentUserId());
    }

    /**
     * Irreversibly delete a project across data plane then control plane. Returns normally only
     * when BOTH planes are removed.
     *
     * @throws PartialDeletionException if the control-plane row cannot be removed after the data
     *                                  plane was deleted (→ 500 DELETE_PARTIAL_FAILED)
     */
    @Transactional
    public void purge(String projectId) {
        assertNoAdminLockedApiKey(projectId);

        // Resolve the extracted sources BEFORE the data plane goes away — deleteProject drops the
        // in-memory entry that holds the path.
        Path extractedSources = managedWorkspaceOf(projectId);

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

        // 3) Disk last, best-effort. Both planes are already gone, so a failure here leaves an
        //    orphan directory to sweep later rather than a project the caller thinks still exists.
        deleteQuietly(extractedSources, projectId);
    }

    /**
     * The directory this project's extracted sources live in, or {@code null} when there is nothing
     * we may delete.
     *
     * <p>This is the guard that makes deleting on purge safe at all. A LOCAL import points its root
     * path at a directory the <b>user</b> owns — deleting that would destroy their source code. Only
     * paths inside the configured import workspace were created by us, so only those are removed.
     * Anything outside, unreadable, or not resolvable is left alone.
     */
    private Path managedWorkspaceOf(String projectId) {
        String rootPath;
        try {
            rootPath = projectService.getProject(projectId).getRootPath();
        } catch (RuntimeException ex) {
            return null;
        }
        if (rootPath == null || rootPath.isBlank()) {
            return null;
        }
        try {
            Path workspaceRoot = importProperties.getWorkspaceRoot().toAbsolutePath().normalize();
            Path candidate = Path.of(rootPath).toAbsolutePath().normalize();
            if (!candidate.startsWith(workspaceRoot) || candidate.equals(workspaceRoot)) {
                return null;
            }
            // Delete the whole per-import directory (…/uploads/github-<uuid>), not just the "source"
            // subdirectory the project points at, so no tarball or scratch file is left behind.
            //
            // The relative path always has at least one element here: startsWith is element-wise,
            // so a candidate under the root with no elements left would have to equal the root, and
            // the check above already rejected that. The equality check is the real guard.
            Path relative = workspaceRoot.relativize(candidate);
            return workspaceRoot.resolve(relative.getName(0));
        } catch (RuntimeException ex) {
            log.warn("Could not resolve the extracted sources of project {}: {}",
                    projectId, ex.getMessage());
            return null;
        }
    }

    private void deleteQuietly(Path directory, String projectId) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> entries = Files.walk(directory)) {
            entries.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Reported once below via the surviving directory.
                }
            });
        } catch (IOException ex) {
            log.warn("Could not delete extracted sources for project {} at {}: {}",
                    projectId, directory, ex.getMessage());
            return;
        }
        if (Files.exists(directory)) {
            log.warn("Extracted sources for project {} survived deletion at {}", projectId, directory);
        } else {
            log.info("Deleted extracted sources for project {}", projectId);
        }
    }

    private void assertNoAdminLockedApiKey(String projectId) {
        apiKeyRepository.lockLiveKeysForProject(projectId);
        if (apiKeyRepository.existsAdminLockedKeyForProject(projectId)) {
            throw new ApiKeyAdminLockedException(
                    "Administrator-locked API key must be unlocked before deleting this project");
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
