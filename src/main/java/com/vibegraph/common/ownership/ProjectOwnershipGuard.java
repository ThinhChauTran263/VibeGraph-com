package com.vibegraph.common.ownership;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.common.exception.ForbiddenException;
import com.vibegraph.common.exception.ProjectNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * IDOR guard: asserts that a project is owned by the acting user before a project-scoped
 * operation proceeds. Ownership is read from the control plane ({@code projects.owner_id}) via a
 * lightweight id projection — no full entity load, no Neo4j access.
 *
 * <p>Lives in {@code common.ownership} (not {@code auth}) so data-plane controllers can depend on
 * it without importing auth internals — the sanctioned cross-module bridge alongside
 * {@link CurrentUser}.
 *
 * <p>This slice provides the guard only; wiring it into project/graph/source/diagram controllers
 * is a later slice.
 */
@Component
@RequiredArgsConstructor
public class ProjectOwnershipGuard {

    private final ProjectOwnershipRepository ownershipRepository;
    private final CurrentUser currentUser;

    /**
     * Assert that {@code currentUserId} owns {@code projectId}.
     *
     * @throws ProjectNotFoundException if no ownership row exists for the project → 404
     * @throws ForbiddenException       if the project is owned by a different user → 403
     */
    public void assertOwner(String projectId, UUID currentUserId) {
        UUID ownerId = ownershipRepository.findOwnerId(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found: " + projectId));
        if (!ownerId.equals(currentUserId)) {
            // Generic message — never leak owner/project details.
            throw new ForbiddenException("Access denied");
        }
    }

    /**
     * Convenience overload resolving the acting user from the security context.
     *
     * @throws com.vibegraph.common.exception.UnauthorizedException if there is no authenticated user
     * @throws ProjectNotFoundException                             if the project has no owner row → 404
     * @throws ForbiddenException                                   if owned by a different user → 403
     */
    public void assertOwner(String projectId) {
        assertOwner(projectId, currentUser.id());
    }
}
