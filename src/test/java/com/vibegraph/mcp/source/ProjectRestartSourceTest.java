package com.vibegraph.mcp.source;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.ObjectProvider;

import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.graph.repository.ProjectMetadata;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.graph.service.impl.ProjectServiceImpl;
import com.vibegraph.mcp.dto.response.SourceFileContextResponse;
import com.vibegraph.mcp.service.impl.SourceFileAnalyzerImpl;
import com.vibegraph.mcp.source.impl.SourceFileServiceImpl;

/**
 * Simulates a backend restart: the in-memory project registry is empty, but the Neo4j
 * {@code Project} node persists. {@code get_source_file} must still resolve the source root.
 */
@DisplayName("Source reading survives a registry restart")
class ProjectRestartSourceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("get_source_file resolves source root from persisted metadata after restart")
    void sourceFileResolvesAfterRestart() throws IOException {
        Path workspaceRoot = Files.createDirectories(tempDir.resolve("uploads")).toRealPath();
        Path source = Files.createDirectories(workspaceRoot.resolve("proj-x/source"));
        Path javaFile = source.resolve("demo/Hello.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, "package demo;\npublic class Hello {}\n", StandardCharsets.UTF_8);

        // Fresh ProjectServiceImpl with an EMPTY in-memory registry (post-restart).
        ArchiveImportProperties props = new ArchiveImportProperties();
        props.setWorkspaceRoot(workspaceRoot);
        GraphRepository graphRepository = mock(GraphRepository.class);
        when(graphRepository.findProject("proj-x"))
                .thenReturn(new ProjectMetadata("proj-x", "Demo", source.toString()));
        ProjectServiceImpl projectService = new ProjectServiceImpl(
                "",
                props,
                emptyProvider(),
                providerOf(graphRepository),
                emptyProvider(),
                emptyProvider());

        GraphService graphService = mock(GraphService.class);
        when(graphService.getFullGraph("proj-x"))
                .thenReturn(GraphDataResponse.builder().nodes(java.util.List.of()).edges(java.util.List.of()).build());

        SourceFileServiceImpl fileService = new SourceFileServiceImpl(projectService);
        SourceGraphSupport support = new SourceGraphSupport(graphService);
        SourceFileAnalyzerImpl analyzer = new SourceFileAnalyzerImpl(fileService, support);

        SourceFileContextResponse result = analyzer.readSourceFile("proj-x", "demo/Hello.java", null, null);

        assertThat(result.getRelativePath()).isEqualTo("demo/Hello.java");
        assertThat(result.getContent()).contains("class Hello");
        assertThat(result.toString()).doesNotContain(workspaceRoot.toString());
    }

    /** Spring 7's ObjectProvider has no static factories; a mock returns null from getIfAvailable(). */
    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> emptyProvider() {
        return mock(ObjectProvider.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
