package com.vibegraph.graph.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.vibegraph.graph.dto.request.CreateProjectRequest;
import com.vibegraph.graph.dto.response.ProjectResponse;

@DisplayName("ProjectServiceImpl")
class ProjectServiceImplTest {

    @TempDir
    Path tempDir;

    private final ProjectServiceImpl service = new ProjectServiceImpl();

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
}
