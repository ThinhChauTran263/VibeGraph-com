package com.vibegraph.common.ownership;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.common.exception.PartialDeletionException;
import com.vibegraph.graph.service.ProjectService;

/**
 * Unit tests for the fail-safe {@link ProjectDeletionOrchestrator}: data plane first, control
 * plane second, and no false success on partial failure.
 */
@DisplayName("ProjectDeletionOrchestrator")
class ProjectDeletionOrchestratorTest {

    private ProjectService projectService;
    private ProjectOwnershipRepository ownershipRepository;
    private CurrentUser currentUser;
    private ProjectDeletionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        projectService = Mockito.mock(ProjectService.class);
        ownershipRepository = Mockito.mock(ProjectOwnershipRepository.class);
        currentUser = Mockito.mock(CurrentUser.class);
        orchestrator = new ProjectDeletionOrchestrator(projectService, ownershipRepository, currentUser);
    }

    @Test
    @DisplayName("success: deletes data plane first, then the Postgres ownership row")
    void deletesBothPlanesInOrder() {
        orchestrator.delete("p1");

        InOrder inOrder = Mockito.inOrder(projectService, ownershipRepository);
        inOrder.verify(projectService).deleteProject("p1");
        inOrder.verify(ownershipRepository).deleteById("p1");
    }

    @Test
    @DisplayName("data-plane failure propagates and preserves the ownership row")
    void dataPlaneFailurePreservesOwnership() {
        doThrow(new RuntimeException("neo4j down")).when(projectService).deleteProject("p1");

        assertThatThrownBy(() -> orchestrator.delete("p1"))
                .isInstanceOf(RuntimeException.class);

        // Control plane must be untouched so the caller can retry.
        verify(ownershipRepository, never()).deleteById("p1");
    }

    @Test
    @DisplayName("control-plane failure after data-plane delete throws PartialDeletionException")
    void controlPlaneFailureThrowsPartial() {
        when(currentUser.id()).thenReturn(UUID.randomUUID());
        doThrow(new RuntimeException("db down")).when(ownershipRepository).deleteById("p1");

        assertThatThrownBy(() -> orchestrator.delete("p1"))
                .isInstanceOf(PartialDeletionException.class);

        // Data plane was already deleted before the control-plane failure.
        verify(projectService).deleteProject("p1");
    }
}
