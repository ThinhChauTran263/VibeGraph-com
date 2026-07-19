package com.vibegraph.common.ownership;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.ProjectOwnership;
import com.vibegraph.auth.domain.ProjectSourceType;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.common.exception.ForbiddenException;

import lombok.RequiredArgsConstructor;

/**
 * Records project ownership in the control plane ({@code projects.owner_id}) at the point a
 * project is created or imported. Ownership is derived from the authenticated {@link CurrentUser}
 * at the boundary — never inferred by graph/import services — and stored in Postgres only (never
 * on Neo4j nodes).
 *
 * <p>Exposes source-specific methods so data-plane controllers never need to import the
 * {@code auth.domain} enum ({@link ProjectSourceType}); this keeps the module boundary intact
 * (graph/diagram depend on {@code common.ownership} + {@code CurrentUser} only).
 *
 * <p>Rows are created with status {@code ANALYZING}; user-visible status sync (ANALYZED/FAILED)
 * from the control plane is a separate slice.
 */
@Component
@RequiredArgsConstructor
public class ProjectOwnershipRegistrar {

    private final ProjectOwnershipRepository ownershipRepository;
    private final CurrentUser currentUser;

    /** Register a locally-created / local-import project (sourceType LOCAL). */
    public void registerLocal(String projectId, String name) {
        register(projectId, name, ProjectSourceType.LOCAL);
    }

    /** Register an archive-upload project (sourceType ARCHIVE). */
    public void registerArchive(String projectId, String name) {
        register(projectId, name, ProjectSourceType.ARCHIVE);
    }

    /** Register a GitHub-import project (sourceType GITHUB). */
    public void registerGithub(String projectId, String name) {
        register(projectId, name, ProjectSourceType.GITHUB);
    }

    @Transactional
    public void unregister(String projectId) {
        ownershipRepository.deleteById(projectId);
    }

    /**
     * Record ownership of {@code projectId} for the current user. Ownership is <b>never
     * transferred</b>:
     * <ul>
     *   <li>no existing row → create it with the current user as owner;</li>
     *   <li>existing row owned by the current user → idempotent metadata refresh (name/sourceType);</li>
     *   <li>existing row owned by a different user → {@link ForbiddenException} (403), owner_id is
     *       never changed from user A to user B.</li>
     * </ul>
     */
    @Transactional
    public void register(String projectId, String name, ProjectSourceType sourceType) {
        UUID ownerId = currentUser.id();
        ProjectOwnership existing = ownershipRepository.findById(projectId).orElse(null);
        if (existing != null) {
            if (!ownerId.equals(existing.getOwnerId())) {
                // Never reassign ownership across users. Generic message — no owner/project leak.
                throw new ForbiddenException("Access denied");
            }
            existing.setName(name != null ? name : projectId);
            existing.setSourceType(sourceType);
            ownershipRepository.save(existing);
            return;
        }
        ProjectOwnership ownership = ProjectOwnership.builder()
                .projectId(projectId)
                .ownerId(ownerId)
                .name(name != null ? name : projectId)
                .sourceType(sourceType)
                .build();
        ownershipRepository.save(ownership);
    }
}
