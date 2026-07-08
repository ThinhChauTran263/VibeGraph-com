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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.ImpactAnalysisResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.graph.service.ProjectService;
import com.vibegraph.mcp.dto.response.CodeChangePlanResponse;
import com.vibegraph.mcp.dto.response.FailureExplanationResponse;
import com.vibegraph.mcp.dto.response.MethodCpgContextResponse;
import com.vibegraph.mcp.dto.response.RelatedTestsResponse;
import com.vibegraph.mcp.dto.response.TestPlanResponse;
import com.vibegraph.mcp.service.impl.CodeChangePlannerImpl;
import com.vibegraph.mcp.service.impl.FailureExplainerImpl;
import com.vibegraph.mcp.service.impl.MethodCpgAnalyzerImpl;
import com.vibegraph.mcp.service.impl.SourceSearchAnalyzerImpl;
import com.vibegraph.mcp.service.impl.TestIntelligenceAnalyzerImpl;
import com.vibegraph.mcp.source.SourceGraphSupport;
import com.vibegraph.mcp.source.impl.SourceFileServiceImpl;

@DisplayName("Senior MCP intelligence tools")
class SeniorMcpToolsTest {

    private static final String PROJECT = "proj-1";

    @TempDir
    Path tempDir;

    private final ProjectService projectService = mock(ProjectService.class);
    private final GraphService graphService = mock(GraphService.class);

    private Path root;
    private String mainPath;
    private String testPath;

    private MethodCpgTool methodCpgTool;
    private FindRelatedTestsTool findRelatedTestsTool;
    private SuggestTestPlanTool suggestTestPlanTool;
    private PlanCodeChangeTool planCodeChangeTool;
    private ExplainFailureTool explainFailureTool;

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
                    public List<Category> findAll() {
                        try {
                            return categoryService.all();
                        } catch (RuntimeException e) {
                            throw e;
                        }
                    }
                }
                """, StandardCharsets.UTF_8);
        mainPath = controller.toString();

        Path test = root.resolve("src/test/java/demo/CategoryControllerTest.java");
        Files.createDirectories(test.getParent());
        Files.writeString(test, """
                package demo;

                class CategoryControllerTest {
                    void findAll_returnsList() {
                        new CategoryController().findAll();
                    }
                }
                """, StandardCharsets.UTF_8);
        testPath = test.toString();

        lenient().when(projectService.getProject(PROJECT)).thenReturn(
                ProjectResponse.builder().id(PROJECT).rootPath(root.toString()).build());
        lenient().when(graphService.getFullGraph(PROJECT)).thenReturn(graph());

        SourceFileServiceImpl fileService = new SourceFileServiceImpl(projectService);
        SourceGraphSupport support = new SourceGraphSupport(graphService);
        SourceSearchAnalyzerImpl searchAnalyzer = new SourceSearchAnalyzerImpl(fileService, support);
        TestIntelligenceAnalyzerImpl testIntel = new TestIntelligenceAnalyzerImpl(support);

        methodCpgTool = new MethodCpgTool(new MethodCpgAnalyzerImpl(support, fileService));
        findRelatedTestsTool = new FindRelatedTestsTool(testIntel);
        suggestTestPlanTool = new SuggestTestPlanTool(testIntel);
        planCodeChangeTool = new PlanCodeChangeTool(
                new CodeChangePlannerImpl(support, searchAnalyzer, testIntel, graphService, fileService));
        explainFailureTool = new ExplainFailureTool(new FailureExplainerImpl(support, fileService));
    }

    private GraphDataResponse graph() {
        NodeDto controller = clazz("class-cc", "CategoryController", "demo.CategoryController", mainPath, 3, 14);
        NodeDto findAll = method("m-findall", "findAll", "demo.CategoryController.findAll()", mainPath, 7, 13);
        NodeDto field = node("f-svc", "Field", "categoryService", "demo.CategoryController.categoryService", mainPath, 4);
        NodeDto service = clazz("class-cs", "CategoryService", "demo.service.CategoryService", mainPath, 1, 30);
        NodeDto svcAll = method("m-all", "all", "demo.service.CategoryService.all()", mainPath, 5, 8);
        NodeDto svcAll2 = method("m-all2", "all2", "demo.service.CategoryService.all2()", mainPath, 9, 12);
        NodeDto dbModel = node("db-cat", "DBModel", "Category", "demo.model.Category", mainPath, 1);
        NodeDto ex = node("ex-rte", "Class", "RuntimeException", "java.lang.RuntimeException", "", 0);
        NodeDto testClass = clazz("class-cct", "CategoryControllerTest", "demo.CategoryControllerTest", testPath, 3, 7);
        NodeDto testMethod = method("m-test", "findAll_returnsList", "demo.CategoryControllerTest.findAll_returnsList()", testPath, 4, 6);

        List<NodeDto> nodes = List.of(controller, findAll, field, service, svcAll, svcAll2, dbModel, ex, testClass, testMethod);
        List<EdgeDto> edges = List.of(
                edge("e1", "class-cc", "m-findall", "HAS_METHOD"),
                edge("e2", "class-cc", "f-svc", "HAS_FIELD"),
                edge("e3", "m-findall", "m-all", "CALLS"),
                edge("e13", "m-findall", "m-all2", "CALLS"),
                flowEdge("e4", "m-findall", "m-all", 9, 0.82),
                edge("e5", "m-findall", "f-svc", "READS"),
                edge("e6", "m-findall", "f-svc", "WRITES"),
                edge("e7", "m-findall", "ex-rte", "CATCHES"),
                edge("e8", "m-findall", "db-cat", "RETURNS"),
                edge("e9", "f-svc", "class-cs", "TYPE_OF"),
                edge("e10", "class-cct", "class-cc", "IMPORTS"),
                edge("e11", "class-cct", "m-test", "HAS_METHOD"),
                edge("e12", "m-test", "m-findall", "CALLS"));
        return GraphDataResponse.builder().nodes(nodes).edges(edges).build();
    }

    // ---- get_method_cpg_context --------------------------------------------------------------

    @Test
    @DisplayName("get_method_cpg_context groups reads/writes/calls/flow/catches/types with source")
    void methodCpg_full() {
        MethodCpgContextResponse r = methodCpgTool.getMethodCpgContext(
                PROJECT, null, null, null, "demo.CategoryController.findAll()", true, null, "full");

        assertThat(r.getResolvedMethod().getId()).isEqualTo("m-findall");
        assertThat(r.getResolvedMethod().getRelativePath()).isEqualTo("src/main/java/demo/CategoryController.java");
        assertThat(r.getDataFlow().getReads()).extracting(MethodCpgContextResponse.NodeRef::getName).contains("categoryService");
        assertThat(r.getDataFlow().getWrites()).extracting(MethodCpgContextResponse.NodeRef::getName).contains("categoryService");
        assertThat(r.getDataFlow().getTypeLinks()).extracting(MethodCpgContextResponse.NodeRef::getName).contains("Category");
        assertThat(r.getControlFlow().getCalls()).extracting(MethodCpgContextResponse.NodeRef::getName).contains("all");
        assertThat(r.getControlFlow().getFlowSteps()).hasSize(1);
        assertThat(r.getControlFlow().getFlowSteps().get(0).getConfidence()).isEqualTo(0.82);
        assertThat(r.getControlFlow().getCatches()).extracting(MethodCpgContextResponse.NodeRef::getName).contains("RuntimeException");
        assertThat(r.getSource().getContent()).contains("findAll");
        assertThat(r.toString()).doesNotContain(root.toString());
    }

    @Test
    @DisplayName("get_method_cpg_context summary profile keeps counts but omits detail lists")
    void methodCpg_summary() {
        MethodCpgContextResponse r = methodCpgTool.getMethodCpgContext(
                PROJECT, null, null, null, "demo.CategoryController.findAll()", false, null, "summary");

        assertThat(r.getDataFlow()).isNull();
        assertThat(r.getControlFlow()).isNull();
        assertThat(r.getCounts()).containsEntry("calls", 2).containsEntry("reads", 1);
    }

    @Test
    @DisplayName("get_method_cpg_context maxRelations cap sets truncated")
    void methodCpg_truncates() {
        MethodCpgContextResponse r = methodCpgTool.getMethodCpgContext(
                PROJECT, "m-findall", null, null, null, false, 1, "full");

        assertThat(r.getControlFlow().getCalls()).hasSize(1);
        assertThat(r.getCounts()).containsEntry("calls", 2);
        assertThat(r.isTruncated()).isTrue();
    }

    @Test
    @DisplayName("get_method_cpg_context resolves an unambiguous simple method name")
    void methodCpg_uniqueName() {
        MethodCpgContextResponse r = methodCpgTool.getMethodCpgContext(
                PROJECT, null, null, null, "findAll", false, null, "full");
        assertThat(r.getResolvedMethod()).isNotNull();
        assertThat(r.getResolvedMethod().getId()).isEqualTo("m-findall");
    }

    // ---- find_related_tests ------------------------------------------------------------------

    @Test
    @DisplayName("find_related_tests finds the graph-referencing test and emits OS-aware commands")
    void relatedTests() {
        RelatedTestsResponse r = findRelatedTestsTool.findRelatedTests(
                PROJECT, null, "demo.CategoryController", null, null, null, null);

        assertThat(r.getMatches()).extracting(RelatedTestsResponse.TestMatch::getName).contains("CategoryControllerTest");
        assertThat(r.getMatches()).anyMatch(m -> "GRAPH_REFERENCE".equals(m.getMatchType()));
        assertThat(r.getSuggestedCommands().getWindows()).contains("CategoryControllerTest");
        assertThat(r.getSuggestedCommands().getUnix()).contains("CategoryControllerTest");
    }

    @Test
    @DisplayName("find_related_tests reports a gap and suggested name when no test exists")
    void relatedTests_gap() {
        RelatedTestsResponse r = findRelatedTestsTool.findRelatedTests(
                PROJECT, null, "demo.service.CategoryService", null, null, null, null);

        assertThat(r.getMatches()).isEmpty();
        assertThat(r.getGaps()).anyMatch(g -> g.contains("CategoryServiceTest"));
    }

    // ---- suggest_test_plan -------------------------------------------------------------------

    @Test
    @DisplayName("suggest_test_plan adds integration for persistence and frontend for vue")
    void testPlan_levels() {
        TestPlanResponse persistence = suggestTestPlanTool.suggestTestPlan(
                PROJECT, "Change the Neo4j repository persistence for categories", null, null, "low");
        assertThat(persistence.getRecommendedLevels()).extracting(TestPlanResponse.TestLevel::getLevel)
                .anyMatch(l -> l.contains("integration"));

        TestPlanResponse frontend = suggestTestPlanTool.suggestTestPlan(
                PROJECT, "Update the Vue component layout", null, List.of("vibegraph-web/src/App.vue"), "low");
        assertThat(frontend.getRecommendedLevels()).extracting(TestPlanResponse.TestLevel::getLevel)
                .anyMatch(l -> l.contains("frontend"));
        assertThat(frontend.getNotCovered()).isNotEmpty();
    }

    @Test
    @DisplayName("suggest_test_plan rejects an invalid risk tolerance")
    void testPlan_invalidRisk() {
        assertThatThrownBy(() -> suggestTestPlanTool.suggestTestPlan(PROJECT, "change x", null, null, "extreme"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- plan_code_change --------------------------------------------------------------------

    @Test
    @DisplayName("plan_code_change identifies the controller file/symbol and a test plan")
    void planChange() {
        when(graphService.getImpactAnalysis(eq(PROJECT), eq("demo.CategoryController"), anyInt()))
                .thenReturn(ImpactAnalysisResponse.builder()
                        .directDependents(1).totalDependents(2).riskLevel("LOW").build());

        CodeChangePlanResponse r = planCodeChangeTool.planCodeChange(
                PROJECT, "Add caching to CategoryController findAll endpoint", null, null, null, true);

        assertThat(r.getCandidateFiles()).extracting(CodeChangePlanResponse.CandidateFile::getRelativePath)
                .anyMatch(p -> p.contains("CategoryController.java"));
        assertThat(r.getCandidateSymbols()).extracting(CodeChangePlanResponse.CandidateSymbol::getName)
                .contains("CategoryController");
        assertThat(r.getTestPlan()).isNotEmpty();
        assertThat(r.getProposedSteps()).isNotEmpty();
        assertThat(r.getConfidence()).isIn("HIGH", "MEDIUM");
        assertThat(r.toString()).doesNotContain(root.toString());
    }

    @Test
    @DisplayName("plan_code_change returns low confidence and a question for an unrelated request")
    void planChange_unrelated() {
        CodeChangePlanResponse r = planCodeChangeTool.planCodeChange(
                PROJECT, "Configure quantum teleportation flux capacitor", null, null, null, false);

        assertThat(r.getConfidence()).isEqualTo("LOW");
        assertThat(r.getOpenQuestions()).isNotEmpty();
    }

    // ---- explain_failure_path ----------------------------------------------------------------

    @Test
    @DisplayName("explain_failure_path maps in-project frames to methods and source")
    void explainFailure_stack() {
        String stack = """
                java.lang.RuntimeException: boom
                    at demo.CategoryController.findAll(CategoryController.java:9)
                    at jdk.internal.reflect.Method.invoke(Method.java:568)
                """;
        FailureExplanationResponse r = explainFailureTool.explainFailurePath(
                PROJECT, stack, null, null, null, true, null);

        assertThat(r.getProjectFrameCount()).isEqualTo(1);
        FailureExplanationResponse.Frame frame = r.getProjectFrames().get(0);
        assertThat(frame.getDeclaringClass()).isEqualTo("demo.CategoryController");
        assertThat(frame.getRelativePath()).isEqualTo("src/main/java/demo/CategoryController.java");
        assertThat(frame.getCalls()).contains("all");
        assertThat(frame.getSource().getContent()).contains("categoryService");
        assertThat(r.getLikelyRootCauses()).isNotEmpty();
        assertThat(r.toString()).doesNotContain(root.toString());
    }

    @Test
    @DisplayName("explain_failure_path reports external-only traces without inventing")
    void explainFailure_external() {
        String stack = """
                at jdk.internal.reflect.Method.invoke(Method.java:568)
                at org.springframework.web.Foo.bar(Foo.java:10)
                """;
        FailureExplanationResponse r = explainFailureTool.explainFailurePath(
                PROJECT, stack, null, null, null, false, null);

        assertThat(r.getProjectFrameCount()).isZero();
        assertThat(r.getWarnings()).anyMatch(w -> w.toLowerCase().contains("external"));
    }

    @Test
    @DisplayName("explain_failure_path maps a test name to its production targets")
    void explainFailure_testName() {
        FailureExplanationResponse r = explainFailureTool.explainFailurePath(
                PROJECT, null, "CategoryControllerTest", null, null, false, null);

        assertThat(r.getTestTarget()).isNotNull();
        assertThat(r.getTestTarget().getProductionTargets()).contains("demo.CategoryController");
    }

    @Test
    @DisplayName("explain_failure_path handles a malformed trace gracefully")
    void explainFailure_malformed() {
        FailureExplanationResponse r = explainFailureTool.explainFailurePath(
                PROJECT, "this is not a stack trace at all", null, null, null, false, null);

        assertThat(r.getProjectFrameCount()).isZero();
        assertThat(r.getWarnings()).isNotEmpty();
    }

    // ---- fixture helpers ---------------------------------------------------------------------

    private NodeDto clazz(String id, String name, String fullName, String filePath, int line, int endLine) {
        return NodeDto.builder().id(id).type("Class").name(name).fullName(fullName)
                .filePath(filePath).lineNumber(line).properties(Map.of("endLine", endLine)).build();
    }

    private NodeDto method(String id, String name, String fullName, String filePath, int line, int endLine) {
        return NodeDto.builder().id(id).type("Method").name(name).fullName(fullName)
                .filePath(filePath).lineNumber(line)
                .properties(Map.of("endLine", endLine, "returnType", "List<Category>", "visibility", "public",
                        "paramTypes", List.of(), "paramNames", List.of()))
                .build();
    }

    private NodeDto node(String id, String type, String name, String fullName, String filePath, int line) {
        return NodeDto.builder().id(id).type(type).name(name).fullName(fullName)
                .filePath(filePath).lineNumber(line).properties(Map.of()).build();
    }

    private EdgeDto edge(String id, String source, String target, String type) {
        return EdgeDto.builder().id(id).source(source).target(target).type(type).confidence(1.0).lineNumber(1).build();
    }

    private EdgeDto flowEdge(String id, String source, String target, int line, double confidence) {
        return EdgeDto.builder().id(id).source(source).target(target).type("STEP_IN_FLOW")
                .confidence(confidence).lineNumber(line).build();
    }
}
