package com.vibegraph.graph.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.service.AccountQuotaSnapshot;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.FeatureGateService;
import com.vibegraph.auth.service.ProjectUsageService;
import com.vibegraph.abuse.AbuseProperties;
import com.vibegraph.abuse.ConcurrentImportGuard;
import com.vibegraph.common.exception.AccountBlockedException;
import com.vibegraph.common.exception.FeatureDisabledException;
import com.vibegraph.common.ownership.ProjectOwnershipRegistrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.common.exception.GithubImportException;
import com.vibegraph.graph.dto.request.GithubImportRequest;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.dto.response.ProjectStatus;
import com.vibegraph.graph.importer.ArchiveExtractionResult;
import com.vibegraph.graph.importer.ArchiveExtractor;
import com.vibegraph.graph.importer.ArchiveType;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;
import com.vibegraph.graph.importer.github.GitHubPreFlightService;
import com.vibegraph.graph.importer.github.GitHubRepositoryRef;
import com.vibegraph.graph.importer.github.GitHubTarballClient;
import com.vibegraph.graph.importer.github.GitHubUrlParser;
import com.vibegraph.graph.service.AnalyzeService;
import com.vibegraph.graph.service.AnalyzeService.AnalysisResult;
import com.vibegraph.graph.service.ProjectService;
import com.vibegraph.graph.websocket.FileChangeBroadcaster;
import com.vibegraph.graph.websocket.GraphUpdateController;

@ExtendWith(MockitoExtension.class)
@DisplayName("TarballImportServiceImpl")
class TarballImportServiceImplTest {

    @TempDir
    Path tempDir;

    @Mock
    GitHubPreFlightService preFlightService;
    @Mock
    GitHubTarballClient tarballClient;
    @Mock
    ArchiveExtractor archiveExtractor;
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

    private final List<Runnable> backgroundTasks = new ArrayList<>();
    private Path workspaceRoot;
    private TarballImportServiceImpl service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workspaceRoot = tempDir.resolve("uploads");
        ArchiveImportProperties properties = new ArchiveImportProperties();
        properties.setWorkspaceRoot(workspaceRoot);
        lenient().when(currentUser.id()).thenReturn(userId);
        lenient().when(accountSettingsService.quotaSnapshot(userId))
                .thenReturn(new AccountQuotaSnapshot(0, 104857600L, 104857600L, "TEST", "Test", null));
        service = new TarballImportServiceImpl(new GitHubUrlParser(), preFlightService, tarballClient, properties,
                archiveExtractor, projectService, analyzeService, graphUpdateController, fileChangeBroadcaster,
                backgroundTasks::add, accountSettingsService, projectUsageService,
                currentUser, ownershipRegistrar, featureGateService,
                new ConcurrentImportGuard(new AbuseProperties()));
    }

    @Test
    @DisplayName("imports GitHub tarball, returns ANALYZING project, and defers analysis")
    void importsGithubTarballAndDefersAnalysis() throws Exception {
        GitHubRepositoryRef resolved = new GitHubRepositoryRef("acme", "demo", "main");
        Path extractedRoot = workspaceRoot.resolve("github-test/source");
        Path javaFile = extractedRoot.resolve("src/App.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, "class App {}");

        when(preFlightService.validatePublicRepository(any(GitHubRepositoryRef.class), anyLong())).thenReturn(resolved);
        when(archiveExtractor.extract(any(Path.class), eq(ArchiveType.TAR_GZ), any(Path.class), anyLong()))
                .thenReturn(new ArchiveExtractionResult(extractedRoot, List.of(javaFile), List.of("src/App.java")));
        ProjectResponse created = ProjectResponse.builder().id("p1").name("acme/demo").rootPath("rp").status("CREATED").build();
        ProjectResponse analyzing = ProjectResponse.builder().id("p1").name("acme/demo").status("ANALYZING").progress(0).build();
        when(projectService.createProjectFromWorkspace("acme/demo", extractedRoot)).thenReturn(created);
        when(projectService.getProject("p1")).thenReturn(analyzing);

        ProjectResponse result = service.importFromGithub(new GithubImportRequest("https://github.com/acme/demo"));

        assertThat(result.getStatus()).isEqualTo("ANALYZING");
        verify(preFlightService).validatePublicRepository(new GitHubRepositoryRef("acme", "demo", null), 104857600L);
        verify(tarballClient).downloadTarball(eq(resolved), any(Path.class), eq(104857600L));
        verify(projectService).markAnalyzing("p1");
        verify(graphUpdateController).broadcastStatus(eq("p1"), eq(ProjectStatus.ANALYZING), eq(0), any(String.class));
        verify(analyzeService, never()).analyzeProject(any(), any(), any(), any());
        assertThat(backgroundTasks).hasSize(1);

        when(analyzeService.analyzeProject(eq("p1"), eq("acme/demo"), eq("rp"), any()))
                .thenReturn(new AnalysisResult("p1", 1, 5, 4, 0));
        backgroundTasks.get(0).run();

        verify(projectService).markAnalyzed("p1", 1, 5, 4);
        verify(graphUpdateController).broadcastStatus(eq("p1"), eq(ProjectStatus.ANALYZED), eq(100), any(String.class));
        verify(fileChangeBroadcaster).watchProject("p1", "rp");
    }

    @Test
    @DisplayName("disabled GitHub import flag blocks before preflight or download")
    void disabledGithubImportFlag_blocksBeforeNetwork() {
        doThrow(new FeatureDisabledException(FeatureGateService.IMPORT_GITHUB))
                .when(featureGateService).assertEnabled(FeatureGateService.IMPORT_GITHUB);

        assertThatThrownBy(() -> service.importFromGithub(new GithubImportRequest("https://github.com/acme/demo")))
                .isInstanceOf(FeatureDisabledException.class);

        verify(preFlightService, never()).validatePublicRepository(any(), anyLong());
        verify(tarballClient, never()).downloadTarball(any(), any(), anyLong());
        verify(projectService, never()).createProjectFromWorkspace(any(), any());
    }

    @Test
    @DisplayName("blocked account stops before GitHub preflight or download")
    void blockedAccount_stopsBeforeNetwork() {
        doThrow(new AccountBlockedException("internal reason", "Policy review"))
                .when(accountSettingsService).assertNotBlocked(userId);

        assertThatThrownBy(() -> service.importFromGithub(new GithubImportRequest("https://github.com/acme/demo")))
                .isInstanceOf(AccountBlockedException.class);

        verify(preFlightService, never()).validatePublicRepository(any(), anyLong());
        verify(tarballClient, never()).downloadTarball(any(), any(), anyLong());
        verify(archiveExtractor, never()).extract(any(), any(), any());
        verify(projectService, never()).createProjectFromWorkspace(any(), any());
    }

    @Test
    @DisplayName("preflight failure stops before tarball download and project creation")
    void rejectsPreflightFailureBeforeDownload() {
        GitHubRepositoryRef parsed = new GitHubRepositoryRef("acme", "private", null);
        when(preFlightService.validatePublicRepository(parsed, 104857600L))
                .thenThrow(new GithubImportException("GitHub repository is private or not found"));

        assertThatThrownBy(() -> service.importFromGithub(new GithubImportRequest("https://github.com/acme/private")))
                .isInstanceOf(GithubImportException.class)
                .hasMessage("GitHub repository is private or not found");

        verify(preFlightService).validatePublicRepository(parsed, 104857600L);
        verify(tarballClient, never()).downloadTarball(any(), any(), anyLong());
        verify(archiveExtractor, never()).extract(any(), any(), any());
        verify(projectService, never()).createProjectFromWorkspace(any(), any());
        verify(graphUpdateController, never()).broadcastStatus(any(), any(ProjectStatus.class), any(Integer.class), any());
        verify(analyzeService, never()).analyzeProject(any(), any(), any(), any());
        verify(fileChangeBroadcaster, never()).watchProject(any(), any());
        assertThat(backgroundTasks).isEmpty();
    }

    @Test
    @DisplayName("cleans workspace and deletes project when preparation fails after project creation")
    void cleansUpWhenPreparationFailsAfterProjectCreation() throws Exception {
        GitHubRepositoryRef resolved = new GitHubRepositoryRef("acme", "demo", "main");
        Path extractedRoot = workspaceRoot.resolve("github-test/source");
        Files.createDirectories(extractedRoot);

        when(preFlightService.validatePublicRepository(any(GitHubRepositoryRef.class), anyLong())).thenReturn(resolved);
        when(archiveExtractor.extract(any(Path.class), eq(ArchiveType.TAR_GZ), any(Path.class), anyLong()))
                .thenReturn(new ArchiveExtractionResult(extractedRoot, List.of(extractedRoot.resolve("App.java")), List.of("App.java")));
        when(projectService.createProjectFromWorkspace("acme/demo", extractedRoot))
                .thenReturn(ProjectResponse.builder().id("p1").rootPath("rp").build());
        when(projectService.getProject("p1")).thenThrow(new IllegalStateException("lookup failed"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.importFromGithub(new GithubImportRequest("https://github.com/acme/demo")))
                .isInstanceOf(IllegalStateException.class);

        verify(projectService).deleteProject("p1");
        verify(fileChangeBroadcaster, never()).watchProject(any(), any());
        ArgumentCaptor<Path> tarballPath = ArgumentCaptor.forClass(Path.class);
        verify(tarballClient).downloadTarball(eq(resolved), tarballPath.capture(), eq(104857600L));
        assertThat(Files.exists(tarballPath.getValue().getParent())).isFalse();
    }
}
