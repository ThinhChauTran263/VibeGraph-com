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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
import com.vibegraph.graph.service.AnalyzeService;
import com.vibegraph.graph.service.AnalyzeService.AnalysisResult;
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
    private AnalyzeService analyzeService;
    private ProjectOwnershipRegistrar ownershipRegistrar;
    private ProjectOwnershipGuard ownershipGuard;
    private ProjectOwnershipQuery ownershipQuery;
    private ProjectDeletionOrchestrator deletionOrchestrator;
    private CurrentUser currentUser;
    private AccountSettingsService accountSettingsService;
    private com.vibegraph.auth.service.FeatureGateService featureGateService;
    private com.vibegraph.auth.service.ProjectUsageService projectUsageService;

    @BeforeEach
    void setUp() {
        projectService = Mockito.mock(ProjectService.class);
        analyzeService = Mockito.mock(AnalyzeService.class);
        ownershipRegistrar = Mockito.mock(ProjectOwnershipRegistrar.class);
        ownershipGuard = Mockito.mock(ProjectOwnershipGuard.class);
        ownershipQuery = Mockito.mock(ProjectOwnershipQuery.class);
        deletionOrchestrator = Mockito.mock(ProjectDeletionOrchestrator.class);
        currentUser = Mockito.mock(CurrentUser.class);
        accountSettingsService = Mockito.mock(AccountSettingsService.class);
        featureGateService = Mockito.mock(com.vibegraph.auth.service.FeatureGateService.class);
        projectUsageService = Mockito.mock(com.vibegraph.auth.service.ProjectUsageService.class);
        ProjectController controller = new ProjectController(
                projectService, analyzeService, ownershipRegistrar, ownershipGuard, ownershipQuery,
                deletionOrchestrator, currentUser, accountSettingsService, featureGateService, projectUsageService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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


        verify(deletionOrchestrator).delete("abc123");
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
    @DisplayName("POST /api/projects/{id}/analyze persists stats via the service contract")
    void shouldPersistStatsThroughInterface() throws Exception {
        UUID userId = UUID.randomUUID();
        ProjectResponse project = ProjectResponse.builder()
                .id("p1").name("p1").rootPath("/tmp/p1").status("CREATED").build();
        when(currentUser.id()).thenReturn(userId);
        when(projectService.getProject("p1")).thenReturn(project);
        when(analyzeService.analyzeProject("p1", "p1", "/tmp/p1"))
                .thenReturn(new AnalysisResult("p1", 3, 10, 7, 0));

        mockMvc.perform(post("/api/projects/p1/analyze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodesUpserted").value(10))
                .andExpect(jsonPath("$.data.edgesUpserted").value(7));

        // The key regression guard for the removed downcast: stats must be pushed
        // through the interface method, which a plain mock honors.
        verify(projectService, times(1)).updateProjectStats("p1", 3, 10, 7);
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
        verify(analyzeService, never()).analyzeProject(any(), any(), any());
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
        verify(analyzeService, never()).analyzeProject(any(), any(), any());
        verify(projectService, never()).updateProjectStats(eq("p1"), any(Integer.class), any(Integer.class), any(Integer.class));
    }

    @Test
    @DisplayName("DELETE /api/projects/{id} guards ownership then orchestrates delete, returning 204")
    void shouldDeleteOwnedProject() throws Exception {
        mockMvc.perform(delete("/api/projects/p1"))
                .andExpect(status().isNoContent());

        verify(ownershipGuard, times(1)).assertOwner("p1");
        verify(deletionOrchestrator, times(1)).delete("p1");
    }

    @Test
    @DisplayName("DELETE /api/projects/{id} returns 403 for a non-owner and does not delete")
    void shouldReturn403OnDeleteWhenNotOwner() throws Exception {
        doThrow(new ForbiddenException("Access denied")).when(ownershipGuard).assertOwner("p1");

        mockMvc.perform(delete("/api/projects/p1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // No data-plane/control-plane delete when ownership is rejected.
        verify(deletionOrchestrator, never()).delete("p1");
    }

    @Test
    @DisplayName("DELETE /api/projects/{id} maps partial deletion to 500 DELETE_PARTIAL_FAILED")
    void shouldReturn500OnPartialDeletion() throws Exception {
        doThrow(new PartialDeletionException("p1", "CONTROL_PLANE", new RuntimeException("db down")))
                .when(deletionOrchestrator).delete("p1");

        mockMvc.perform(delete("/api/projects/p1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("DELETE_PARTIAL_FAILED"));
    }
}
