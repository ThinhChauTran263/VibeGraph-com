package com.vibegraph.common.ownership;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.ProjectOwnership;
import com.vibegraph.auth.domain.ProjectSourceType;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.config.ProjectsProperties;
import com.vibegraph.graph.dto.TrashedProjectResponse;

import lombok.RequiredArgsConstructor;

/**
 * Owner-facing trash operations plus the retention sweep that makes deletion final.
 *
 * <p>Trash is a control-plane state: {@code projects.deleted_at} is set, and every owner-scoped
 * query filters on it, so the project disappears from the REST API, the UI and the MCP tools while
 * its graph and extracted sources stay untouched. That is what makes a restore free.
 *
 * <p>The trade-off is deliberate: a trashed project still occupies storage, so it keeps counting
 * toward the owner's quota until it is purged. Owners who need the space back immediately can purge
 * it themselves instead of waiting for the sweep.
 */
@Service
@RequiredArgsConstructor
public class ProjectTrashService {

    private static final Logger log = LoggerFactory.getLogger(ProjectTrashService.class);

    private final ProjectOwnershipRepository ownershipRepository;
    private final ProjectDeletionOrchestrator deletionOrchestrator;
    private final ProjectsProperties projectsProperties;
    private final CurrentUser currentUser;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TrashedProjectResponse> listTrash() {
        Instant now = Instant.now(clock);
        return ownershipRepository
                .findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(currentUser.id())
                .stream()
                .map(ownership -> TrashedProjectResponse.from(ownership, purgeAt(ownership), now))
                .toList();
    }

    /**
     * Restore a trashed project the caller owns.
     *
     * <p>Ownership is resolved against the trashed rows directly: the usual guard deliberately
     * treats a trashed project as non-existent, so it cannot be reused here.
     */
    @Transactional
    public void restore(String projectId) {
        requireOwnedTrashedProject(projectId);
        deletionOrchestrator.restore(projectId);
    }

    /** Permanently delete a trashed project the caller owns, without waiting for the sweep. */
    @Transactional
    public void purge(String projectId) {
        requireOwnedTrashedProject(projectId);
        deletionOrchestrator.purge(projectId);
    }

    /**
     * Permanently deletes trashed GitHub imports of the same repository before a re-import.
     *
     * <p>Re-importing a repo that is still in trash would otherwise leave a hidden duplicate
     * consuming quota for the rest of the retention window. Matching is by name and restricted to
     * GitHub on purpose: there the name is the globally unique {@code owner/repo}, whereas archive
     * and local imports carry user-supplied names where two unrelated projects can easily collide.
     *
     * @return the ids that were purged, so the caller can tell the user what happened
     */
    @Transactional
    public List<String> purgeTrashedGitHubDuplicates(UUID ownerId, String repositoryName) {
        if (repositoryName == null || repositoryName.isBlank()) {
            return List.of();
        }
        List<ProjectOwnership> duplicates = ownershipRepository
                .findByOwnerIdAndSourceTypeAndNameAndDeletedAtIsNotNull(
                        ownerId, ProjectSourceType.GITHUB, repositoryName);
        return duplicates.stream()
                .map(ProjectOwnership::getProjectId)
                .filter(this::purgeQuietly)
                .toList();
    }

    /**
     * Deletes projects whose retention window has expired.
     *
     * <p>Runs per project so one failure — most often an administrator lock placed after the owner
     * trashed the project — cannot stop the rest of the sweep. A project that keeps failing simply
     * stays in trash and is retried on the next run rather than being silently lost.
     */
    @Scheduled(cron = "${vibegraph.projects.trash-sweep-cron:0 30 3 * * ?}")
    @Transactional
    public void purgeExpiredProjects() {
        Instant cutoff = Instant.now(clock)
                .minus(projectsProperties.getTrashRetentionDays(), ChronoUnit.DAYS);
        List<ProjectOwnership> expired = ownershipRepository.findByDeletedAtLessThan(cutoff);
        if (expired.isEmpty()) {
            return;
        }
        long purged = expired.stream()
                .map(ProjectOwnership::getProjectId)
                .filter(this::purgeQuietly)
                .count();
        log.info("Trash retention sweep purged {} of {} expired projects (retention {} days)",
                purged, expired.size(), projectsProperties.getTrashRetentionDays());
    }

    private boolean purgeQuietly(String projectId) {
        try {
            deletionOrchestrator.purge(projectId);
            return true;
        } catch (RuntimeException ex) {
            log.warn("Could not purge trashed project {}; it stays in trash for the next run: {}",
                    projectId, ex.getMessage());
            return false;
        }
    }

    private ProjectOwnership requireOwnedTrashedProject(String projectId) {
        return ownershipRepository
                .findByProjectIdAndOwnerIdAndDeletedAtIsNotNull(projectId, currentUser.id())
                .orElseThrow(() -> new ProjectNotFoundException(
                        "Trashed project not found: " + projectId));
    }

    private Instant purgeAt(ProjectOwnership ownership) {
        return ownership.getDeletedAt()
                .plus(projectsProperties.getTrashRetentionDays(), ChronoUnit.DAYS);
    }
}
