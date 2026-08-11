package com.vibegraph.graph.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.UUID;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.service.AccountQuotaSnapshot;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.FeatureGateService;
import com.vibegraph.auth.service.ProjectUsageService;
import com.vibegraph.abuse.AbuseProperties;
import com.vibegraph.abuse.ConcurrentImportGuard;
import com.vibegraph.common.exception.FeatureDisabledException;
import com.vibegraph.common.ownership.ProjectOwnershipRegistrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.vibegraph.common.exception.ArchiveImportException;
import com.vibegraph.common.exception.ArchiveImportException.Reason;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.dto.response.ProjectStatus;
import com.vibegraph.graph.importer.ArchiveExtractor;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;
import com.vibegraph.graph.service.AnalyzeService;
import com.vibegraph.graph.service.AnalyzeService.AnalysisResult;
import com.vibegraph.graph.service.ProjectService;
import com.vibegraph.graph.websocket.FileChangeBroadcaster;
import com.vibegraph.graph.websocket.GraphUpdateController;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArchiveImportServiceImpl")
class ArchiveImportServiceImplTest {

    @TempDir
    Path tempDir;

    @Mock
    ProjectService projectService;
    @Mock
    AnalyzeService analyzeService;
    @Mock
    GraphUpdateController graphUpdateController;
    @Mock
    FileChangeBroadcaster fileChangeBroadcaster;

    @Mock AccountSettingsService accountSettingsService;
    @Mock ProjectUsageService projectUsageService;
    @Mock CurrentUser currentUser;
    @Mock ProjectOwnershipRegistrar ownershipRegistrar;
    @Mock FeatureGateService featureGateService;

    /** Capturing executor: background analysis runs only when we drain this list. */
    private final List<Runnable> backgroundTasks = new ArrayList<>();
    private Path workspaceRoot;
    private ArchiveImportServiceImpl service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workspaceRoot = tempDir.resolve("uploads");
        ArchiveImportProperties properties = new ArchiveImportProperties();
        properties.setWorkspaceRoot(workspaceRoot);
        lenient().when(currentUser.id()).thenReturn(userId);
        lenient().when(accountSettingsService.quotaSnapshot(userId))
                .thenReturn(new AccountQuotaSnapshot(0, Long.MAX_VALUE, Long.MAX_VALUE, "TEST", "Test", null));
        service = new ArchiveImportServiceImpl(properties, new ArchiveExtractor(properties),
                projectService, analyzeService, graphUpdateController, fileChangeBroadcaster, backgroundTasks::add,
                accountSettingsService, projectUsageService, currentUser, ownershipRegistrar, featureGateService,
                new ConcurrentImportGuard(new AbuseProperties()));
    }

    @Test
    @DisplayName("imports a valid ZIP: extracts under workspaceRoot, registers, analyzes, returns the project")
    void importsValidZip() throws IOException {
        MockMultipartFile file = zip("project.zip", Map.of("src/App.java", "class App {}"));
        ProjectResponse created = ProjectResponse.builder().id("p1").name("demo").rootPath("rp").status("CREATED").build();
        ProjectResponse analyzed = ProjectResponse.builder().id("p1").name("demo").status("ANALYZED").totalFiles(1).build();
        when(projectService.createProjectFromWorkspace(eq("demo"), any(Path.class))).thenReturn(created);
        when(analyzeService.analyzeProject("p1", "demo", "rp")).thenReturn(new AnalysisResult("p1", 1, 5, 4, 0));
        when(projectService.getProject("p1")).thenReturn(analyzed);

        ProjectResponse result = service.importArchive("demo", file);

        assertThat(result).isSameAs(analyzed);
        ArgumentCaptor<Path> source = ArgumentCaptor.forClass(Path.class);
        verify(projectService).createProjectFromWorkspace(eq("demo"), source.capture());
        assertThat(source.getValue()).startsWith(workspaceRoot.toAbsolutePath().normalize());
        assertThat(Files.readString(source.getValue().resolve("src/App.java"))).contains("class App");
        verify(analyzeService).analyzeProject("p1", "demo", "rp");
        verify(projectService).updateProjectStats("p1", 1, 5, 4);
        verify(fileChangeBroadcaster).watchProject("p1", "rp");
    }

    @Test
    @DisplayName("disabled archive import flag blocks before workspace creation")
    void disabledArchiveImportFlag_blocksBeforeWorkspaceCreation() throws IOException {
        doThrow(new FeatureDisabledException(FeatureGateService.IMPORT_ARCHIVE))
                .when(featureGateService).assertEnabled(FeatureGateService.IMPORT_ARCHIVE);
        MockMultipartFile file = zip("project.zip", Map.of("src/App.java", "class App {}"));

        assertThatThrownBy(() -> service.importArchive("demo", file))
                .isInstanceOf(FeatureDisabledException.class);

        assertThat(Files.exists(workspaceRoot)).isFalse();
        verify(projectService, never()).createProjectFromWorkspace(any(), any());
        verify(currentUser, never()).id();
    }

    @Test
    @DisplayName("rejects a missing/empty file with MISSING_FILE")
    void rejectsMissingFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "project.zip", "application/zip", new byte[0]);
        assertReason(() -> service.importArchive("demo", empty), Reason.MISSING_FILE);
    }

    @Test
    @DisplayName("rejects a blank project name with BLANK_NAME")
    void rejectsBlankName() throws IOException {
        MockMultipartFile file = zip("project.zip", Map.of("src/App.java", "class App {}"));
        assertReason(() -> service.importArchive("   ", file), Reason.BLANK_NAME);
    }

    @Test
    @DisplayName("rejects an unsupported archive type")
    void rejectsUnsupportedType() {
        MockMultipartFile txt = new MockMultipartFile("file", "notes.txt", "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8));
        assertReason(() -> service.importArchive("demo", txt), Reason.UNSUPPORTED_TYPE);
    }

    @Test
    @DisplayName("an archive with no .java yields EMPTY_ARCHIVE and leaves no workspace or project")
    void emptyArchiveCleansUp() throws IOException {
        MockMultipartFile file = zip("docs.zip", Map.of("README.md", "x"));

        assertReason(() -> service.importArchive("demo", file), Reason.EMPTY_ARCHIVE);

        verify(projectService, never()).createProjectFromWorkspace(any(), any());
        assertNoWorkspaceLeftover();
    }

    @Test
    @DisplayName("a failed analysis cleans up the workspace and deletes the partially-registered project")
    void analyzeFailureCleansUp() throws IOException {
        MockMultipartFile file = zip("project.zip", Map.of("src/App.java", "class App {}"));
        ProjectResponse created = ProjectResponse.builder().id("p1").name("demo").rootPath("rp").status("CREATED").build();
        when(projectService.createProjectFromWorkspace(eq("demo"), any(Path.class))).thenReturn(created);
        when(analyzeService.analyzeProject("p1", "demo", "rp")).thenThrow(new IllegalStateException("neo4j down"));

        assertThatThrownBy(() -> service.importArchive("demo", file)).isInstanceOf(IllegalStateException.class);

        verify(projectService).deleteProject("p1");
        verify(fileChangeBroadcaster, never()).watchProject(any(), any());
        assertNoWorkspaceLeftover();
    }

    @Test
    @DisplayName("async import registers + marks ANALYZING + broadcasts, deferring analysis to the executor")
    void asyncImportSubmitsAndReturnsAnalyzing() throws IOException {
        MockMultipartFile file = zip("project.zip", Map.of("src/App.java", "class App {}"));
        ProjectResponse created = ProjectResponse.builder().id("p1").name("demo").rootPath("rp").status("CREATED").build();
        ProjectResponse analyzing = ProjectResponse.builder().id("p1").name("demo").status("ANALYZING").progress(0).build();
        when(projectService.createProjectFromWorkspace(eq("demo"), any(Path.class))).thenReturn(created);
        when(projectService.getProject("p1")).thenReturn(analyzing);

        ProjectResponse result = service.importArchiveAsync("demo", file);

        assertThat(result.getStatus()).isEqualTo("ANALYZING");
        assertThat(result.getProgress()).isZero();
        verify(projectService).markAnalyzing("p1");
        verify(graphUpdateController).broadcastStatus("p1", ProjectStatus.ANALYZING, 0);
        verify(analyzeService, never()).analyzeProject(any(), any(), any(), any());
        assertThat(backgroundTasks).hasSize(1);

        when(analyzeService.analyzeProject(eq("p1"), eq("demo"), eq("rp"), any()))
                .thenReturn(new AnalysisResult("p1", 1, 5, 4, 0));
        backgroundTasks.get(0).run();

        verify(analyzeService).analyzeProject(eq("p1"), eq("demo"), eq("rp"), any());
        verify(projectService).markAnalyzed("p1", 1, 5, 4);
        verify(graphUpdateController).broadcastStatus("p1", ProjectStatus.ANALYZED, 100);
        verify(fileChangeBroadcaster).watchProject("p1", "rp");
    }

    @Test
    @DisplayName("async analyze failure marks FAILED + broadcasts FAILED, removes the workspace but keeps the project")
    void asyncAnalyzeFailureMarksFailedAndCleansWorkspace() throws IOException {
        MockMultipartFile file = zip("project.zip", Map.of("src/App.java", "class App {}"));
        ProjectResponse created = ProjectResponse.builder().id("p1").name("demo").rootPath("rp").status("CREATED").build();
        when(projectService.createProjectFromWorkspace(eq("demo"), any(Path.class))).thenReturn(created);
        when(projectService.getProject("p1")).thenReturn(created);
        when(analyzeService.analyzeProject(eq("p1"), eq("demo"), eq("rp"), any()))
                .thenThrow(new IllegalStateException("neo4j down"));

        service.importArchiveAsync("demo", file);
        backgroundTasks.get(0).run();

        verify(projectService).markFailed("p1", "neo4j down");
        verify(graphUpdateController).broadcastStatus("p1", "FAILED", 0, "neo4j down");
        verify(projectService, never()).deleteProject("p1");
        verify(fileChangeBroadcaster, never()).watchProject(any(), any());
        assertNoWorkspaceLeftover();
    }

    // ----------------------------- helpers -----------------------------

    private void assertReason(ThrowingCallable call, Reason expected) {
        assertThatThrownBy(call)
                .isInstanceOf(ArchiveImportException.class)
                .satisfies(e -> assertThat(((ArchiveImportException) e).getReason()).isEqualTo(expected));
    }

    private void assertNoWorkspaceLeftover() throws IOException {
        if (!Files.exists(workspaceRoot)) {
            return;
        }
        try (Stream<Path> children = Files.list(workspaceRoot)) {
            assertThat(children).isEmpty();
        }
    }

    private MockMultipartFile zip(String filename, Map<String, String> entries) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return new MockMultipartFile("file", filename, "application/zip", bos.toByteArray());
    }
}
