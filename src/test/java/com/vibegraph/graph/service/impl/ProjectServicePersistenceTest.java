package com.vibegraph.graph.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.graph.repository.ProjectMetadata;

@DisplayName("ProjectServiceImpl persisted-fallback (restart reliability)")
class ProjectServicePersistenceTest {

    @TempDir
    Path tempDir;

    private final ProjectServiceImpl service = new ProjectServiceImpl();
    private final GraphRepository graphRepository = mock(GraphRepository.class);
    private Path workspaceRoot;

    @BeforeEach
    void setUp() throws IOException {
        workspaceRoot = Files.createDirectories(tempDir.resolve("uploads"));
        ArchiveImportProperties props = new ArchiveImportProperties();
        props.setWorkspaceRoot(workspaceRoot);
        ReflectionTestUtils.setField(service, "archiveImportProperties", props);
        ReflectionTestUtils.setField(service, "graphRepository", graphRepository);
    }

    @Test
    @DisplayName("recovers a project from persisted metadata after the in-memory registry is lost")
    void recoversPersistedProjectAfterRestart() throws IOException {
        Path source = Files.createDirectories(workspaceRoot.resolve("proj-x/source"));
        when(graphRepository.findProject("proj-x"))
                .thenReturn(new ProjectMetadata(
                        "proj-x",
                        "Demo Repo",
                        source.toString(),
                        Instant.parse("2026-07-17T10:00:00Z"),
                        Instant.parse("2026-07-17T11:15:00Z"),
                        24,
                        180,
                        320));

        ProjectResponse recovered = service.getProject("proj-x");

        assertThat(recovered.getId()).isEqualTo("proj-x");
        assertThat(recovered.getName()).isEqualTo("Demo Repo");
        assertThat(recovered.getRootPath()).isEqualTo(source.toString());
        assertThat(recovered.getStatus()).isEqualTo("ANALYZED");
        assertThat(recovered.getTotalFiles()).isEqualTo(24);
        assertThat(recovered.getTotalNodes()).isEqualTo(180);
        assertThat(recovered.getTotalEdges()).isEqualTo(320);
        assertThat(recovered.getCreatedAt()).isEqualTo(Instant.parse("2026-07-17T10:00:00Z"));
        assertThat(recovered.getLastAnalyzedAt()).isEqualTo(Instant.parse("2026-07-17T11:15:00Z"));
    }

    @Test
    @DisplayName("lists recovered persisted projects with stats after restart")
    void listsRecoveredPersistedProjectsWithStats() throws IOException {
        Path source = Files.createDirectories(workspaceRoot.resolve("proj-y/source"));
        when(graphRepository.findAllProjects())
                .thenReturn(List.of(new ProjectMetadata(
                        "proj-y",
                        "Demo Repo Y",
                        source.toString(),
                        Instant.parse("2026-07-17T12:00:00Z"),
                        Instant.parse("2026-07-17T12:30:00Z"),
                        13,
                        91,
                        140)));

        List<ProjectResponse> projects = service.listProjects();

        assertThat(projects).singleElement()
                .satisfies(project -> {
                    assertThat(project.getId()).isEqualTo("proj-y");
                    assertThat(project.getStatus()).isEqualTo("ANALYZED");
                    assertThat(project.getTotalFiles()).isEqualTo(13);
                    assertThat(project.getTotalNodes()).isEqualTo(91);
                    assertThat(project.getTotalEdges()).isEqualTo(140);
                    assertThat(project.getLastAnalyzedAt()).isEqualTo(Instant.parse("2026-07-17T12:30:00Z"));
                });
    }

    @Test
    @DisplayName("returns ProjectNotFound when neither in-memory nor persisted metadata exists")
    void notFoundWhenAbsentEverywhere() {
        when(graphRepository.findProject("ghost")).thenReturn(null);

        assertThatThrownBy(() -> service.getProject("ghost"))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    @DisplayName("rejects a persisted root that escapes the allowed workspace")
    void rejectsEscapingPersistedRoot() throws IOException {
        Path outside = Files.createDirectories(tempDir.resolve("outside/evil"));
        when(graphRepository.findProject("evil"))
                .thenReturn(new ProjectMetadata("evil", "evil", outside.toString()));

        assertThatThrownBy(() -> service.getProject("evil"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the allowed workspace");
    }

    @Test
    @DisplayName("deletes a project recovered from persisted metadata after restart")
    void deletesPersistedProjectAfterRestart() throws IOException {
        Path source = Files.createDirectories(workspaceRoot.resolve("delete-me/source"));
        when(graphRepository.findProject("delete-me"))
                .thenReturn(new ProjectMetadata("delete-me", "Delete Me", source.toString()));

        service.deleteProject("delete-me");

        verify(graphRepository).deleteProject("delete-me");
    }

    @Test
    @DisplayName("in-memory project takes precedence and never touches persistence")
    void inMemoryTakesPrecedence() throws IOException {
        Path source = Files.createDirectories(workspaceRoot.resolve("mem/source"));
        ProjectResponse inMemory = ProjectResponse.builder()
                .id("mem").name("mem").rootPath(source.toString()).status("ANALYZED").build();
        @SuppressWarnings("unchecked")
        java.util.Map<String, ProjectResponse> map =
                (java.util.Map<String, ProjectResponse>) ReflectionTestUtils.getField(service, "projects");
        map.put("mem", inMemory);

        ProjectResponse result = service.getProject("mem");

        assertThat(result).isSameAs(inMemory);
        verifyNoInteractions(graphRepository);
    }
}
