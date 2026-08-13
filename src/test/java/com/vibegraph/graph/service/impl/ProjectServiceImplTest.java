package com.vibegraph.graph.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

import com.vibegraph.auth.domain.ProjectOwnership;
import com.vibegraph.auth.domain.ProjectOwnershipStatus;
import com.vibegraph.auth.domain.ProjectSourceType;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.request.CreateProjectRequest;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.watcher.service.FileWatcherService;

@DisplayName("ProjectServiceImpl")
class ProjectServiceImplTest {

    @TempDir
    Path tempDir;

    private final ProjectServiceImpl service = new ProjectServiceImpl();

    @BeforeEach
    void setUp() {
        // Confine import to the temp dir so these tests stay deterministic (and exercise the
        // confined path); every project they create lives under tempDir.
        ReflectionTestUtils.setField(service, "allowedRoot", tempDir.toString());
    }

    @Test
    @DisplayName("createEmptyWorkspaceProject persists recoverable project metadata")
    void shouldPersistEmptyWorkspaceProjectMetadata() throws IOException {
        Path workspaceRoot = Files.createDirectory(tempDir.resolve("uploads"));
        Path source = Files.createDirectories(workspaceRoot.resolve("cli/abcd1234/source"));
        ArchiveImportProperties props = new ArchiveImportProperties();
        props.setWorkspaceRoot(workspaceRoot);
        GraphRepository graphRepository = mock(GraphRepository.class);
        ReflectionTestUtils.setField(service, "archiveImportProperties", props);
        ReflectionTestUtils.setField(service, "graphRepository", graphRepository);

        ProjectResponse project = service.createEmptyWorkspaceProject("cli repo", source);

        assertThat(project.getName()).isEqualTo("cli repo");
        assertThat(project.getRootPath()).isEqualTo(source.toRealPath().toString());
        verify(graphRepository).upsertProject(project.getId(), "cli repo", source.toRealPath().toString());
    }

    @Test
    @DisplayName("createProject stores the canonical existing directory path")
    void shouldStoreCanonicalRootPath() throws IOException {
        Path projectRoot = Files.createDirectory(tempDir.resolve("project"));
        Path nestedReference = projectRoot.resolve(".");

        ProjectResponse project = service.createProject(CreateProjectRequest.builder()
                .name("demo")
                .rootPath(nestedReference.toString())
                .build());

        assertThat(project.getName()).isEqualTo("demo");
        assertThat(project.getRootPath()).isEqualTo(projectRoot.toRealPath().toString());
        assertThat(project.getStatus()).isEqualTo("CREATED");
        assertThat(project.getProgress()).isZero();
    }

    @Test
    @DisplayName("createProject rejects a missing rootPath")
    void shouldRejectMissingRootPath() {
        Path missing = tempDir.resolve("missing");

        assertThatThrownBy(() -> service.createProject(CreateProjectRequest.builder()
                .name("demo")
                .rootPath(missing.toString())
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rootPath must be an existing directory");
    }

    @Test
    @DisplayName("createProject rejects a file rootPath")
    void shouldRejectFileRootPath() throws IOException {
        Path file = Files.createFile(tempDir.resolve("Demo.java"));

        assertThatThrownBy(() -> service.createProject(CreateProjectRequest.builder()
                .name("demo")
                .rootPath(file.toString())
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rootPath must be an existing directory");
    }

    @Test
    @DisplayName("createProject rejects paths outside the configured allowed root")
    void shouldRejectPathOutsideAllowedRoot() throws IOException {
        Path allowedRoot = Files.createDirectory(tempDir.resolve("allowed"));
        Path outsideRoot = Files.createDirectory(tempDir.resolve("outside"));
        ReflectionTestUtils.setField(service, "allowedRoot", allowedRoot.toString());

        assertThatThrownBy(() -> service.createProject(CreateProjectRequest.builder()
                .name("demo")
                .rootPath(outsideRoot.toString())
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rootPath must be inside the configured allowed root");
    }

    @Test
    @DisplayName("createProjectFromWorkspace accepts a server workspace under the configured workspace root")
    void shouldCreateProjectFromWorkspaceUnderRoot() throws IOException {
        Path workspaceRoot = Files.createDirectory(tempDir.resolve("uploads"));
        Path source = Files.createDirectories(workspaceRoot.resolve("abcd1234/source"));
        ArchiveImportProperties props = new ArchiveImportProperties();
        props.setWorkspaceRoot(workspaceRoot);
        ReflectionTestUtils.setField(service, "archiveImportProperties", props);

        ProjectResponse project = service.createProjectFromWorkspace("uploaded", source);

        assertThat(project.getName()).isEqualTo("uploaded");
        assertThat(project.getRootPath()).isEqualTo(source.toRealPath().toString());
        assertThat(project.getStatus()).isEqualTo("CREATED");
        assertThat(service.getProject(project.getId())).isNotNull();
    }

    @Test
    @DisplayName("createProjectFromWorkspace rejects a workspace outside the configured workspace root")
    void shouldRejectWorkspaceOutsideRoot() throws IOException {
        Path workspaceRoot = Files.createDirectory(tempDir.resolve("uploads"));
        Path outside = Files.createDirectory(tempDir.resolve("elsewhere"));
        ArchiveImportProperties props = new ArchiveImportProperties();
        props.setWorkspaceRoot(workspaceRoot);
        ReflectionTestUtils.setField(service, "archiveImportProperties", props);

        assertThatThrownBy(() -> service.createProjectFromWorkspace("x", outside))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspace must be inside the configured archive workspace root");
    }

    @Test
    @DisplayName("markAnalyzing transitions the project to ANALYZING")
    void shouldMarkAnalyzing() throws IOException {
        String id = createProject("a");

        service.markAnalyzing(id);

        ProjectResponse p = service.getProject(id);
        assertThat(p.getStatus()).isEqualTo("ANALYZING");
        assertThat(p.getProgress()).isZero();
    }

    @Test
    @DisplayName("updateProjectStats delegates to markAnalyzed: ANALYZED + stats + lastAnalyzedAt + progress 100")
    void shouldMarkAnalyzedViaUpdateProjectStats() throws IOException {
        String id = createProject("b");

        service.updateProjectStats(id, 3, 10, 7);

        ProjectResponse p = service.getProject(id);
        assertThat(p.getStatus()).isEqualTo("ANALYZED");
        assertThat(p.getTotalFiles()).isEqualTo(3);
        assertThat(p.getTotalNodes()).isEqualTo(10);
        assertThat(p.getTotalEdges()).isEqualTo(7);
        assertThat(p.getLastAnalyzedAt()).isNotNull();
        assertThat(p.getProgress()).isEqualTo(100);
    }

    @Test
    @DisplayName("markFailed transitions to FAILED, preserving name/rootPath/createdAt")
    void shouldMarkFailed() throws IOException {
        String id = createProject("c");
        ProjectResponse before = service.getProject(id);
        service.markAnalyzing(id);

        service.markFailed(id, "neo4j down");

        ProjectResponse p = service.getProject(id);
        assertThat(p.getStatus()).isEqualTo("FAILED");
        assertThat(p.getName()).isEqualTo("c");
        assertThat(p.getRootPath()).isEqualTo(before.getRootPath());
        assertThat(p.getCreatedAt()).isEqualTo(before.getCreatedAt());
    }

    @Test
    @DisplayName("status transitions on an unknown project are no-ops (consistent with updateProjectStats)")
    void shouldIgnoreTransitionsOnUnknownProject() {
        service.markAnalyzing("nope");
        service.markAnalyzed("nope", 1, 1, 1);
        service.markFailed("nope", "x");

        assertThatThrownBy(() -> service.getProject("nope"))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    @DisplayName("deleteProject stops the file watcher for the project")
    void shouldStopWatcherOnDelete() throws IOException {
        FileWatcherService fileWatcherService = mock(FileWatcherService.class);
        ReflectionTestUtils.setField(service, "fileWatcherService", fileWatcherService);
        String id = createProject("watched");

        service.deleteProject(id);

        verify(fileWatcherService).stopWatching(id);
        assertThatThrownBy(() -> service.getProject(id)).isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    @DisplayName("deleteProject of an unknown project throws and never stops a watcher")
    void shouldNotStopWatcherWhenProjectUnknown() {
        FileWatcherService fileWatcherService = mock(FileWatcherService.class);
        ReflectionTestUtils.setField(service, "fileWatcherService", fileWatcherService);

        assertThatThrownBy(() -> service.deleteProject("missing"))
                .isInstanceOf(ProjectNotFoundException.class);

        verifyNoInteractions(fileWatcherService);
    }

    @Test
    @DisplayName("deleteProject works when no file watcher is wired (optional dependency)")
    void shouldDeleteWhenWatcherNotWired() throws IOException {
        String id = createProject("no-watcher");

        service.deleteProject(id);

        assertThatThrownBy(() -> service.getProject(id)).isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    @DisplayName("H7: project ids are full UUIDs and never collide across consecutive creates")
    void shouldAllocateFullUuidProjectIds() throws IOException {
        String id1 = createProject("p1");
        String id2 = createProject("p2");

        assertThat(id1).hasSize(36); // full UUID with hyphens, not the old 8-char prefix
        assertThat(id2).hasSize(36);
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("H6: getProject reads name/status from the Postgres ownership row, keeps transient state")
    void shouldReadNameAndStatusFromOwnershipRow() throws IOException {
        ProjectOwnershipRepository repo = mock(ProjectOwnershipRepository.class);
        ReflectionTestUtils.setField(service, "ownershipRepository", repo);
        String id = createProject("original");
        ProjectResponse cached = service.getProject(id);
        ProjectOwnership row = ownershipRow(id, "renamed-in-db", ProjectOwnershipStatus.FAILED);
        when(repo.findById(id)).thenReturn(Optional.of(row));

        ProjectResponse p = service.getProject(id);

        assertThat(p.getName()).isEqualTo("renamed-in-db");
        assertThat(p.getStatus()).isEqualTo("FAILED");
        assertThat(p.getRootPath()).isEqualTo(cached.getRootPath()); // transient state preserved
    }

    @Test
    @DisplayName("H6: markAnalyzed persists ANALYZED to the ownership row")
    void shouldPersistAnalyzedStatusToOwnershipRow() throws IOException {
        ProjectOwnershipRepository repo = mock(ProjectOwnershipRepository.class);
        ReflectionTestUtils.setField(service, "ownershipRepository", repo);
        String id = createProject("synced");
        ProjectOwnership row = ownershipRow(id, "synced", ProjectOwnershipStatus.ANALYZING);
        when(repo.findById(id)).thenReturn(Optional.of(row));

        service.markAnalyzed(id, 2, 5, 3);

        verify(repo).save(row);
        assertThat(row.getStatus()).isEqualTo(ProjectOwnershipStatus.ANALYZED);
    }

    @Test
    @DisplayName("H6: markFailed persists FAILED to the ownership row")
    void shouldPersistFailedStatusToOwnershipRow() throws IOException {
        ProjectOwnershipRepository repo = mock(ProjectOwnershipRepository.class);
        ReflectionTestUtils.setField(service, "ownershipRepository", repo);
        String id = createProject("failing");
        ProjectOwnership row = ownershipRow(id, "failing", ProjectOwnershipStatus.ANALYZING);
        when(repo.findById(id)).thenReturn(Optional.of(row));

        service.markFailed(id, "boom");

        verify(repo).save(row);
        assertThat(row.getStatus()).isEqualTo(ProjectOwnershipStatus.FAILED);
    }

    @Test
    @DisplayName("H6: listProjects overlays ownership name/status on the merged listing")
    void shouldOverlayOwnershipTruthOnListing() throws IOException {
        ProjectOwnershipRepository repo = mock(ProjectOwnershipRepository.class);
        ReflectionTestUtils.setField(service, "ownershipRepository", repo);
        String id = createProject("listed");
        ProjectOwnership row = ownershipRow(id, "listed-renamed", ProjectOwnershipStatus.ANALYZED);
        when(repo.findAllById(any())).thenReturn(List.of(row));

        List<ProjectResponse> listed = service.listProjects();

        assertThat(listed).hasSize(1);
        assertThat(listed.getFirst().getName()).isEqualTo("listed-renamed");
        assertThat(listed.getFirst().getStatus()).isEqualTo("ANALYZED");
        assertThat(listed.getFirst().getProgress()).isEqualTo(100);
    }

    @Test
    @DisplayName("H6: a trashed ownership row falls back to the legacy resolution order")
    void shouldFallBackWhenOwnershipRowTrashed() throws IOException {
        ProjectOwnershipRepository repo = mock(ProjectOwnershipRepository.class);
        ReflectionTestUtils.setField(service, "ownershipRepository", repo);
        String id = createProject("trashed");
        ProjectOwnership row = ownershipRow(id, "trashed", ProjectOwnershipStatus.ANALYZED);
        row.setDeletedAt(java.time.Instant.now());
        when(repo.findById(id)).thenReturn(Optional.of(row));

        ProjectResponse p = service.getProject(id);

        assertThat(p.getName()).isEqualTo("trashed"); // cache value, no DB overlay
        assertThat(p.getStatus()).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("H6: ownership DB outage degrades to the in-memory registry instead of failing")
    void shouldDegradeWhenOwnershipRepositoryUnavailable() throws IOException {
        ProjectOwnershipRepository repo = mock(ProjectOwnershipRepository.class);
        ReflectionTestUtils.setField(service, "ownershipRepository", repo);
        String id = createProject("resilient");
        when(repo.findById(id)).thenThrow(new RuntimeException("db down"));

        ProjectResponse p = service.getProject(id);

        assertThat(p.getName()).isEqualTo("resilient");
        assertThat(p.getStatus()).isEqualTo("CREATED");
    }

    private ProjectOwnership ownershipRow(String projectId, String name, ProjectOwnershipStatus status) {
        return ProjectOwnership.builder()
                .projectId(projectId)
                .ownerId(UUID.randomUUID())
                .name(name)
                .sourceType(ProjectSourceType.LOCAL)
                .status(status)
                .build();
    }

    private String createProject(String name) throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("proj-" + name));
        return service.createProject(CreateProjectRequest.builder()
                .name(name)
                .rootPath(root.toString())
                .build()).getId();
    }
}
