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

import com.vibegraph.common.exception.NodeNotFoundException;
import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.ImpactAnalysisResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.mcp.dto.response.ArchitectureContextResponse;
import com.vibegraph.mcp.dto.response.ClassContextResponse;
import com.vibegraph.mcp.dto.response.ImpactAnalysisContextResponse;
import com.vibegraph.mcp.service.ArchitectureAnalyzer;
import com.vibegraph.mcp.service.ClassContextAnalyzer;
import com.vibegraph.mcp.service.ImpactAnalysisAnalyzer;
import com.vibegraph.mcp.service.impl.ArchitectureAnalyzerImpl;
import com.vibegraph.mcp.service.impl.ClassContextAnalyzerImpl;
import com.vibegraph.mcp.service.impl.ImpactAnalysisAnalyzerImpl;

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
            ImpactAnalysisTool impactAnalysisTool = new ImpactAnalysisTool(new ImpactAnalysisAnalyzerImpl(graphService));
            ToolCallbackProvider provider = new McpServerConfig().mcpToolCallbackProvider(
                    architectureTool, classContextTool, impactAnalysisTool);

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
            ImpactAnalysisTool impactAnalysisTool = new ImpactAnalysisTool(new ImpactAnalysisAnalyzerImpl(graphService));
            ToolCallbackProvider provider = new McpServerConfig().mcpToolCallbackProvider(
                    architectureTool, classContextTool, impactAnalysisTool);

            assertThat(provider.getToolCallbacks())
                    .extracting(ToolCallback::getToolDefinition)
                    .extracting(definition -> definition.name())
                    .containsExactly("get_project_architecture", "get_class_context", "get_impact_analysis");
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

        private final GraphService graphService = Mockito.mock(GraphService.class);
        private final ImpactAnalysisAnalyzer impactAnalysisAnalyzer = new ImpactAnalysisAnalyzerImpl(graphService);
        private final ImpactAnalysisTool impactAnalysisTool = new ImpactAnalysisTool(impactAnalysisAnalyzer);

        @Test
        @DisplayName("get_impact_analysis returns direct and transitive impact summary")
        void getImpactAnalysis_existingNode_returnsImpactContext() {
            when(graphService.getImpactAnalysis("p1", "com.app.service.UserService", 3)).thenReturn(impactResponse());

            ImpactAnalysisContextResponse result = impactAnalysisTool.getImpactAnalysis("p1", "com.app.service.UserService", 3);

            assertThat(result.getProjectId()).isEqualTo("p1");
            assertThat(result.getNodeQuery()).isEqualTo("com.app.service.UserService");
            assertThat(result.getDepth()).isEqualTo(3);
            assertThat(result.getSummary().getTargetFullName()).isEqualTo("com.app.service.UserService");
            assertThat(result.getSummary().getDirectDependents()).isEqualTo(2);
            assertThat(result.getSummary().getTotalDependents()).isEqualTo(4);
            assertThat(result.getRiskLevel()).isEqualTo("MEDIUM");
            assertThat(result.getDirectImpact()).extracting(ImpactAnalysisContextResponse.NodeImpact::getFullName)
                    .containsExactly("com.app.api.UserController", "com.app.web.UserPage");
            assertThat(result.getTransitiveImpact()).extracting(ImpactAnalysisContextResponse.NodeImpact::getFullName)
                    .containsExactly("com.app.audit.AuditService", "com.app.job.UserSyncJob");
            assertThat(result.getWarnings()).isEmpty();
            assertThat(result.toString()).doesNotContain("C:/secret/project");
        }

        @Test
        @DisplayName("get_impact_analysis rejects unsupported depth")
        void getImpactAnalysis_unsupportedDepth_throws() {
            assertThatThrownBy(() -> impactAnalysisTool.getImpactAnalysis("p1", "UserService", 4))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("depth");
        }

        @Test
        @DisplayName("get_impact_analysis rejects invalid input")
        void getImpactAnalysis_invalidInput_throws() {
            assertThatThrownBy(() -> impactAnalysisTool.getImpactAnalysis(" ", "UserService", 3))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("projectId");
            assertThatThrownBy(() -> impactAnalysisTool.getImpactAnalysis("p1", "UserService\nInjected", 3))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("printable");
        }

        @Test
        @DisplayName("get_impact_analysis returns clear warning when target is missing")
        void getImpactAnalysis_missingTarget_returnsWarning() {
            when(graphService.getImpactAnalysis("p1", "MissingService", 3)).thenThrow(new NodeNotFoundException("C:/secret/project/missing"));

            ImpactAnalysisContextResponse result = impactAnalysisTool.getImpactAnalysis("p1", "MissingService", 3);

            assertThat(result.getSummary()).isNull();
            assertThat(result.getDirectImpact()).isEmpty();
            assertThat(result.getTransitiveImpact()).isEmpty();
            assertThat(result.getWarnings()).containsExactly("Impact target not found: MissingService");
            assertThat(result.toString()).doesNotContain("C:/secret/project");
        }

        @Test
        @DisplayName("get_impact_analysis propagates project not found signal")
        void getImpactAnalysis_projectNotFound_propagatesSignal() {
            when(graphService.getImpactAnalysis("missing", "UserService", 3)).thenThrow(new ProjectNotFoundException("Project not found: missing"));

            assertThatThrownBy(() -> impactAnalysisTool.getImpactAnalysis("missing", "UserService", 3))
                    .isInstanceOf(ProjectNotFoundException.class)
                    .hasMessageContaining("missing");
        }

        @Test
        @DisplayName("get_impact_analysis returns safe warning when graph service fails")
        void getImpactAnalysis_graphFailure_returnsSafeWarning() {
            when(graphService.getImpactAnalysis("p1", "UserService", 3)).thenThrow(new IllegalStateException("C:/secret/project/db failed"));

            ImpactAnalysisContextResponse result = impactAnalysisTool.getImpactAnalysis("p1", "UserService", 3);

            assertThat(result.getWarnings()).containsExactly("Impact analysis is temporarily unavailable.");
            assertThat(result.toString()).doesNotContain("C:/secret/project");
        }

        @Test
        @DisplayName("get_impact_analysis bounds output and reports truncation")
        void getImpactAnalysis_manyDependents_returnsBoundedOutput() {
            ImpactAnalysisResponse impact = ImpactAnalysisResponse.builder()
                    .target(impactNode("target", "Class", "UserService", "com.app.UserService"))
                    .riskLevel("CRITICAL")
                    .directDependents(75)
                    .totalDependents(180)
                    .willBreak(impactNodes("direct", 75))
                    .likelyAffected(impactNodes("likely", 75))
                    .mayNeedTesting(impactNodes("testing", 75))
                    .build();
            when(graphService.getImpactAnalysis("p1", "com.app.UserService", 5)).thenReturn(impact);

            ImpactAnalysisContextResponse result = impactAnalysisTool.getImpactAnalysis("p1", "com.app.UserService", 5);

            assertThat(result.getDirectImpact()).hasSize(50);
            assertThat(result.getTransitiveImpact()).hasSize(100);
            assertThat(result.getWarnings()).containsExactly(
                    "directImpact truncated to 50 of 75",
                    "transitiveImpact truncated to 100 of 150");
        }

        @Test
        @DisplayName("get_impact_analysis is registered as a Spring AI tool callback")
        void getImpactAnalysis_registeredAsToolCallback() {
            ArchitectureTool architectureTool = new ArchitectureTool(new ArchitectureAnalyzerImpl(graphService));
            ClassContextTool classContextTool = new ClassContextTool(new ClassContextAnalyzerImpl(graphService));
            ToolCallbackProvider provider = new McpServerConfig().mcpToolCallbackProvider(
                    architectureTool, classContextTool, impactAnalysisTool);

            assertThat(provider.getToolCallbacks())
                    .extracting(ToolCallback::getToolDefinition)
                    .extracting(definition -> definition.name())
                    .containsExactly("get_project_architecture", "get_class_context", "get_impact_analysis");
        }

        private ImpactAnalysisResponse impactResponse() {
            return ImpactAnalysisResponse.builder()
                    .target(impactNode("target", "Class", "UserService", "com.app.service.UserService"))
                    .riskLevel("MEDIUM")
                    .directDependents(2)
                    .totalDependents(4)
                    .willBreak(List.of(
                            impactNode("direct-b", "Class", "UserPage", "com.app.web.UserPage"),
                            impactNode("direct-a", "Class", "UserController", "com.app.api.UserController")))
                    .likelyAffected(List.of(
                            impactNode("likely-b", "Class", "UserSyncJob", "com.app.job.UserSyncJob"),
                            impactNode("likely-a", "Class", "AuditService", "com.app.audit.AuditService")))
                    .mayNeedTesting(List.of())
                    .build();
        }

        private List<NodeDto> impactNodes(String prefix, int count) {
            List<NodeDto> nodes = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                nodes.add(impactNode(prefix + "-" + index, "Class", prefix + index, "com.app." + prefix + index));
            }
            return nodes;
        }

        private NodeDto impactNode(String id, String type, String name, String fullName) {
            return NodeDto.builder()
                    .id(id)
                    .type(type)
                    .name(name)
                    .fullName(fullName)
                    .filePath("C:/secret/project/src/main/java/" + name + ".java")
                    .lineNumber(9)
                    .build();
        }
    }
}
