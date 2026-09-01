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
import com.vibegraph.common.exception.InsufficientCreditsException;
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
import com.vibegraph.graph.service.ImportCreditBilling;
import com.vibegraph.graph.service.ProjectService;
import com.vibegraph.graph.websocket.FileChangeBroadcaster;
import com.vibegraph.graph.websocket.GraphUpdateController;
import com.vibegraph.infrastructure.service.OperationTelemetryRecorder;

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
    @Mock com.vibegraph.common.ownership.ProjectTrashService trashService;
    @Mock com.vibegraph.graph.repository.GraphRepository graphRepository;
    @Mock ImportCreditBilling importCreditBilling;
    @Mock com.vibegraph.auth.repository.ProjectOwnershipRepository ownershipRepository;
    @Mock OperationTelemetryRecorder telemetryRecorder;

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
        lenient().when(ownershipRepository.findByOwnerIdAndSourceTypeAndNameAndDeletedAtIsNull(
                org.mockito.ArgumentMatchers.any(UUID.class),
                org.mockito.ArgumentMatchers.any(com.vibegraph.auth.domain.ProjectSourceType.class),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of());
        service = new TarballImportServiceImpl(new GitHubUrlParser(), preFlightService, tarballClient, properties,
                archiveExtractor, projectService, analyzeService, graphUpdateController, fileChangeBroadcaster,
                backgroundTasks::add, accountSettingsService, projectUsageService,
                currentUser, ownershipRegistrar, featureGateService,
                new ConcurrentImportGuard(new AbuseProperties()), trashService, graphRepository, importCreditBilling,
                ownershipRepository);
        service.setTelemetryRecorder(telemetryRecorder);
        lenient().when(trashService.purgeTrashedGitHubDuplicates(eq(userId), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of());
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

        var token = new OperationTelemetryRecorder.OperationToken("evt-github");
        ProjectResponse result = service.importFromGithub(
                new GithubImportRequest("https://github.com/acme/demo"), token);

        assertThat(result.getStatus()).isEqualTo("ANALYZING");
        // Preflight and download are bounded by the server hard limit (200MB default),
        // not by the account's remaining quota.
        verify(preFlightService).validatePublicRepository(new GitHubRepositoryRef("acme", "demo", null), 209715200L);
        verify(tarballClient).downloadTarball(eq(resolved), any(Path.class), eq(209715200L));
        // Billed upfront by the extracted .java file count.
        verify(importCreditBilling).chargeUpfront(userId, ImportCreditBilling.OPERATION_IMPORT_GITHUB, 1, "p1");
        verify(projectService).markAnalyzing("p1");
        verify(graphUpdateController).broadcastStatus(eq("p1"), eq(ProjectStatus.ANALYZING), eq(0), any(String.class));
        verify(analyzeService, never()).analyzeProjectWithinOperation(any(), any(), any(), any());
        verify(telemetryRecorder).attach(token, "p1", "acme/demo");
        verify(telemetryRecorder, never()).complete(eq(token), anyInt(), anyInt(), anyLong());
        assertThat(backgroundTasks).hasSize(1);

        when(analyzeService.analyzeProjectWithinOperation(eq("p1"), eq("acme/demo"), eq("rp"), any()))
                .thenReturn(new AnalysisResult("p1", 1, 5, 4, 0));
        backgroundTasks.get(0).run();

        verify(projectService).markAnalyzed("p1", 1, 5, 4);
        verify(graphUpdateController).broadcastStatus(eq("p1"), eq(ProjectStatus.ANALYZED), eq(100), any(String.class));
        verify(fileChangeBroadcaster).watchProject("p1", "rp");
        verify(telemetryRecorder).complete(eq(token), eq(5), eq(4), anyLong());
    }

    @Test
    @DisplayName("forwards a caller-selected branch to the pre-flight check")
    void forwardsSelectedBranchToPreflight() throws Exception {
        GitHubRepositoryRef resolved = new GitHubRepositoryRef("acme", "demo", "develop", "sha-1");
        Path extractedRoot = workspaceRoot.resolve("github-branch/source");
        Path javaFile = extractedRoot.resolve("src/App.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, "class App {}");

        when(preFlightService.validatePublicRepository(any(GitHubRepositoryRef.class), anyLong())).thenReturn(resolved);
        when(archiveExtractor.extract(any(Path.class), eq(ArchiveType.TAR_GZ), any(Path.class), anyLong()))
                .thenReturn(new ArchiveExtractionResult(extractedRoot, List.of(javaFile), List.of("src/App.java")));
        ProjectResponse created = ProjectResponse.builder().id("p2").name("acme/demo").rootPath("rp").status("CREATED").build();
        ProjectResponse analyzing = ProjectResponse.builder().id("p2").name("acme/demo").status("ANALYZING").progress(0).build();
        when(projectService.createProjectFromWorkspace("acme/demo", extractedRoot)).thenReturn(created);
        when(projectService.getProject("p2")).thenReturn(analyzing);

        service.importFromGithub(new GithubImportRequest("https://github.com/acme/demo", "develop"));

        verify(preFlightService).validatePublicRepository(new GitHubRepositoryRef("acme", "demo", "develop"),
                209715200L);
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
        when(preFlightService.validatePublicRepository(parsed, 209715200L))
                .thenThrow(new GithubImportException("GitHub repository is private or not found"));

        assertThatThrownBy(() -> service.importFromGithub(new GithubImportRequest("https://github.com/acme/private")))
                .isInstanceOf(GithubImportException.class)
                .hasMessage("GitHub repository is private or not found");

        verify(preFlightService).validatePublicRepository(parsed, 209715200L);
        verify(tarballClient, never()).downloadTarball(any(), any(), anyLong());
        verify(archiveExtractor, never()).extract(any(), any(), any());
        verify(projectService, never()).createProjectFromWorkspace(any(), any());
        verify(graphUpdateController, never()).broadcastStatus(any(), any(ProjectStatus.class), any(Integer.class), any());
        verify(analyzeService, never()).analyzeProjectWithinOperation(any(), any(), any(), any());
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
        verify(tarballClient).downloadTarball(eq(resolved), tarballPath.capture(), eq(209715200L));
        assertThat(Files.exists(tarballPath.getValue().getParent())).isFalse();
    }

    @Test
    @DisplayName("an exhausted credit balance blocks the GitHub import and cleans up the project")
    void insufficientCreditsBlocksGithubImport() throws Exception {
        GitHubRepositoryRef resolved = new GitHubRepositoryRef("acme", "demo", "main");
        Path extractedRoot = workspaceRoot.resolve("github-test/source");
        Path javaFile = extractedRoot.resolve("src/App.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, "class App {}");

        when(preFlightService.validatePublicRepository(any(GitHubRepositoryRef.class), anyLong())).thenReturn(resolved);
        when(archiveExtractor.extract(any(Path.class), eq(ArchiveType.TAR_GZ), any(Path.class), anyLong()))
                .thenReturn(new ArchiveExtractionResult(extractedRoot, List.of(javaFile), List.of("src/App.java")));
        when(projectService.createProjectFromWorkspace("acme/demo", extractedRoot))
                .thenReturn(ProjectResponse.builder().id("p1").name("acme/demo").rootPath("rp").status("CREATED").build());
        doThrow(new InsufficientCreditsException(
                "Insufficient credits to perform this operation. Required: 2, Available: 0", 2L, 0L))
                .when(importCreditBilling).chargeUpfront(userId, ImportCreditBilling.OPERATION_IMPORT_GITHUB, 1, "p1");

        assertThatThrownBy(() -> service.importFromGithub(new GithubImportRequest("https://github.com/acme/demo")))
                .isInstanceOf(InsufficientCreditsException.class);

        verify(projectService).deleteProject("p1");
        verify(projectService, never()).markAnalyzing("p1");
        verify(analyzeService, never()).analyzeProjectWithinOperation(any(), any(), any(), any());
        assertThat(backgroundTasks).isEmpty();
    }

    private com.vibegraph.auth.domain.entity.ProjectOwnership activeGithubRow(String sourceRef,
            com.vibegraph.auth.domain.ProjectOwnershipStatus status) {
        return com.vibegraph.auth.domain.entity.ProjectOwnership.builder()
                .projectId("p1").ownerId(userId).name("acme/demo")
                .sourceType(com.vibegraph.auth.domain.ProjectSourceType.GITHUB)
                .sourceRef(sourceRef).status(status)
                .build();
    }

    @Test
    @DisplayName("re-import of an up-to-date repository is blocked before any download")
    void reimportUpToDateRepository_blocksWithoutDownload() {
        GitHubRepositoryRef resolved = new GitHubRepositoryRef("acme", "demo", "main").withCommitSha("sha-old");
        when(preFlightService.validatePublicRepository(any(GitHubRepositoryRef.class), anyLong())).thenReturn(resolved);
        when(ownershipRepository.findByOwnerIdAndSourceTypeAndNameAndDeletedAtIsNull(
                eq(userId), eq(com.vibegraph.auth.domain.ProjectSourceType.GITHUB), eq("acme/demo")))
                .thenReturn(List.of(activeGithubRow("sha-old", com.vibegraph.auth.domain.ProjectOwnershipStatus.ANALYZED)));

        assertThatThrownBy(() -> service.importFromGithub(new GithubImportRequest("https://github.com/acme/demo")))
                .isInstanceOf(com.vibegraph.common.exception.RepositoryUpToDateException.class)
                .hasMessageContaining("up to date");

        verify(tarballClient, never()).downloadTarball(any(), any(), anyLong());
        verify(projectService, never()).createProjectFromWorkspace(any(), any());
        verify(projectService, never()).markAnalyzing(any());
        assertThat(backgroundTasks).isEmpty();
    }

    @Test
    @DisplayName("re-import while the existing project is still analyzing is blocked")
    void reimportWhileAnalyzing_blocksWithoutDownload() {
        GitHubRepositoryRef resolved = new GitHubRepositoryRef("acme", "demo", "main").withCommitSha("sha-new");
        when(preFlightService.validatePublicRepository(any(GitHubRepositoryRef.class), anyLong())).thenReturn(resolved);
        when(ownershipRepository.findByOwnerIdAndSourceTypeAndNameAndDeletedAtIsNull(
                eq(userId), eq(com.vibegraph.auth.domain.ProjectSourceType.GITHUB), eq("acme/demo")))
                .thenReturn(List.of(activeGithubRow("sha-old", com.vibegraph.auth.domain.ProjectOwnershipStatus.ANALYZING)));

        assertThatThrownBy(() -> service.importFromGithub(new GithubImportRequest("https://github.com/acme/demo")))
                .isInstanceOf(com.vibegraph.common.exception.ProjectRefreshInProgressException.class)
                .hasMessageContaining("still being analyzed");

        verify(tarballClient, never()).downloadTarball(any(), any(), anyLong());
        verify(projectService, never()).createProjectFromWorkspace(any(), any());
        assertThat(backgroundTasks).isEmpty();
    }

    @Test
    @DisplayName("re-import of a changed repository refreshes the existing project instead of duplicating it")
    void reimportChangedRepository_refreshesExistingProjectInPlace() throws Exception {
        GitHubRepositoryRef resolved = new GitHubRepositoryRef("acme", "demo", "main").withCommitSha("sha-new");
        Path existingRoot = tempDir.resolve("existing-root");
        Files.createDirectories(existingRoot);
        Files.writeString(existingRoot.resolve("Old.java"), "class Old {}");

        Path extractedRoot = workspaceRoot.resolve("github-refresh-test/source");
        Path javaFile = extractedRoot.resolve("src/App.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, "class App {}");

        when(preFlightService.validatePublicRepository(any(GitHubRepositoryRef.class), anyLong())).thenReturn(resolved);
        when(ownershipRepository.findByOwnerIdAndSourceTypeAndNameAndDeletedAtIsNull(
                eq(userId), eq(com.vibegraph.auth.domain.ProjectSourceType.GITHUB), eq("acme/demo")))
                .thenReturn(List.of(activeGithubRow("sha-old", com.vibegraph.auth.domain.ProjectOwnershipStatus.ANALYZED)));
        when(archiveExtractor.extract(any(Path.class), eq(ArchiveType.TAR_GZ), any(Path.class), anyLong()))
                .thenReturn(new ArchiveExtractionResult(extractedRoot, List.of(javaFile), List.of("src/App.java")));
        ProjectResponse analyzing = ProjectResponse.builder().id("p1").name("acme/demo")
                .rootPath(existingRoot.toString()).status("ANALYZING").progress(0).build();
        when(projectService.getProject("p1")).thenReturn(analyzing);

        ProjectResponse result = service.importFromGithub(new GithubImportRequest("https://github.com/acme/demo"));

        assertThat(result.getId()).isEqualTo("p1");
        // The existing project is reused — no second project is ever created.
        verify(projectService, never()).createProjectFromWorkspace(any(), any());
        verify(fileChangeBroadcaster).unwatch("p1");
        // Sources were swapped in place: old file gone, new file present.
        assertThat(Files.exists(existingRoot.resolve("Old.java"))).isFalse();
        assertThat(Files.exists(existingRoot.resolve("src/App.java"))).isTrue();
        verify(projectService).markAnalyzing("p1");
        assertThat(backgroundTasks).hasSize(1);

        when(analyzeService.analyzeProjectWithinOperation(
                eq("p1"), eq("acme/demo"), eq(existingRoot.toString()), any()))
                .thenReturn(new AnalysisResult("p1", 1, 5, 4, 0));
        backgroundTasks.get(0).run();

        verify(projectService).markAnalyzed("p1", 1, 5, 4);
        // The stored SHA only advances after a successful re-analysis.
        verify(ownershipRegistrar).updateSourceRef("p1", "sha-new", "main");
        verify(fileChangeBroadcaster).watchProject("p1", existingRoot.toString());
        verify(projectService, never()).deleteProject("p1");
    }

    @Test
    @DisplayName("a failed refresh re-analysis keeps the old stored SHA so the next re-import retries")
    void failedRefresh_keepsOldStoredSha() throws Exception {
        GitHubRepositoryRef resolved = new GitHubRepositoryRef("acme", "demo", "main").withCommitSha("sha-new");
        Path existingRoot = tempDir.resolve("existing-root-2");
        Files.createDirectories(existingRoot);

        Path extractedRoot = workspaceRoot.resolve("github-refresh-fail/source");
        Path javaFile = extractedRoot.resolve("src/App.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, "class App {}");

        when(preFlightService.validatePublicRepository(any(GitHubRepositoryRef.class), anyLong())).thenReturn(resolved);
        when(ownershipRepository.findByOwnerIdAndSourceTypeAndNameAndDeletedAtIsNull(
                eq(userId), eq(com.vibegraph.auth.domain.ProjectSourceType.GITHUB), eq("acme/demo")))
                .thenReturn(List.of(activeGithubRow("sha-old", com.vibegraph.auth.domain.ProjectOwnershipStatus.ANALYZED)));
        when(archiveExtractor.extract(any(Path.class), eq(ArchiveType.TAR_GZ), any(Path.class), anyLong()))
                .thenReturn(new ArchiveExtractionResult(extractedRoot, List.of(javaFile), List.of("src/App.java")));
        when(projectService.getProject("p1")).thenReturn(ProjectResponse.builder().id("p1").name("acme/demo")
                .rootPath(existingRoot.toString()).status("ANALYZING").build());

        var token = new OperationTelemetryRecorder.OperationToken("evt-github-failed");
        service.importFromGithub(new GithubImportRequest("https://github.com/acme/demo"), token);
        var failure = new IllegalStateException("parse exploded");
        when(analyzeService.analyzeProjectWithinOperation(eq("p1"), any(), any(), any()))
                .thenThrow(failure);
        backgroundTasks.get(0).run();

        verify(projectService).markFailed(eq("p1"), any());
        verify(ownershipRegistrar, never()).updateSourceRef(any(), any(), any());
        verify(telemetryRecorder).fail(token, failure);
        // The pre-existing project survives a failed refresh.
        verify(projectService, never()).deleteProject("p1");
    }

    @Test
    @DisplayName("fresh import stores the resolved commit SHA for future re-import detection")
    void freshImportStoresResolvedCommitSha() throws Exception {
        GitHubRepositoryRef resolved = new GitHubRepositoryRef("acme", "demo", "main").withCommitSha("sha-new");
        Path extractedRoot = workspaceRoot.resolve("github-fresh-sha/source");
        Path javaFile = extractedRoot.resolve("src/App.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, "class App {}");

        when(preFlightService.validatePublicRepository(any(GitHubRepositoryRef.class), anyLong())).thenReturn(resolved);
        when(archiveExtractor.extract(any(Path.class), eq(ArchiveType.TAR_GZ), any(Path.class), anyLong()))
                .thenReturn(new ArchiveExtractionResult(extractedRoot, List.of(javaFile), List.of("src/App.java")));
        when(projectService.createProjectFromWorkspace("acme/demo", extractedRoot)).thenReturn(
                ProjectResponse.builder().id("p1").name("acme/demo").rootPath("rp").status("CREATED").build());
        when(projectService.getProject("p1")).thenReturn(
                ProjectResponse.builder().id("p1").name("acme/demo").status("ANALYZING").build());

        service.importFromGithub(new GithubImportRequest("https://github.com/acme/demo"));

        verify(ownershipRegistrar).registerGithub("p1", "acme/demo", "sha-new", "main");
    }
}
