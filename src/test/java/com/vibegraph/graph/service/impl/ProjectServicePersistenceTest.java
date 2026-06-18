package com.vibegraph.graph.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.mock;
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
                .thenReturn(new ProjectMetadata("proj-x", "Demo Repo", source.toString()));

        ProjectResponse recovered = service.getProject("proj-x");

        assertThat(recovered.getId()).isEqualTo("proj-x");
        assertThat(recovered.getName()).isEqualTo("Demo Repo");
        assertThat(recovered.getRootPath()).isEqualTo(source.toString());
        assertThat(recovered.getStatus()).isEqualTo("ANALYZED");
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
