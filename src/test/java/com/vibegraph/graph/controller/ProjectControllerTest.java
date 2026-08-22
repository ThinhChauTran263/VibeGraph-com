package com.vibegraph.graph.controller;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import org.mockito.Mockito;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.hamcrest.Matchers.containsString;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.common.exception.AccountBlockedException;
import com.vibegraph.common.exception.ForbiddenException;
import com.vibegraph.common.exception.GlobalExceptionHandler;
import com.vibegraph.common.exception.PartialDeletionException;
import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.common.ownership.ProjectDeletionOrchestrator;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;
import com.vibegraph.common.ownership.ProjectOwnershipQuery;
import com.vibegraph.common.ownership.ProjectOwnershipRegistrar;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.dto.response.CliRepositorySetupResponse;
import com.vibegraph.auth.dto.ApiKeyCreateResponse;
import com.vibegraph.auth.dto.ProjectBindingResponse;
import com.vibegraph.graph.service.CliRepositoryService;
import com.vibegraph.graph.service.ProjectAnalysisScheduler;
import com.vibegraph.graph.service.ProjectService;

/**
 * Web-layer tests for ProjectController using standalone MockMvc — no Neo4j and no
 * full Spring context. Verifies validation, 404 mapping, and that analysis stats
 * are persisted through the ProjectService contract (no impl downcast).
 *
 * Run: mvn test -Dtest=ProjectControllerTest
 */
@DisplayName("ProjectController")
class ProjectControllerTest {

    private MockMvc mockMvc;
    private ProjectService projectService;
    private ProjectAnalysisScheduler projectAnalysisScheduler;
    private ProjectOwnershipRegistrar ownershipRegistrar;
    private ProjectOwnershipGuard ownershipGuard;
    private ProjectOwnershipQuery ownershipQuery;
    private ProjectDeletionOrchestrator deletionOrchestrator;
    private com.vibegraph.common.ownership.ProjectTrashService trashService;
    private CurrentUser currentUser;
    private AccountSettingsService accountSettingsService;
    private com.vibegraph.auth.service.FeatureGateService featureGateService;
    private com.vibegraph.auth.service.ProjectUsageService projectUsageService;
    private CliRepositoryService cliRepositoryService;
    private com.vibegraph.auth.service.CreditPricingService creditPricingService;
    private com.vibegraph.auth.service.CreditBalanceService creditBalanceService;

    @BeforeEach
    void setUp() {
        projectService = Mockito.mock(ProjectService.class);
        projectAnalysisScheduler = Mockito.mock(ProjectAnalysisScheduler.class);
        ownershipRegistrar = Mockito.mock(ProjectOwnershipRegistrar.class);
        ownershipGuard = Mockito.mock(ProjectOwnershipGuard.class);
        ownershipQuery = Mockito.mock(ProjectOwnershipQuery.class);
        deletionOrchestrator = Mockito.mock(ProjectDeletionOrchestrator.class);
        trashService = Mockito.mock(com.vibegraph.common.ownership.ProjectTrashService.class);
        currentUser = Mockito.mock(CurrentUser.class);
        accountSettingsService = Mockito.mock(AccountSettingsService.class);
        featureGateService = Mockito.mock(com.vibegraph.auth.service.FeatureGateService.class);
        projectUsageService = Mockito.mock(com.vibegraph.auth.service.ProjectUsageService.class);
        cliRepositoryService = Mockito.mock(CliRepositoryService.class);
        creditPricingService = Mockito.mock(com.vibegraph.auth.service.CreditPricingService.class);
        creditBalanceService = Mockito.mock(com.vibegraph.auth.service.CreditBalanceService.class);
        ProjectController controller = new ProjectController(
                projectService, projectAnalysisScheduler, ownershipRegistrar, ownershipGuard, ownershipQuery,
                deletionOrchestrator, trashService, currentUser, accountSettingsService, featureGateService,
                projectUsageService, cliRepositoryService, creditPricingService, creditBalanceService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/projects/cli-setup returns one-time key and no-store cache policy")
    void shouldCreateCliRepositorySetup() throws Exception {
        ProjectResponse project = ProjectResponse.builder()
                .id("cli123").name("CLI Repo").rootPath("/tmp/cli/source").status("CREATED").build();
        ApiKeyCreateResponse apiKey = new ApiKeyCreateResponse(
                UUID.randomUUID(),
                "vbg_abcd1234",
                "CLI Repo CLI",
                "vbg_fullsecret",
                new ProjectBindingResponse("cli123", "CLI Repo", "LOCAL", "ANALYZING"),
                java.time.Instant.now(),
                null);
        when(cliRepositoryService.create(any())).thenReturn(new CliRepositorySetupResponse(
                project,
                apiKey,
                List.of("vibegraph login", "vibegraph push", "vibegraph watch")));

        mockMvc.perform(post("/api/projects/cli-setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CLI Repo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.project.id").value("cli123"))
                .andExpect(jsonPath("$.data.apiKey.secretKey").value("vbg_fullsecret"))
                .andExpect(jsonPath("$.data.commands[1]").value("vibegraph push"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    @DisplayName("POST /api/projects should create project and return its data")
    void shouldCreateProject() throws Exception {
        ProjectResponse created = ProjectResponse.builder()
                .id("abc123").name("test").rootPath("/tmp/test").status("CREATED").build();
        when(projectService.createProject(any())).thenReturn(created);

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\",\"rootPath\":\"/tmp/test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("test"));

        // Ownership is recorded for the created project.
        verify(ownershipRegistrar, times(1)).registerLocal("abc123", "test");
        verify(projectUsageService).recordImport(eq("abc123"), isNull(), eq(0L));
    }

    @Test
    @DisplayName("POST /api/projects cleans up when quota tracking initialization fails")
    void shouldCleanUpWhenUsageInitializationFails() throws Exception {
        ProjectResponse created = ProjectResponse.builder()
                .id("abc123").name("test").rootPath("/tmp/test").status("CREATED").build();
        when(projectService.createProject(any())).thenReturn(created);
        doThrow(new IllegalStateException("usage unavailable"))
                .when(projectUsageService).recordImport(eq("abc123"), isNull(), eq(0L));

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\",\"rootPath\":\"/tmp/test\"}"))
                .andExpect(status().isConflict());


        // A create that never completed is rolled back outright, not parked in the user's trash.
        verify(deletionOrchestrator).purge("abc123");
    }

    @Test
    @DisplayName("POST /api/projects with blank rootPath should return 400")
    void shouldReject400ForBlankRootPath() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\",\"rootPath\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // Validation fails before the handler body runs, so no ownership row is created.
        verify(ownershipRegistrar, never()).registerLocal(any(), any());
    }

    @Test
    @DisplayName("GET /api/projects returns only projects owned by the current user")
    void shouldListOnlyOwnedProjects() throws Exception {
        ProjectResponse mine = ProjectResponse.builder().id("mine").name("Mine").status("ANALYZED").build();
        ProjectResponse theirs = ProjectResponse.builder().id("theirs").name("Theirs").status("ANALYZED").build();
        when(projectService.listProjects()).thenReturn(List.of(mine, theirs));
        when(ownershipQuery.ownedProjectIds()).thenReturn(List.of("mine"));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("mine"));
    }

    @Test
    @DisplayName("GET /api/projects/{id} should return 404 for unknown id")
    void shouldReturn404ForUnknownProject() throws Exception {
        when(projectService.getProject(eq("nope")))
                .thenThrow(new ProjectNotFoundException("Project not found: nope"));

        mockMvc.perform(get("/api/projects/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/projects/{id} returns 403 when the ownership guard rejects a non-owner")
    void shouldReturn403WhenNotOwner() throws Exception {
        doThrow(new ForbiddenException("Access denied")).when(ownershipGuard).assertOwner("p1");

        mockMvc.perform(get("/api/projects/p1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // Guard blocks before the service is consulted.
        verify(projectService, never()).getProject("p1");
    }

    @Test
    @DisplayName("POST /api/projects/{id}/analyze accepts immediately and schedules background analysis (H8)")
    void shouldAcceptAndScheduleBackgroundAnalysis() throws Exception {
        UUID userId = UUID.randomUUID();
        ProjectResponse project = ProjectResponse.builder()
                .id("p1").name("p1").rootPath("/tmp/p1").status("CREATED").build();
        when(currentUser.id()).thenReturn(userId);
        when(projectService.getProject("p1")).thenReturn(project);

        mockMvc.perform(post("/api/projects/p1/analyze"))
                .andExpect(status().isAccepted());

        // Existence is checked on the request thread; the heavy work is queued, not run inline.
        verify(projectService, times(1)).getProject("p1");
        // Billing is pre-charged on the request thread via the flat per-file rule
        // (missing root degrades to the base charge).
        verify(creditPricingService).calculateCredits("PROJECT_ANALYZE", 0, 0);
        verify(creditBalanceService).deductCredits(userId, 0L, "WEB", "PROJECT_ANALYZE", "p1");
        verify(projectAnalysisScheduler, times(1)).schedule("p1");
    }

    @Test
    @DisplayName("POST /api/projects/{id}/analyze returns 402 and skips scheduling when credits run out")
    void shouldRejectAnalyzeWhenCreditsExhausted() throws Exception {
        UUID userId = UUID.randomUUID();
        ProjectResponse project = ProjectResponse.builder()
                .id("p1").name("p1").rootPath("/tmp/p1").status("CREATED").build();
        when(currentUser.id()).thenReturn(userId);
        when(projectService.getProject("p1")).thenReturn(project);
        Mockito.doThrow(new com.vibegraph.common.exception.InsufficientCreditsException(
                        "Insufficient credits to perform this operation. Required: 2, Available: 0", 2L, 0L))
                .when(creditBalanceService).assertCreditsAvailable(userId, 0L);

        mockMvc.perform(post("/api/projects/p1/analyze"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error.code").value("CREDIT_EXHAUSTED"))
                .andExpect(jsonPath("$.error.details").value("Required: 2 credits, Available: 0 credits"));

        verify(projectAnalysisScheduler, never()).schedule("p1");
    }

    @Test
    @DisplayName("POST /api/projects/{id}/analyze rejects a disabled feature before metering or analysis")
    void shouldRejectDisabledAnalyzeBeforeWork() throws Exception {
        doThrow(new com.vibegraph.common.exception.FeatureDisabledException("project.analyze"))
                .when(featureGateService).assertEnabled("project.analyze");

        mockMvc.perform(post("/api/projects/p1/analyze"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FEATURE_DISABLED"));

        verify(ownershipGuard).assertOwner("p1");
        verify(currentUser, never()).id();
        verify(projectService, never()).getProject("p1");
        verify(projectAnalysisScheduler, never()).schedule("p1");
    }

    @Test
    @DisplayName("POST /api/projects/{id}/analyze rejects a blocked account before analysis work")
    void shouldRejectBlockedAccountBeforeAnalyzeWork() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUser.id()).thenReturn(userId);
        doThrow(new AccountBlockedException("internal risk note", "Policy review"))
                .when(accountSettingsService).assertNotBlocked(userId);

        mockMvc.perform(post("/api/projects/p1/analyze"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_BLOCKED"))
                .andExpect(jsonPath("$.error.message").value("Policy review"));

        verify(ownershipGuard).assertOwner("p1");
        verify(projectService, never()).getProject("p1");
        verify(projectAnalysisScheduler, never()).schedule("p1");
    }

    @Test
    @DisplayName("DELETE /api/projects/{id} guards ownership then moves the project to trash, returning 204")
    void shouldDeleteOwnedProject() throws Exception {
        mockMvc.perform(delete("/api/projects/p1"))
                .andExpect(status().isNoContent());

        verify(ownershipGuard, times(1)).assertOwner("p1");
        verify(deletionOrchestrator, times(1)).moveToTrash("p1");
        // The public delete is reversible for the whole retention window.
        verify(deletionOrchestrator, never()).purge("p1");
    }

    @Test
    @DisplayName("DELETE /api/projects/{id} returns 403 for a non-owner and does not delete")
    void shouldReturn403OnDeleteWhenNotOwner() throws Exception {
        doThrow(new ForbiddenException("Access denied")).when(ownershipGuard).assertOwner("p1");

        mockMvc.perform(delete("/api/projects/p1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // Nothing is trashed when ownership is rejected.
        verify(deletionOrchestrator, never()).moveToTrash("p1");
    }

    @Test
    @DisplayName("DELETE /api/projects/{id}/purge maps partial deletion to 500 DELETE_PARTIAL_FAILED")
    void shouldReturn500OnPartialDeletion() throws Exception {
        doThrow(new PartialDeletionException("p1", "CONTROL_PLANE", new RuntimeException("db down")))
                .when(trashService).purge("p1");

        mockMvc.perform(delete("/api/projects/p1/purge"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("DELETE_PARTIAL_FAILED"));
    }

    @Test
    @DisplayName("GET /api/projects/trash lists the caller's trashed projects with the countdown")
    void shouldListTrashedProjects() throws Exception {
        java.time.Instant deletedAt = java.time.Instant.parse("2026-08-09T10:00:00Z");
        when(trashService.listTrash()).thenReturn(java.util.List.of(
                new com.vibegraph.graph.dto.TrashedProjectResponse(
                        "p1", "acme/widgets", "GITHUB", 2048L,
                        deletedAt, deletedAt.plus(java.time.Duration.ofDays(3)), 2L)));

        mockMvc.perform(get("/api/projects/trash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value("p1"))
                .andExpect(jsonPath("$.data[0].name").value("acme/widgets"))
                .andExpect(jsonPath("$.data[0].daysRemaining").value(2));
    }

    @Test
    @DisplayName("POST /api/projects/{id}/restore brings a trashed project back, returning 204")
    void shouldRestoreTrashedProject() throws Exception {
        mockMvc.perform(post("/api/projects/p1/restore"))
                .andExpect(status().isNoContent());

        verify(trashService, times(1)).restore("p1");
    }

    @Test
    @DisplayName("POST /api/projects/{id}/restore returns 404 when the project is not in the caller's trash")
    void shouldReturn404WhenRestoringForeignProject() throws Exception {
        doThrow(new com.vibegraph.common.exception.ProjectNotFoundException("Trashed project not found: p1"))
                .when(trashService).restore("p1");

        mockMvc.perform(post("/api/projects/p1/restore"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/projects/{id}/purge permanently deletes a trashed project, returning 204")
    void shouldPurgeTrashedProject() throws Exception {
        mockMvc.perform(delete("/api/projects/p1/purge"))
                .andExpect(status().isNoContent());

        verify(trashService, times(1)).purge("p1");
    }
}
