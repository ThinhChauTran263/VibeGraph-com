package com.vibegraph.common.ownership;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;

import lombok.RequiredArgsConstructor;

/**
 * Read-side ownership queries for the control plane. Lets data-plane controllers scope results to
 * the current user without importing {@code auth.repository} directly — the sanctioned bridge is
 * {@code common.ownership} + {@link CurrentUser}.
 *
 * <p>Postgres {@code projects.owner_id} is the single source of truth; this helper never touches
 * Neo4j or the in-memory registry.
 */
@Component
@RequiredArgsConstructor
public class ProjectOwnershipQuery {

    private final ProjectOwnershipRepository ownershipRepository;
    private final CurrentUser currentUser;

    /**
     * @return the project ids owned by the current authenticated user (possibly empty)
     * @throws com.vibegraph.common.exception.UnauthorizedException if there is no authenticated user
     */
    @Transactional(readOnly = true)
    public List<String> ownedProjectIds() {
        UUID ownerId = currentUser.id();
        return ownershipRepository.findProjectIdsByOwnerId(ownerId);
    }
}
