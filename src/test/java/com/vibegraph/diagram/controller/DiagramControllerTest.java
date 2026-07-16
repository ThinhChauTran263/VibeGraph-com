package com.vibegraph.diagram.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import org.mockito.Mockito;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibegraph.common.exception.ForbiddenException;
import com.vibegraph.common.exception.GlobalExceptionHandler;
import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;
import com.vibegraph.diagram.dto.response.DiagramResponse;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse;
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
    private ProjectOwnershipGuard ownershipGuard;
    private com.vibegraph.auth.service.FeatureGateService featureGateService;

    @BeforeEach
    void setUp() {
        useCaseDiagramService = Mockito.mock(UseCaseDiagramService.class);
        classDiagramService = Mockito.mock(ClassDiagramService.class);
        projectService = Mockito.mock(ProjectService.class);
        ownershipGuard = Mockito.mock(ProjectOwnershipGuard.class);
        featureGateService = Mockito.mock(com.vibegraph.auth.service.FeatureGateService.class);
        DiagramController controller =
                new DiagramController(useCaseDiagramService, classDiagramService, projectService, ownershipGuard,
                        featureGateService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /diagrams/usecase returns 403 when the ownership guard rejects a non-owner")
    void shouldReturn403WhenNotOwner() throws Exception {
        doThrow(new ForbiddenException("Access denied")).when(ownershipGuard).assertOwner("p1");

        mockMvc.perform(get("/api/projects/p1/diagrams/usecase"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // Guard blocks before the project/analysis is consulted.
        verifyNoInteractions(useCaseDiagramService, classDiagramService, projectService);
    }

    private void stubAnalyzed(String projectId) {
        when(projectService.getProject(projectId)).thenReturn(
                ProjectResponse.builder().id(projectId).status(ProjectStatus.ANALYZED.name()).build());
    }

    @Test
    @DisplayName("GET /diagrams/usecase rejects a disabled feature before project reads")
    void useCaseFeatureDisabled() throws Exception {
        doThrow(new com.vibegraph.common.exception.FeatureDisabledException("usecase.generate"))
                .when(featureGateService).assertEnabled("usecase.generate");

        mockMvc.perform(get("/api/projects/p1/diagrams/usecase"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FEATURE_DISABLED"));

        verify(ownershipGuard).assertOwner("p1");
        verifyNoInteractions(useCaseDiagramService, classDiagramService, projectService);
    }

    @Test
    @DisplayName("GET /diagrams/usecase defaults to the UML style (API Map removed)")
    void getUseCaseDiagram() throws Exception {
        stubAnalyzed("p1");
        when(useCaseDiagramService.generateUmlUseCase(eq("p1"), isNull())).thenReturn(
                UmlUseCaseResponse.builder()
                        .diagramType("usecase")
                        .style("uml")
                        .systemName("Shop")
                        .mermaidSyntax("flowchart TB\n    A_User(((User)))")
                        .plantUmlSyntax("@startuml\n@enduml")
                        .build());

        mockMvc.perform(get("/api/projects/p1/diagrams/usecase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.style").value("uml"));

        verify(useCaseDiagramService).generateUmlUseCase(eq("p1"), isNull());
    }

    @Test
    @DisplayName("GET /diagrams/usecase?style=uml routes to the UML inference path with the mode")
    void getUmlUseCaseDiagram() throws Exception {
        stubAnalyzed("p1");
        when(useCaseDiagramService.generateUmlUseCase("p1", "grouped")).thenReturn(
                UmlUseCaseResponse.builder()
                        .diagramType("usecase")
                        .style("uml")
                        .mode("grouped")
                        .systemName("Shop")
                        .mermaidSyntax("flowchart LR\n    subgraph boundary")
                        .plantUmlSyntax("@startuml\n@enduml")
                        .build());

        mockMvc.perform(get("/api/projects/p1/diagrams/usecase")
                        .param("style", "uml").param("mode", "grouped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.style").value("uml"))
                .andExpect(jsonPath("$.data.mode").value("grouped"))
                .andExpect(jsonPath("$.data.plantUmlSyntax").value(org.hamcrest.Matchers.startsWith("@startuml")));

        verify(useCaseDiagramService).generateUmlUseCase("p1", "grouped");
    }

    @Test
    @DisplayName("GET /diagrams/usecase returns 400 for an unsupported style")
    void useCaseInvalidStyle() throws Exception {
        stubAnalyzed("p1");

        mockMvc.perform(get("/api/projects/p1/diagrams/usecase").param("style", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").exists());

        verifyNoInteractions(useCaseDiagramService);
    }

    @Test
    @DisplayName("GET /diagrams/usecase?style=uml returns 400 for an invalid mode")
    void useCaseInvalidMode() throws Exception {
        stubAnalyzed("p1");
        when(useCaseDiagramService.generateUmlUseCase(eq("p1"), eq("bogus")))
                .thenThrow(new IllegalArgumentException("Invalid mode 'bogus'."));

        mockMvc.perform(get("/api/projects/p1/diagrams/usecase")
                        .param("style", "uml").param("mode", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").exists());
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
