package com.vibegraph.common.ownership;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.entity.ProjectOwnership;
import com.vibegraph.auth.repository.ApiKeyRepository;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.common.exception.ApiKeyAdminLockedException;
import com.vibegraph.common.exception.PartialDeletionException;
import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;
import com.vibegraph.graph.service.ProjectService;

/**
 * Unit tests for {@link ProjectDeletionOrchestrator}: the reversible trash transitions, and the
 * fail-safe purge — data plane first, control plane second, no false success on partial failure.
 */
@DisplayName("ProjectDeletionOrchestrator")
class ProjectDeletionOrchestratorTest {

    private static final Instant NOW = Instant.parse("2026-08-10T09:15:00Z");

    private ProjectService projectService;
    private ProjectOwnershipRepository ownershipRepository;
    private ApiKeyRepository apiKeyRepository;
    private CurrentUser currentUser;
    private ProjectDeletionOrchestrator orchestrator;

    @TempDir
    Path tempDir;

    private ArchiveImportProperties importProperties;
    private Path workspaceRoot;

    @BeforeEach
    void setUp() {
        projectService = Mockito.mock(ProjectService.class);
        ownershipRepository = Mockito.mock(ProjectOwnershipRepository.class);
        apiKeyRepository = Mockito.mock(ApiKeyRepository.class);
        currentUser = Mockito.mock(CurrentUser.class);
        workspaceRoot = tempDir.resolve("uploads");
        importProperties = new ArchiveImportProperties();
        importProperties.setWorkspaceRoot(workspaceRoot);
        orchestrator = new ProjectDeletionOrchestrator(
                projectService, ownershipRepository, apiKeyRepository, currentUser,
                Clock.fixed(NOW, ZoneOffset.UTC), importProperties);
    }

    /** Register a project whose extracted sources live at {@code rootPath}. */
    private void projectAt(String projectId, Path rootPath) {
        Mockito.lenient().when(projectService.getProject(projectId)).thenReturn(
                ProjectResponse.builder().id(projectId).name(projectId)
                        .rootPath(rootPath.toString()).build());
    }

    private Path createTree(Path root) throws IOException {
        Files.createDirectories(root.resolve("source/src"));
        Files.writeString(root.resolve("source/src/App.java"), "class App {}");
        return root;
    }

    private ProjectOwnership ownership(String projectId, Instant deletedAt) {
        ProjectOwnership ownership = new ProjectOwnership();
        ownership.setProjectId(projectId);
        ownership.setDeletedAt(deletedAt);
        when(ownershipRepository.findById(projectId)).thenReturn(Optional.of(ownership));
        return ownership;
    }

    @Test
    @DisplayName("moveToTrash stamps deletedAt and leaves both data planes untouched")
    void moveToTrashOnlyMarksTheControlPlane() {
        ownership("p1", null);

        orchestrator.moveToTrash("p1");

        ArgumentCaptor<ProjectOwnership> saved = ArgumentCaptor.forClass(ProjectOwnership.class);
        verify(ownershipRepository).save(saved.capture());
        assertThat(saved.getValue().getDeletedAt()).isEqualTo(NOW);
        // The graph and the extracted sources survive, which is what makes a restore free.
        verify(projectService, never()).deleteProject("p1");
        verify(ownershipRepository, never()).deleteById("p1");
    }

    @Test
    @DisplayName("moveToTrash is idempotent: a second delete does not reset the retention clock")
    void moveToTrashKeepsTheOriginalDeletedAt() {
        Instant trashedEarlier = NOW.minusSeconds(3600);
        ProjectOwnership ownership = ownership("p1", trashedEarlier);

        orchestrator.moveToTrash("p1");

        verify(ownershipRepository, never()).save(Mockito.any());
        assertThat(ownership.getDeletedAt()).isEqualTo(trashedEarlier);
    }

    @Test
    @DisplayName("moveToTrash is refused while an administrator-locked key exists")
    void moveToTrashRespectsAdminLock() {
        when(apiKeyRepository.existsAdminLockedKeyForProject("p1")).thenReturn(true);

        assertThatThrownBy(() -> orchestrator.moveToTrash("p1"))
                .isInstanceOf(ApiKeyAdminLockedException.class);

        verify(ownershipRepository, never()).save(Mockito.any());
    }

    @Test
    @DisplayName("moveToTrash on an unknown project reports it as not found")
    void moveToTrashRejectsUnknownProject() {
        when(ownershipRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orchestrator.moveToTrash("ghost"))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    @DisplayName("restore clears deletedAt so the project reappears in every owner-scoped query")
    void restoreClearsDeletedAt() {
        ProjectOwnership ownership = ownership("p1", NOW.minusSeconds(60));

        orchestrator.restore("p1");

        verify(ownershipRepository).save(ownership);
        assertThat(ownership.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("restore of a live project is a no-op")
    void restoreOfLiveProjectDoesNothing() {
        ownership("p1", null);

        orchestrator.restore("p1");

        verify(ownershipRepository, never()).save(Mockito.any());
    }

    @Test
    @DisplayName("purge deletes the data plane first, then the Postgres ownership row")
    void purgeDeletesBothPlanesInOrder() {
        orchestrator.purge("p1");

        InOrder inOrder = Mockito.inOrder(projectService, ownershipRepository);
        inOrder.verify(projectService).deleteProject("p1");
        inOrder.verify(ownershipRepository).deleteById("p1");
    }

    @Test
    @DisplayName("administrator-locked key blocks the purge before any project plane is changed")
    void adminLockedKeyPreservesBothPlanes() {
        when(apiKeyRepository.lockLiveKeysForProject("p1"))
                .thenReturn(java.util.List.of(UUID.randomUUID()));
        when(apiKeyRepository.existsAdminLockedKeyForProject("p1")).thenReturn(true);

        assertThatThrownBy(() -> orchestrator.purge("p1"))
                .isInstanceOf(ApiKeyAdminLockedException.class);

        verify(projectService, never()).deleteProject("p1");
        verify(ownershipRepository, never()).deleteById("p1");
    }

    @Test
    @DisplayName("data-plane failure propagates and preserves the ownership row")
    void dataPlaneFailurePreservesOwnership() {
        doThrow(new RuntimeException("neo4j down")).when(projectService).deleteProject("p1");

        assertThatThrownBy(() -> orchestrator.purge("p1"))
                .isInstanceOf(RuntimeException.class);

        // Control plane must be untouched so the caller can retry.
        verify(ownershipRepository, never()).deleteById("p1");
    }

    @Test
    @DisplayName("purge deletes the whole import directory, not just the source subfolder")
    void purgeRemovesExtractedSources() throws IOException {
        Path importDir = createTree(workspaceRoot.resolve("github-abc"));
        Files.writeString(importDir.resolve("repo.tar.gz"), "leftover");
        projectAt("p1", importDir.resolve("source"));

        orchestrator.purge("p1");

        // The per-import directory goes, so no scratch file or tarball survives either.
        assertThat(importDir).doesNotExist();
    }

    @Test
    @DisplayName("purge never touches a local project's own source directory")
    void purgeLeavesUserOwnedDirectoriesAlone() throws IOException {
        // A LOCAL import points at a folder the user owns. Deleting it would destroy their code, so
        // only paths inside the managed workspace may ever be removed.
        Path userWorkspace = createTree(tempDir.resolve("my-own-code"));
        projectAt("p1", userWorkspace.resolve("source"));

        orchestrator.purge("p1");

        assertThat(userWorkspace.resolve("source/src/App.java")).exists();
    }

    @Test
    @DisplayName("purge refuses to delete the workspace root itself")
    void purgeNeverDeletesTheWorkspaceRoot() throws IOException {
        Files.createDirectories(workspaceRoot);
        projectAt("p1", workspaceRoot);

        orchestrator.purge("p1");

        assertThat(workspaceRoot).exists();
    }

    @Test
    @DisplayName("a project whose sources are already gone still purges cleanly")
    void purgeToleratesMissingSources() {
        projectAt("p1", workspaceRoot.resolve("github-missing/source"));

        orchestrator.purge("p1");

        verify(ownershipRepository).deleteById("p1");
    }

    @Test
    @DisplayName("an unreadable project path does not block the purge")
    void purgeToleratesUnknownProjectPath() {
        when(projectService.getProject("p1")).thenThrow(new ProjectNotFoundException("gone"));

        orchestrator.purge("p1");

        verify(projectService).deleteProject("p1");
        verify(ownershipRepository).deleteById("p1");
    }

    @Test
    @DisplayName("moveToTrash keeps the extracted sources so a restore is free")
    void moveToTrashKeepsExtractedSources() throws IOException {
        Path importDir = createTree(workspaceRoot.resolve("github-keep"));
        projectAt("p1", importDir.resolve("source"));
        ownership("p1", null);

        orchestrator.moveToTrash("p1");

        assertThat(importDir.resolve("source/src/App.java")).exists();
    }

    @Test
    @DisplayName("control-plane failure after data-plane delete throws PartialDeletionException")
    void controlPlaneFailureThrowsPartial() {
        when(currentUser.id()).thenReturn(UUID.randomUUID());
        doThrow(new RuntimeException("db down")).when(ownershipRepository).deleteById("p1");

        assertThatThrownBy(() -> orchestrator.purge("p1"))
                .isInstanceOf(PartialDeletionException.class);

        // Data plane was already deleted before the control-plane failure.
        verify(projectService).deleteProject("p1");
    }
}
