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
import com.vibegraph.common.exception.InsufficientCreditsException;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.vibegraph.graph.service.ImportCreditBilling;
import com.vibegraph.graph.service.ProjectService;
import com.vibegraph.graph.websocket.FileChangeBroadcaster;
import com.vibegraph.graph.websocket.GraphUpdateController;
import com.vibegraph.infrastructure.service.OperationTelemetryRecorder;

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
    @Mock com.vibegraph.graph.repository.GraphRepository graphRepository;
    @Mock ImportCreditBilling importCreditBilling;
    @Mock OperationTelemetryRecorder telemetryRecorder;

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
                new ConcurrentImportGuard(new AbuseProperties()), graphRepository, importCreditBilling);
        service.setTelemetryRecorder(telemetryRecorder);
    }

    @Test
    @DisplayName("imports a valid ZIP: extracts under workspaceRoot, registers, analyzes, returns the project")
    void importsValidZip() throws IOException {
        MockMultipartFile file = zip("project.zip", Map.of("src/App.java", "class App {}"));
        ProjectResponse created = ProjectResponse.builder().id("p1").name("demo").rootPath("rp").status("CREATED").build();
        ProjectResponse analyzed = ProjectResponse.builder().id("p1").name("demo").status("ANALYZED").totalFiles(1).build();
        when(projectService.createProjectFromWorkspace(eq("demo"), any(Path.class))).thenReturn(created);
        when(analyzeService.analyzeProjectWithinOperation(eq("p1"), eq("demo"), eq("rp"), any()))
                .thenReturn(new AnalysisResult("p1", 1, 5, 4, 0));
        when(projectService.getProject("p1")).thenReturn(analyzed);

        ProjectResponse result = service.importArchive("demo", file);

        assertThat(result).isSameAs(analyzed);
        ArgumentCaptor<Path> source = ArgumentCaptor.forClass(Path.class);
        verify(projectService).createProjectFromWorkspace(eq("demo"), source.capture());
        assertThat(source.getValue()).startsWith(workspaceRoot.toAbsolutePath().normalize());
        assertThat(Files.readString(source.getValue().resolve("src/App.java"))).contains("class App");
        verify(analyzeService).analyzeProjectWithinOperation(eq("p1"), eq("demo"), eq("rp"), any());
        verify(projectService).updateProjectStats("p1", 1, 5, 4);
        verify(fileChangeBroadcaster).watchProject("p1", "rp");
        // Billed upfront by the extracted .java file count.
        verify(importCreditBilling).chargeUpfront(userId, ImportCreditBilling.OPERATION_IMPORT_ARCHIVE, 1, "p1");
    }

    @Test
    @DisplayName("an exhausted credit balance blocks the import and cleans up the partially registered project")
    void insufficientCreditsBlocksImport() throws IOException {
        MockMultipartFile file = zip("project.zip", Map.of("src/App.java", "class App {}"));
        ProjectResponse created = ProjectResponse.builder().id("p1").name("demo").rootPath("rp").status("CREATED").build();
        when(projectService.createProjectFromWorkspace(eq("demo"), any(Path.class))).thenReturn(created);
        doThrow(new InsufficientCreditsException(
                "Insufficient credits to perform this operation. Required: 2, Available: 0", 2L, 0L))
                .when(importCreditBilling).chargeUpfront(userId, ImportCreditBilling.OPERATION_IMPORT_ARCHIVE, 1, "p1");

        assertThatThrownBy(() -> service.importArchive("demo", file))
                .isInstanceOf(InsufficientCreditsException.class);

        verify(projectService).deleteProject("p1");
        verify(projectUsageService, never()).recordImport(any(), any(), org.mockito.ArgumentMatchers.anyLong());
        verify(analyzeService, never()).analyzeProjectWithinOperation(any(), any(), any(), any());
        assertNoWorkspaceLeftover();
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
        when(analyzeService.analyzeProjectWithinOperation(eq("p1"), eq("demo"), eq("rp"), any()))
                .thenThrow(new IllegalStateException("neo4j down"));

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

        var token = new OperationTelemetryRecorder.OperationToken("evt-archive");
        ProjectResponse result = service.importArchiveAsync("demo", file, token);

        assertThat(result.getStatus()).isEqualTo("ANALYZING");
        assertThat(result.getProgress()).isZero();
        verify(projectService).markAnalyzing("p1");
        verify(graphUpdateController).broadcastStatus("p1", ProjectStatus.ANALYZING, 0);
        verify(analyzeService, never()).analyzeProjectWithinOperation(any(), any(), any(), any());
        verify(telemetryRecorder).attach(token, "p1", "demo");
        verify(telemetryRecorder, never()).complete(eq(token), anyInt(), anyInt(), anyLong());
        assertThat(backgroundTasks).hasSize(1);

        when(analyzeService.analyzeProjectWithinOperation(eq("p1"), eq("demo"), eq("rp"), any()))
                .thenReturn(new AnalysisResult("p1", 1, 5, 4, 0));
        backgroundTasks.get(0).run();

        verify(analyzeService).analyzeProjectWithinOperation(eq("p1"), eq("demo"), eq("rp"), any());
        verify(projectService).markAnalyzed("p1", 1, 5, 4);
        verify(graphUpdateController).broadcastStatus("p1", ProjectStatus.ANALYZED, 100);
        verify(fileChangeBroadcaster).watchProject("p1", "rp");
        verify(telemetryRecorder).complete(token, 5, 4, 12L);
    }

    @Test
    @DisplayName("async analyze failure marks FAILED + broadcasts FAILED, removes the workspace but keeps the project")
    void asyncAnalyzeFailureMarksFailedAndCleansWorkspace() throws IOException {
        MockMultipartFile file = zip("project.zip", Map.of("src/App.java", "class App {}"));
        ProjectResponse created = ProjectResponse.builder().id("p1").name("demo").rootPath("rp").status("CREATED").build();
        when(projectService.createProjectFromWorkspace(eq("demo"), any(Path.class))).thenReturn(created);
        when(projectService.getProject("p1")).thenReturn(created);
        var failure = new IllegalStateException("neo4j down");
        when(analyzeService.analyzeProjectWithinOperation(eq("p1"), eq("demo"), eq("rp"), any()))
                .thenThrow(failure);

        var token = new OperationTelemetryRecorder.OperationToken("evt-archive-failed");
        service.importArchiveAsync("demo", file, token);
        backgroundTasks.get(0).run();

        verify(projectService).markFailed("p1", "neo4j down");
        verify(graphUpdateController).broadcastStatus("p1", "FAILED", 0, "neo4j down");
        // B-M11: the FAILED project keeps its row but its (possibly partial) graph is removed,
        // since the workspace backing it is gone.
        verify(graphRepository).deleteProject("p1");
        verify(projectService, never()).deleteProject("p1");
        verify(fileChangeBroadcaster, never()).watchProject(any(), any());
        verify(telemetryRecorder).fail(token, failure);
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
