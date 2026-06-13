package com.vibegraph.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
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
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.mcp.dto.response.ArchitectureContextResponse;
import com.vibegraph.mcp.dto.response.ClassContextResponse;
import com.vibegraph.mcp.service.ArchitectureAnalyzer;
import com.vibegraph.mcp.service.ClassContextAnalyzer;
import com.vibegraph.mcp.service.impl.ArchitectureAnalyzerImpl;
import com.vibegraph.mcp.service.impl.ClassContextAnalyzerImpl;

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
            ClassContextTool classContextTool = new ClassContextTool(new ClassContextAnalyzerImpl(graphService));
            ToolCallbackProvider provider = new McpServerConfig().mcpToolCallbackProvider(architectureTool, classContextTool);

            assertThat(provider.getToolCallbacks())
                    .extracting(ToolCallback::getToolDefinition)
                    .extracting(definition -> definition.name())
                    .contains("get_project_architecture");
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

        private final GraphService graphService = Mockito.mock(GraphService.class);
        private final ClassContextAnalyzer classContextAnalyzer = new ClassContextAnalyzerImpl(graphService);
        private final ClassContextTool classContextTool = new ClassContextTool(classContextAnalyzer);

        @Test
        @DisplayName("get_class_context resolves class by full name with members and relations")
        void getClassContext_fullName_returnsClassContext() {
            when(graphService.getFullGraph("p1")).thenReturn(classGraph());

            ClassContextResponse result = classContextTool.getClassContext("p1", "com.app.service.UserService");

            assertThat(result.getProjectId()).isEqualTo("p1");
            assertThat(result.getQuery()).isEqualTo("com.app.service.UserService");
            assertThat(result.getClassInfo().getId()).isEqualTo("class-user-service");
            assertThat(result.getClassInfo().getLayer()).isEqualTo("SERVICE");
            assertThat(result.getMethods()).extracting(ClassContextResponse.MemberInfo::getName)
                    .containsExactly("createUser", "findUser");
            assertThat(result.getFields()).extracting(ClassContextResponse.MemberInfo::getName)
                    .containsExactly("repository");
            assertThat(result.getIncomingRelations()).extracting(ClassContextResponse.RelationInfo::getType)
                    .containsExactly("CALLS");
            assertThat(result.getOutgoingRelations()).extracting(ClassContextResponse.RelationInfo::getType)
                    .containsExactly("CALLS", "HAS_FIELD", "HAS_METHOD", "HAS_METHOD");
            assertThat(result.getWarnings()).isEmpty();
            assertThat(result.toString()).doesNotContain("C:/secret/project");
        }

        @Test
        @DisplayName("get_class_context resolves class by simple name deterministically")
        void getClassContext_simpleName_returnsFirstDeterministicMatch() {
            when(graphService.getFullGraph("p1")).thenReturn(classGraph());

            ClassContextResponse result = classContextTool.getClassContext("p1", "UserService");

            assertThat(result.getClassInfo().getFullName()).isEqualTo("com.app.duplicate.UserService");
            assertThat(result.getMethods()).isEmpty();
            assertThat(result.getOutgoingRelations()).isEmpty();
        }

        @Test
        @DisplayName("get_class_context resolves class by node id")
        void getClassContext_nodeId_returnsClassContext() {
            when(graphService.getFullGraph("p1")).thenReturn(classGraph());

            ClassContextResponse result = classContextTool.getClassContext("p1", "class-user-service");

            assertThat(result.getClassInfo().getFullName()).isEqualTo("com.app.service.UserService");
        }

        @Test
        @DisplayName("get_class_context returns clear warning when class is missing")
        void getClassContext_missingClass_returnsWarning() {
            when(graphService.getFullGraph("p1")).thenReturn(classGraph());

            ClassContextResponse result = classContextTool.getClassContext("p1", "MissingService");

            assertThat(result.getClassInfo()).isNull();
            assertThat(result.getMethods()).isEmpty();
            assertThat(result.getFields()).isEmpty();
            assertThat(result.getIncomingRelations()).isEmpty();
            assertThat(result.getOutgoingRelations()).isEmpty();
            assertThat(result.getWarnings()).containsExactly("Class not found: MissingService");
            assertThat(result.toString()).doesNotContain("C:/secret/project");
        }

        @Test
        @DisplayName("get_class_context rejects blank projectId")
        void getClassContext_blankProjectId_throws() {
            assertThatThrownBy(() -> classContextTool.getClassContext(" ", "UserService"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("projectId");
        }

        @Test
        @DisplayName("get_class_context rejects blank class query")
        void getClassContext_blankClassQuery_throws() {
            assertThatThrownBy(() -> classContextTool.getClassContext("p1", " "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("classQuery");
        }

        @Test
        @DisplayName("get_class_context rejects control characters")
        void getClassContext_controlCharacter_throws() {
            assertThatThrownBy(() -> classContextTool.getClassContext("p1", "UserService\nInjected"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("printable");
        }

        @Test
        @DisplayName("get_class_context returns safe warning when graph is too large")
        void getClassContext_largeGraph_returnsSafeWarning() {
            when(graphService.getFullGraph("p1")).thenReturn(largeGraph());

            ClassContextResponse result = classContextTool.getClassContext("p1", "UserService");

            assertThat(result.getClassInfo()).isNull();
            assertThat(result.getWarnings()).containsExactly("Graph is too large for class context: 10001 nodes, 0 edges.");
        }

        @Test
        @DisplayName("get_class_context returns safe warning when graph cannot be loaded")
        void getClassContext_graphFailure_returnsSafeWarning() {
            when(graphService.getFullGraph("p1")).thenThrow(new IllegalStateException("C:/secret/project/db failed"));

            ClassContextResponse result = classContextTool.getClassContext("p1", "UserService");

            assertThat(result.getWarnings()).containsExactly("Class context is temporarily unavailable.");
            assertThat(result.toString()).doesNotContain("C:/secret/project");
        }

        @Test
        @DisplayName("get_class_context is registered as a Spring AI tool callback")
        void getClassContext_registeredAsToolCallback() {
            ArchitectureTool architectureTool = new ArchitectureTool(new ArchitectureAnalyzerImpl(graphService));
            ToolCallbackProvider provider = new McpServerConfig().mcpToolCallbackProvider(architectureTool, classContextTool);

            assertThat(provider.getToolCallbacks())
                    .extracting(ToolCallback::getToolDefinition)
                    .extracting(definition -> definition.name())
                    .containsExactly("get_project_architecture", "get_class_context");
        }

        private GraphDataResponse classGraph() {
            return GraphDataResponse.builder()
                    .nodes(List.of(
                            classNode("class-user-service", "UserService", "com.app.service.UserService", "SERVICE"),
                            classNode("class-duplicate-user-service", "UserService", "com.app.duplicate.UserService", "SERVICE"),
                            classNode("class-user-controller", "UserController", "com.app.controller.UserController", "CONTROLLER"),
                            node("method-find", "Method", "findUser", "com.app.service.UserService.findUser", Map.of(
                                    "signature", "findUser(String id)",
                                    "visibility", "public")),
                            node("method-create", "Method", "createUser", "com.app.service.UserService.createUser", Map.of(
                                    "signature", "createUser(CreateUserRequest request)",
                                    "visibility", "public")),
                            node("field-repository", "Field", "repository", "com.app.service.UserService.repository", Map.of(
                                    "visibility", "private")),
                            classNode("class-user-repository", "UserRepository", "com.app.repository.UserRepository", "REPOSITORY")))
                    .edges(List.of(
                            edge("e4", "class-user-service", "method-find", "HAS_METHOD"),
                            edge("e3", "class-user-service", "method-create", "HAS_METHOD"),
                            edge("e2", "class-user-service", "field-repository", "HAS_FIELD"),
                            edge("e5", "class-user-controller", "class-user-service", "CALLS"),
                            edge("e1", "class-user-service", "class-user-repository", "CALLS")))
                    .build();
        }

        private GraphDataResponse largeGraph() {
            List<NodeDto> nodes = new ArrayList<>();
            for (int index = 0; index < 10_001; index++) {
                nodes.add(classNode("class-" + index, "UserService" + index, "com.app.UserService" + index, "SERVICE"));
            }
            return GraphDataResponse.builder()
                    .nodes(nodes)
                    .edges(List.of())
                    .build();
        }

        private NodeDto classNode(String id, String name, String fullName, String layer) {
            return node(id, "Class", name, fullName, Map.of("springLayer", layer));
        }

        private NodeDto node(String id, String type, String name, String fullName, Map<String, Object> properties) {
            return NodeDto.builder()
                    .id(id)
                    .type(type)
                    .name(name)
                    .fullName(fullName)
                    .filePath("C:/secret/project/src/main/java/" + name + ".java")
                    .lineNumber(7)
                    .properties(properties)
                    .build();
        }

        private EdgeDto edge(String id, String source, String target, String type) {
            return EdgeDto.builder()
                    .id(id)
                    .source(source)
                    .target(target)
                    .type(type)
                    .confidence(1.0)
                    .lineNumber(12)
                    .build();
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
