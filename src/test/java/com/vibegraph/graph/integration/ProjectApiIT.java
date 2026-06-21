package com.vibegraph.graph.integration;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.common.exception.GlobalExceptionHandler;
import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.controller.ProjectController;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.service.AnalyzeService;
import com.vibegraph.graph.service.AnalyzeService.AnalysisResult;
import com.vibegraph.graph.service.ProjectService;

/**
 * Web-layer tests for the Project API using standalone MockMvc — no Neo4j and no full
 * Spring context (mirrors {@code ProjectControllerTest}/{@code GraphControllerTest}).
 * Verifies the controller-to-service contract, the {@code ApiResponse} envelope, and
 * the exception-to-HTTP mapping via {@link GlobalExceptionHandler}.
 */
@DisplayName("Project API Integration")
class ProjectApiIT {

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
    @DisplayName("Full Project Lifecycle: Create -> List -> Get -> Analyze -> Delete")
    void projectLifecycle() throws Exception {
        ProjectResponse p1 = ProjectResponse.builder()
                .id("p1").name("Project 1").rootPath("/src/p1").status("CREATED").build();
        when(projectService.createProject(any())).thenReturn(p1);

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Project 1\",\"rootPath\":\"/src/p1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("p1"));

        when(projectService.listProjects()).thenReturn(List.of(p1));
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("p1"));

        when(projectService.getProject("p1")).thenReturn(p1);
        mockMvc.perform(get("/api/projects/p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("p1"));

        when(analyzeService.analyzeProject("p1", "Project 1", "/src/p1"))
                .thenReturn(new AnalysisResult("p1", 5, 20, 30, 0));
        mockMvc.perform(post("/api/projects/p1/analyze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodesUpserted").value(20));

        mockMvc.perform(delete("/api/projects/p1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Validation Error: Blank rootPath")
    void validationError() throws Exception {
        // rootPath carries @NotBlank (name does not), so a blank rootPath is the validation trigger.
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Project 1\",\"rootPath\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("404 Error: Unknown project")
    void unknownProject() throws Exception {
        when(projectService.getProject("unknown"))
                .thenThrow(new ProjectNotFoundException("Not found"));

        mockMvc.perform(get("/api/projects/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
    }
}
