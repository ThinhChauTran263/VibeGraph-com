package com.vibegraph.mcp.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;

import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.graph.service.ProjectService;
import com.vibegraph.mcp.dto.response.EndpointTraceResponse;
import com.vibegraph.mcp.dto.response.MethodSourceContextResponse;
import com.vibegraph.mcp.dto.response.ReferenceSearchResponse;
import com.vibegraph.mcp.dto.response.SourceFileContextResponse;
import com.vibegraph.mcp.dto.response.SourceSearchResponse;
import com.vibegraph.mcp.service.impl.EndpointTraceAnalyzerImpl;
import com.vibegraph.mcp.service.impl.MethodSourceAnalyzerImpl;
import com.vibegraph.mcp.service.impl.ReferenceAnalyzerImpl;
import com.vibegraph.mcp.service.impl.SourceFileAnalyzerImpl;
import com.vibegraph.mcp.service.impl.SourceSearchAnalyzerImpl;
import com.vibegraph.mcp.source.SourceGraphSupport;
import com.vibegraph.mcp.source.impl.SourceFileServiceImpl;

@DisplayName("Source-reading MCP tools")
class SourceToolsTest {

    private static final String PROJECT_ID = "proj-1";

    @TempDir
    Path tempDir;

    private final ProjectService projectService = Mockito.mock(ProjectService.class);
    private final GraphService graphService = Mockito.mock(GraphService.class);

    private Path root;
    private String controllerPath;

    private SourceFileTool sourceFileTool;
    private MethodSourceTool methodSourceTool;
    private SearchSourceTool searchSourceTool;
    private FindReferencesTool findReferencesTool;
    private TraceEndpointTool traceEndpointTool;

    @BeforeEach
    void setUp() throws IOException {
        root = tempDir.toRealPath();
        Path controller = root.resolve("src/main/java/demo/CategoryController.java");
        Files.createDirectories(controller.getParent());
        Files.writeString(controller, """
                package demo;

                public class CategoryController {
                    private final CategoryService categoryService = null;

                    @GetMapping("/api/categories/")
                    public String findAll() {
                        String token=superSecretToken;
                        return categoryService.all();
                    }
                }
                """, StandardCharsets.UTF_8);
        controllerPath = controller.toString();

        when(projectService.getProject(PROJECT_ID)).thenReturn(
                ProjectResponse.builder().id(PROJECT_ID).rootPath(root.toString()).build());
        when(graphService.getFullGraph(PROJECT_ID)).thenReturn(graph());

        SourceFileServiceImpl fileService = new SourceFileServiceImpl(projectService);
        SourceGraphSupport support = new SourceGraphSupport(graphService);
        sourceFileTool = new SourceFileTool(new SourceFileAnalyzerImpl(fileService, support));
        methodSourceTool = new MethodSourceTool(new MethodSourceAnalyzerImpl(fileService, support));
        searchSourceTool = new SearchSourceTool(new SourceSearchAnalyzerImpl(fileService, support));
        findReferencesTool = new FindReferencesTool(new ReferenceAnalyzerImpl(support));
        traceEndpointTool = new TraceEndpointTool(new EndpointTraceAnalyzerImpl(support));
    }

    private GraphDataResponse graph() {
        NodeDto controllerClass = node("class-cc", "Class", "CategoryController", "demo.CategoryController", 3, 11);
        NodeDto findAll = method("m-findall", "findAll", "demo.CategoryController.findAll()", 7, 10, "String");
        NodeDto saveA = method("m-save-a", "save", "demo.CategoryController.save(java.lang.String)", 12, 14, "void");
        NodeDto saveB = method("m-save-b", "save", "demo.service.CategoryService.save(java.lang.String)", 20, 22, "void");
        NodeDto legacy = methodNoEnd("m-legacy", "legacy", "demo.CategoryController.legacy()", 7);
        NodeDto serviceClass = node("class-cs", "Class", "CategoryService", "demo.service.CategoryService", 1, 30);
        NodeDto serviceAll = method("m-svc-all", "all", "demo.service.CategoryService.all()", 24, 26, "String");
        NodeDto endpoint = NodeDto.builder()
                .id("route-cat").type("APIEndpoint").name("GET /api/categories/").fullName("GET /api/categories/")
                .filePath("").lineNumber(6)
                .properties(Map.of("httpMethod", "GET", "routePath", "/api/categories/"))
                .build();

        List<NodeDto> nodes = List.of(
                controllerClass, findAll, saveA, saveB, legacy, serviceClass, serviceAll, endpoint);
        List<EdgeDto> edges = List.of(
                edge("e1", "class-cc", "m-findall", "HAS_METHOD"),
                edge("e2", "m-findall", "route-cat", "HANDLES_ROUTE"),
                edge("e3", "m-findall", "m-svc-all", "CALLS"),
                edge("e4", "m-findall", "m-svc-all", "STEP_IN_FLOW"),
                edge("e5", "class-cc", "class-cs", "IMPORTS"),
                edge("e6", "class-cc", "class-cs", "INJECTS"),
                edge("e7", "m-svc-all", "class-cs", "CALLS"));
        return GraphDataResponse.builder().nodes(nodes).edges(edges).build();
    }

    // --- get_source_file ----------------------------------------------------------------------

    @Test
    @DisplayName("get_source_file resolves a class node to its file with declared symbols")
    void getSourceFile_classNode_returnsContentAndSymbols() {
        SourceFileContextResponse result = sourceFileTool.getSourceFile(PROJECT_ID, "demo.CategoryController", null, null);

        assertThat(result.getRelativePath()).isEqualTo("src/main/java/demo/CategoryController.java");
        assertThat(result.getNodeId()).isEqualTo("class-cc");
        assertThat(result.getContent()).contains("public class CategoryController");
        assertThat(result.getSymbols()).extracting(SourceFileContextResponse.SymbolInfo::getName)
                .contains("CategoryController", "findAll");
        assertThat(result.toString()).doesNotContain(root.toString());
    }

    @Test
    @DisplayName("get_source_file reads a project-relative path when no node matches")
    void getSourceFile_relativePath_returnsContent() {
        SourceFileContextResponse result = sourceFileTool.getSourceFile(
                PROJECT_ID, "src/main/java/demo/CategoryController.java", null, null);

        assertThat(result.getContent()).contains("class CategoryController");
        assertThat(result.getRelativePath()).isEqualTo("src/main/java/demo/CategoryController.java");
    }

    @Test
    @DisplayName("get_source_file redacts secrets and never leaks absolute paths")
    void getSourceFile_redactsSecrets() {
        SourceFileContextResponse result = sourceFileTool.getSourceFile(PROJECT_ID, "demo.CategoryController", null, null);

        assertThat(result.getContent()).contains("[REDACTED]");
        assertThat(result.getContent()).doesNotContain("superSecretToken");
    }

    @Test
    @DisplayName("get_source_file rejects path traversal")
    void getSourceFile_pathTraversal_throws() {
        assertThatThrownBy(() -> sourceFileTool.getSourceFile(PROJECT_ID, "../../../../.env", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- get_method_source --------------------------------------------------------------------

    @Test
    @DisplayName("get_method_source returns the exact method body with metadata and relations")
    void getMethodSource_exactSignature_returnsBody() {
        MethodSourceContextResponse result = methodSourceTool.getMethodSource(
                PROJECT_ID, "demo.CategoryController.findAll()");

        assertThat(result.getResolvedMethod().getId()).isEqualTo("m-findall");
        assertThat(result.getResolvedMethod().getReturnType()).isEqualTo("String");
        assertThat(result.getStartLine()).isEqualTo(7);
        assertThat(result.getEndLine()).isEqualTo(10);
        assertThat(result.getContent()).contains("public String findAll()");
        assertThat(result.getRelated().getCalls()).extracting(MethodSourceContextResponse.NodeRef::getName)
                .contains("all");
        assertThat(result.toString()).doesNotContain(root.toString());
    }

    @Test
    @DisplayName("get_method_source resolves an unambiguous simple method name")
    void getMethodSource_uniqueName_resolves() {
        MethodSourceContextResponse result = methodSourceTool.getMethodSource(PROJECT_ID, "findAll");

        assertThat(result.getResolvedMethod()).isNotNull();
        assertThat(result.getResolvedMethod().getId()).isEqualTo("m-findall");
    }

    @Test
    @DisplayName("get_method_source returns candidates for an ambiguous name")
    void getMethodSource_ambiguous_returnsCandidates() {
        MethodSourceContextResponse result = methodSourceTool.getMethodSource(PROJECT_ID, "save");

        assertThat(result.getResolvedMethod()).isNull();
        assertThat(result.getCandidates()).extracting(MethodSourceContextResponse.Candidate::getId)
                .containsExactlyInAnyOrder("m-save-a", "m-save-b");
        assertThat(result.getWarnings()).isNotEmpty();
    }

    @Test
    @DisplayName("get_method_source warns for a missing method")
    void getMethodSource_missing_warns() {
        MethodSourceContextResponse result = methodSourceTool.getMethodSource(PROJECT_ID, "demo.Nope.ghost()");

        assertThat(result.getResolvedMethod()).isNull();
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("not found"));
    }

    @Test
    @DisplayName("get_method_source falls back to a bounded window when endLine is missing")
    void getMethodSource_missingEndLine_fallbackWindow() {
        MethodSourceContextResponse result = methodSourceTool.getMethodSource(PROJECT_ID, "demo.CategoryController.legacy()");

        assertThat(result.getResolvedMethod().getId()).isEqualTo("m-legacy");
        assertThat(result.getNotes()).anyMatch(n -> n.toLowerCase().contains("window"));
    }

    // --- search_source ------------------------------------------------------------------------

    @Test
    @DisplayName("search_source finds an annotation and maps it to the enclosing method")
    void searchSource_annotation_mapsToNode() {
        SourceSearchResponse result = searchSourceTool.searchSource(PROJECT_ID, "@GetMapping", null, null, null);

        assertThat(result.getMatches()).isNotEmpty();
        SourceSearchResponse.Match match = result.getMatches().get(0);
        assertThat(match.getRelativePath()).isEqualTo("src/main/java/demo/CategoryController.java");
        assertThat(match.getNodeId()).isNotNull();
    }

    @Test
    @DisplayName("search_source treats Cypher-injection-like input as a literal and stays safe")
    void searchSource_injectionLikeInput_safe() {
        SourceSearchResponse result = searchSourceTool.searchSource(
                PROJECT_ID, "X'}) MATCH (n) DETACH DELETE n //", null, null, null);

        assertThat(result.getTotalMatches()).isZero();
        assertThat(result.getMatches()).isEmpty();
    }

    @Test
    @DisplayName("search_source rejects a blank query")
    void searchSource_blankQuery_throws() {
        assertThatThrownBy(() -> searchSourceTool.searchSource(PROJECT_ID, "   ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- find_references ----------------------------------------------------------------------

    @Test
    @DisplayName("find_references returns incoming references to a class")
    void findReferences_class_returnsReferences() {
        ReferenceSearchResponse result = findReferencesTool.findReferences(
                PROJECT_ID, "demo.service.CategoryService", null, "incoming", null);

        assertThat(result.getResolvedSymbol().getId()).isEqualTo("class-cs");
        assertThat(result.getReferences()).extracting(ReferenceSearchResponse.Reference::getRelationshipType)
                .contains("IMPORTS", "INJECTS", "CALLS");
    }

    @Test
    @DisplayName("find_references filters by relationship type")
    void findReferences_filterByType() {
        ReferenceSearchResponse result = findReferencesTool.findReferences(
                PROJECT_ID, "demo.service.CategoryService", List.of("IMPORTS"), "incoming", null);

        assertThat(result.getReferences()).extracting(ReferenceSearchResponse.Reference::getRelationshipType)
                .containsOnly("IMPORTS");
    }

    @Test
    @DisplayName("find_references rejects an invalid relationship type")
    void findReferences_invalidType_throws() {
        assertThatThrownBy(() -> findReferencesTool.findReferences(
                PROJECT_ID, "demo.service.CategoryService", List.of("NOT_A_REAL_EDGE"), "both", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- trace_endpoint -----------------------------------------------------------------------

    @Test
    @DisplayName("trace_endpoint maps a GET route to its handler and downstream flow")
    void traceEndpoint_get_returnsFlow() {
        EndpointTraceResponse result = traceEndpointTool.traceEndpoint(PROJECT_ID, "GET", "/api/categories/", null);

        assertThat(result.getEndpoint().getId()).isEqualTo("route-cat");
        assertThat(result.getHandlerMethod().getId()).isEqualTo("m-findall");
        assertThat(result.getTraceStrategy()).isEqualTo("STEP_IN_FLOW");
        assertThat(result.getFlowSteps()).extracting(EndpointTraceResponse.FlowStep::getNodeId)
                .contains("m-svc-all");
    }

    @Test
    @DisplayName("trace_endpoint warns for an unknown route")
    void traceEndpoint_unknownRoute_warns() {
        EndpointTraceResponse result = traceEndpointTool.traceEndpoint(PROJECT_ID, "GET", "/nope", null);

        assertThat(result.getEndpoint()).isNull();
        assertThat(result.getWarnings()).isNotEmpty();
    }

    @Test
    @DisplayName("trace_endpoint validates the HTTP method and depth")
    void traceEndpoint_invalidInput_throws() {
        assertThatThrownBy(() -> traceEndpointTool.traceEndpoint(PROJECT_ID, "FETCH", "/api/categories/", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> traceEndpointTool.traceEndpoint(PROJECT_ID, "GET", "/api/categories/", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private NodeDto node(String id, String type, String name, String fullName, int line, int endLine) {
        return NodeDto.builder()
                .id(id).type(type).name(name).fullName(fullName)
                .filePath(controllerPath).lineNumber(line)
                .properties(Map.of("endLine", endLine))
                .build();
    }

    private NodeDto method(String id, String name, String fullName, int line, int endLine, String returnType) {
        return NodeDto.builder()
                .id(id).type("Method").name(name).fullName(fullName)
                .filePath(controllerPath).lineNumber(line)
                .properties(Map.of(
                        "endLine", endLine,
                        "returnType", returnType,
                        "visibility", "public",
                        "paramTypes", List.of()))
                .build();
    }

    private NodeDto methodNoEnd(String id, String name, String fullName, int line) {
        return NodeDto.builder()
                .id(id).type("Method").name(name).fullName(fullName)
                .filePath(controllerPath).lineNumber(line)
                .properties(Map.of("returnType", "void", "visibility", "public"))
                .build();
    }

    private EdgeDto edge(String id, String source, String target, String type) {
        return EdgeDto.builder().id(id).source(source).target(target).type(type).confidence(1.0).lineNumber(1).build();
    }
}
