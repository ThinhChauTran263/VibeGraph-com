package com.vibegraph.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import com.vibegraph.common.config.McpServerConfig;

import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.mcp.dto.response.ArchitectureContextResponse;
import com.vibegraph.mcp.service.ArchitectureAnalyzer;
import com.vibegraph.mcp.service.impl.ArchitectureAnalyzerImpl;

@DisplayName("MCP Tools")
class McpToolsTest {

    @Nested
    @DisplayName("ArchitectureTool")
    class ArchitectureToolTest {

        private final GraphService graphService = Mockito.mock(GraphService.class);
        private final ArchitectureAnalyzer architectureAnalyzer = new ArchitectureAnalyzerImpl(graphService);
        private final ArchitectureTool architectureTool = new ArchitectureTool(architectureAnalyzer);

        @Test
        @DisplayName("get_project_architecture returns deterministic layers counts and patterns")
        void getProjectArchitecture_graphWithSpringLayers_returnsArchitectureContext() {
            when(graphService.getFullGraph("p1")).thenReturn(GraphDataResponse.builder()
                    .nodes(List.of(
                            node("service", "Class", "UserService", "com.app.service.UserService", Map.of(
                                    "springLayer", "SERVICE",
                                    "filePath", "C:/secret/project/src/main/java/UserService.java")),
                            node("route", "Route", "GET /api/users", "route:/api/users", Map.of()),
                            node("repository", "Interface", "UserRepository", "com.app.repository.UserRepository", Map.of(
                                    "springLayer", "REPOSITORY")),
                            node("controller", "Class", "UserController", "com.app.controller.UserController", Map.of(
                                    "springLayer", "CONTROLLER"))))
                    .edges(List.of())
                    .build());

            ArchitectureContextResponse result = architectureTool.getProjectArchitecture("p1");

            assertThat(result.getProjectId()).isEqualTo("p1");
            assertThat(result.getSummaryCounts()).containsExactly(
                    Map.entry("Class", 2),
                    Map.entry("Interface", 1),
                    Map.entry("Route", 1));
            assertThat(result.getLayers()).extracting(ArchitectureContextResponse.LayerSummary::getName)
                    .containsExactly("CONTROLLER", "REPOSITORY", "ROUTE", "SERVICE");
            assertThat(result.getLayers()).extracting(ArchitectureContextResponse.LayerSummary::getCount)
                    .containsExactly(1, 1, 1, 1);
            assertThat(result.getPatterns()).containsExactly(
                    Map.entry("apiEndpoints", "1 route nodes"),
                    Map.entry("layeredArchitecture", "CONTROLLER -> SERVICE -> REPOSITORY"),
                    Map.entry("layeredComponents", "3 controller/service/repository components"));
            assertThat(result.getNamingConventions()).containsEntry("CONTROLLER", "*Controller");
            assertThat(result.getWarnings()).isEmpty();
            assertThat(result.toString()).doesNotContain("C:/secret/project");
        }

        @Test
        @DisplayName("get_project_architecture returns warning for empty graph")
        void getProjectArchitecture_emptyGraph_returnsClearWarning() {
            when(graphService.getFullGraph("empty")).thenReturn(GraphDataResponse.builder()
                    .nodes(List.of())
                    .edges(List.of())
                    .build());

            ArchitectureContextResponse result = architectureTool.getProjectArchitecture("empty");

            assertThat(result.getProjectId()).isEqualTo("empty");
            assertThat(result.getSummaryCounts()).isEmpty();
            assertThat(result.getLayers()).isEmpty();
            assertThat(result.getPatterns()).isEmpty();
            assertThat(result.getWarnings()).containsExactly("Graph is empty. Analyze the project before requesting architecture context.");
        }

        @Test
        @DisplayName("get_project_architecture is registered as a Spring AI tool callback")
        void getProjectArchitecture_registeredAsToolCallback() {
            ToolCallbackProvider provider = new McpServerConfig().mcpToolCallbackProvider(architectureTool);

            assertThat(provider.getToolCallbacks())
                    .extracting(ToolCallback::getToolDefinition)
                    .extracting(definition -> definition.name())
                    .containsExactly("get_project_architecture");
        }

        @Test
        @DisplayName("get_project_architecture rejects blank projectId")
        void getProjectArchitecture_blankProjectId_throws() {
            assertThatThrownBy(() -> architectureTool.getProjectArchitecture("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("projectId");
        }

        @Test
        @DisplayName("get_project_architecture propagates project not found signal")
        void getProjectArchitecture_projectNotFound_propagatesSignal() {
            when(graphService.getFullGraph("missing")).thenThrow(new ProjectNotFoundException("Project not found: missing"));

            assertThatThrownBy(() -> architectureTool.getProjectArchitecture("missing"))
                    .isInstanceOf(ProjectNotFoundException.class)
                    .hasMessageContaining("missing");
        }

        private NodeDto node(String id, String type, String name, String fullName, Map<String, Object> properties) {
            return NodeDto.builder()
                    .id(id)
                    .type(type)
                    .name(name)
                    .fullName(fullName)
                    .filePath("C:/secret/project/src/main/java/" + name + ".java")
                    .properties(properties)
                    .build();
        }
    }

    @Nested
    @DisplayName("ClassContextTool")
    class ClassContextToolTest {

        @Test
        @Disabled("Chờ T44 ClassContextTool implement")
        @DisplayName("should return class info with fields and methods")
        void shouldReturnClassInfo() {
        }
    }

    @Nested
    @DisplayName("LayerPatternTool")
    class LayerPatternToolTest {

        @Test
        @Disabled("Chờ T46 LayerPatternTool implement")
        @DisplayName("should return conventions for CONTROLLER layer")
        void shouldReturnControllerConventions() {
        }
    }

    @Nested
    @DisplayName("ImpactAnalysisTool")
    class ImpactAnalysisToolTest {

        @Test
        @Disabled("Chờ T45 ImpactAnalysisTool implement")
        @DisplayName("should delegate to ImpactService")
        void shouldDelegateToImpactService() {
        }
    }
}
