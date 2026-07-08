package com.vibegraph.graph.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mockito;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.common.exception.GlobalExceptionHandler;
import com.vibegraph.common.exception.ProjectNotFoundException;
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

    @BeforeEach
    void setUp() {
        projectService = Mockito.mock(ProjectService.class);
        analyzeService = Mockito.mock(AnalyzeService.class);
        ProjectController controller = new ProjectController(projectService, analyzeService);
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
    @DisplayName("POST /api/projects/{id}/analyze persists stats via the service contract")
    void shouldPersistStatsThroughInterface() throws Exception {
        ProjectResponse project = ProjectResponse.builder()
                .id("p1").name("p1").rootPath("/tmp/p1").status("CREATED").build();
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
}
