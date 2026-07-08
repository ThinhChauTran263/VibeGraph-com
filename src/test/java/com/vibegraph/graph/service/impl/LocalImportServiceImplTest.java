package com.vibegraph.graph.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import com.vibegraph.graph.config.ProjectsProperties;
import com.vibegraph.graph.dto.request.LocalImportRequest;
import com.vibegraph.graph.dto.response.DirectoryListing;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.dto.response.ProjectStatus;
import com.vibegraph.graph.service.AnalyzeService;
import com.vibegraph.graph.service.AnalyzeService.AnalysisResult;
import com.vibegraph.graph.service.ProjectService;
import com.vibegraph.graph.websocket.FileChangeBroadcaster;
import com.vibegraph.graph.websocket.GraphUpdateController;

@DisplayName("LocalImportServiceImpl")
class LocalImportServiceImplTest {

    private ProjectService projectService;
    private AnalyzeService analyzeService;
    private FileChangeBroadcaster fileChangeBroadcaster;
    private GraphUpdateController graphUpdateController;
    private ProjectsProperties properties;
    private LocalImportServiceImpl service;

    @BeforeEach
    void setUp() {
        projectService = Mockito.mock(ProjectService.class);
        analyzeService = Mockito.mock(AnalyzeService.class);
        fileChangeBroadcaster = Mockito.mock(FileChangeBroadcaster.class);
        graphUpdateController = Mockito.mock(GraphUpdateController.class);
        properties = new ProjectsProperties();
        // Run the "background" analysis inline so the async flow is deterministic in tests.
        service = new LocalImportServiceImpl(projectService, analyzeService, fileChangeBroadcaster,
                properties, graphUpdateController, Runnable::run);
    }

    @Test
    @DisplayName("importLocal analyzes in place, persists stats, broadcasts, and starts watching")
    void importLocalAnalyzesAndWatches() {
        ProjectResponse created = ProjectResponse.builder()
                .id("p1").name("demo").rootPath("/srv/demo").status("CREATED").build();
        ProjectResponse analyzing = ProjectResponse.builder()
                .id("p1").name("demo").status("ANALYZING").build();
        Mockito.when(projectService.createProject(ArgumentMatchers.any())).thenReturn(created);
        Mockito.when(analyzeService.analyzeProject(
                        ArgumentMatchers.eq("p1"), ArgumentMatchers.eq("demo"), ArgumentMatchers.eq("/srv/demo"),
                        ArgumentMatchers.any()))
                .thenReturn(new AnalysisResult("p1", 3, 10, 7, 0));
        Mockito.when(projectService.getProject("p1")).thenReturn(analyzing);

        service.importLocal(new LocalImportRequest("/srv/demo", "demo"));

        Mockito.verify(projectService).createProject(ArgumentMatchers.argThat(
                r -> "demo".equals(r.getName()) && "/srv/demo".equals(r.getRootPath())));
        Mockito.verify(projectService).markAnalyzing("p1");
        Mockito.verify(graphUpdateController).broadcastStatus("p1", ProjectStatus.ANALYZING, 0);
        Mockito.verify(analyzeService).analyzeProject(
                ArgumentMatchers.eq("p1"), ArgumentMatchers.eq("demo"), ArgumentMatchers.eq("/srv/demo"),
                ArgumentMatchers.any());
        Mockito.verify(projectService).markAnalyzed("p1", 3, 10, 7);
        Mockito.verify(graphUpdateController).broadcastStatus("p1", ProjectStatus.ANALYZED, 100);
        Mockito.verify(fileChangeBroadcaster).watchProject("p1", "/srv/demo");
    }

    @Test
    @DisplayName("importLocal marks FAILED and does not watch when analysis fails")
    void importLocalMarksFailedOnFailure() {
        ProjectResponse created = ProjectResponse.builder()
                .id("p1").name("demo").rootPath("/srv/demo").status("CREATED").build();
        Mockito.when(projectService.createProject(ArgumentMatchers.any())).thenReturn(created);
        Mockito.when(projectService.getProject("p1")).thenReturn(created);
        Mockito.when(analyzeService.analyzeProject(
                        ArgumentMatchers.eq("p1"), ArgumentMatchers.eq("demo"), ArgumentMatchers.eq("/srv/demo"),
                        ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("neo4j down"));

        service.importLocal(new LocalImportRequest("/srv/demo", "demo"));

        Mockito.verify(projectService).markFailed("p1", "neo4j down");
        Mockito.verify(graphUpdateController).broadcastStatus("p1", "FAILED", 0, "neo4j down");
        Mockito.verify(fileChangeBroadcaster, Mockito.never()).watchProject(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    @DisplayName("browse lists sub-directories, excludes ignored/hidden dirs, and flags Java project roots")
    void browseListsSubDirectories(@TempDir Path base) throws Exception {
        properties.setAllowedRoot(base.toString());
        Files.createDirectories(base.resolve("my-app/src/main/java"));
        Files.writeString(base.resolve("my-app/pom.xml"), "<project/>\n");
        Files.createDirectories(base.resolve("docs"));
        Files.createDirectories(base.resolve("node_modules"));
        Files.createDirectories(base.resolve(".git"));

        DirectoryListing listing = service.browse(null);

        assertThat(listing.path()).isEqualTo(base.toRealPath().toString());
        assertThat(listing.parent()).isNull();
        assertThat(listing.entries()).extracting(DirectoryListing.Entry::name)
                .containsExactlyInAnyOrder("my-app", "docs");
        assertThat(entry(listing, "my-app").containsJava()).isTrue();
        assertThat(entry(listing, "docs").containsJava()).isFalse();
    }

    @Test
    @DisplayName("browse into a sub-directory exposes a parent confined to the base")
    void browseSubDirectoryParentWithinBase(@TempDir Path base) throws Exception {
        properties.setAllowedRoot(base.toString());
        Files.createDirectories(base.resolve("src"));

        DirectoryListing listing = service.browse(base.resolve("src").toString());

        assertThat(listing.parent()).isEqualTo(base.toRealPath().toString());
    }

    @Test
    @DisplayName("browse rejects a path outside the allowed base when confined")
    void browseRejectsEscape(@TempDir Path base, @TempDir Path outside) {
        properties.setAllowedRoot(base.toString());

        assertThatThrownBy(() -> service.browse(outside.toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("browse with a blank path lists the filesystem roots when unconfined")
    void browseListsRootsWhenUnconfined() {
        // allowedRoot left blank → "This PC" view: every drive/root, with no parent above it.
        DirectoryListing listing = service.browse(null);

        assertThat(listing.parent()).isNull();
        assertThat(listing.path()).isEmpty();
        assertThat(listing.entries()).isNotEmpty();
        // Every entry is an actual filesystem root (e.g. "C:\" on Windows, "/" on Unix).
        java.util.Set<String> roots = new java.util.HashSet<>();
        java.nio.file.FileSystems.getDefault().getRootDirectories()
                .forEach(r -> roots.add(r.toString()));
        assertThat(listing.entries()).allSatisfy(e -> assertThat(roots).contains(e.path()));
    }

    @Test
    @DisplayName("browse of any accessible directory is allowed when unconfined")
    void browseAllowsAnyDirectoryWhenUnconfined(@TempDir Path anywhere) throws Exception {
        // allowedRoot left blank → a developer can navigate to a project on any drive.
        Files.createDirectories(anywhere.resolve("my-app/src"));
        Files.writeString(anywhere.resolve("my-app/pom.xml"), "<project/>\n");

        DirectoryListing listing = service.browse(anywhere.toString());

        assertThat(listing.path()).isEqualTo(anywhere.toRealPath().toString());
        assertThat(listing.parent()).isEqualTo(anywhere.toRealPath().getParent().toString());
        assertThat(listing.entries()).extracting(DirectoryListing.Entry::name).contains("my-app");
        assertThat(entry(listing, "my-app").containsJava()).isTrue();
    }

    private static DirectoryListing.Entry entry(DirectoryListing listing, String name) {
        return listing.entries().stream()
                .filter(e -> e.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no entry " + name));
    }
}
