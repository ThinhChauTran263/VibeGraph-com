package com.vibegraph.diagram.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.common.exception.GlobalExceptionHandler;
import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.diagram.dto.response.DiagramResponse;
import com.vibegraph.diagram.dto.response.UseCaseResponse;
import com.vibegraph.diagram.service.ClassDiagramService;
import com.vibegraph.diagram.service.UseCaseDiagramService;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.dto.response.ProjectStatus;
import com.vibegraph.graph.service.ProjectService;

/**
 * Web-layer tests for DiagramController using standalone MockMvc — no Neo4j and no
 * full Spring context (mirrors GraphControllerTest). Covers the usecase/class
 * endpoints, package filtering, and not-found / not-analyzed error mapping.
 */
@DisplayName("DiagramController")
class DiagramControllerTest {

    private MockMvc mockMvc;
    private UseCaseDiagramService useCaseDiagramService;
    private ClassDiagramService classDiagramService;
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        useCaseDiagramService = Mockito.mock(UseCaseDiagramService.class);
        classDiagramService = Mockito.mock(ClassDiagramService.class);
        projectService = Mockito.mock(ProjectService.class);
        DiagramController controller =
                new DiagramController(useCaseDiagramService, classDiagramService, projectService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void stubAnalyzed(String projectId) {
        when(projectService.getProject(projectId)).thenReturn(
                ProjectResponse.builder().id(projectId).status(ProjectStatus.ANALYZED.name()).build());
    }

    @Test
    @DisplayName("GET /diagrams/usecase returns a valid Mermaid flowchart")
    void getUseCaseDiagram() throws Exception {
        stubAnalyzed("p1");
        when(useCaseDiagramService.generateUseCaseDiagram("p1")).thenReturn(
                UseCaseResponse.builder()
                        .actors(List.of("HTTP Client"))
                        .useCases(List.of("GET /api/users"))
                        .mermaidSyntax("flowchart LR\n    actor_HTTP_Client((\"HTTP Client\"))")
                        .build());

        mockMvc.perform(get("/api/projects/p1/diagrams/usecase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.actors[0]").value("HTTP Client"))
                .andExpect(jsonPath("$.data.mermaidSyntax").value(org.hamcrest.Matchers.startsWith("flowchart LR")));
    }

    @Test
    @DisplayName("GET /diagrams/class returns a valid Mermaid classDiagram")
    void getClassDiagram() throws Exception {
        stubAnalyzed("p1");
        when(classDiagramService.generateClassDiagram(eq("p1"), isNull())).thenReturn(
                DiagramResponse.builder()
                        .diagramType("class")
                        .mermaidSyntax("classDiagram\n    class UserService {\n    }")
                        .build());

        mockMvc.perform(get("/api/projects/p1/diagrams/class"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.diagramType").value("class"))
                .andExpect(jsonPath("$.data.mermaidSyntax").value(org.hamcrest.Matchers.startsWith("classDiagram")));
    }

    @Test
    @DisplayName("GET /diagrams/class forwards the package filter to the service")
    void getClassDiagramWithPackageFilter() throws Exception {
        stubAnalyzed("p1");
        when(classDiagramService.generateClassDiagram("p1", "com.app.web")).thenReturn(
                DiagramResponse.builder().diagramType("class").mermaidSyntax("classDiagram").build());

        mockMvc.perform(get("/api/projects/p1/diagrams/class").param("package", "com.app.web"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(classDiagramService).generateClassDiagram("p1", "com.app.web");
    }

    @Test
    @DisplayName("GET /diagrams/class forwards a blank package filter unchanged")
    void getClassDiagramWithBlankPackageFilter() throws Exception {
        stubAnalyzed("p1");
        when(classDiagramService.generateClassDiagram("p1", "")).thenReturn(
                DiagramResponse.builder().diagramType("class").mermaidSyntax("classDiagram").build());

        mockMvc.perform(get("/api/projects/p1/diagrams/class").param("package", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(classDiagramService).generateClassDiagram("p1", "");
    }

    @Test
    @DisplayName("GET /diagrams/usecase returns 404 when the project does not exist")
    void useCaseProjectNotFound() throws Exception {
        when(projectService.getProject("nope"))
                .thenThrow(new ProjectNotFoundException("Project not found: nope"));

        mockMvc.perform(get("/api/projects/nope/diagrams/usecase"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").exists());

        verifyNoInteractions(useCaseDiagramService);
    }

    @Test
    @DisplayName("GET /diagrams/class returns 404 when the project does not exist")
    void classProjectNotFound() throws Exception {
        when(projectService.getProject("nope"))
                .thenThrow(new ProjectNotFoundException("Project not found: nope"));

        mockMvc.perform(get("/api/projects/nope/diagrams/class"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").exists());

        verifyNoInteractions(classDiagramService);
    }

    @Test
    @DisplayName("GET /diagrams/usecase returns 409 when the project is not analyzed")
    void useCaseProjectNotAnalyzed() throws Exception {
        when(projectService.getProject("p2")).thenReturn(
                ProjectResponse.builder().id("p2").status(ProjectStatus.CREATED.name()).build());

        mockMvc.perform(get("/api/projects/p2/diagrams/usecase"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_ANALYZED"))
                .andExpect(jsonPath("$.error.message").exists());

        verifyNoInteractions(useCaseDiagramService);
    }

    @Test
    @DisplayName("GET /diagrams/class returns 409 when the project is still analyzing")
    void classProjectNotAnalyzed() throws Exception {
        when(projectService.getProject("p3")).thenReturn(
                ProjectResponse.builder().id("p3").status(ProjectStatus.ANALYZING.name()).build());

        mockMvc.perform(get("/api/projects/p3/diagrams/class"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_ANALYZED"))
                .andExpect(jsonPath("$.error.message").exists());

        verifyNoInteractions(classDiagramService);
    }
}
