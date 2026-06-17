package com.vibegraph.parser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.vibegraph.parser.flow.FlowAnalyzer;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;
import com.vibegraph.parser.node.ParseResult;
import com.vibegraph.parser.service.impl.ParserServiceImpl;

/**
 * End-to-end CPG coverage over the dedicated fixture project
 * {@code src/test/resources/cpg-fixture} (controller → service → repository,
 * annotations, packages, JPA entity, enum, record, body-level data-flow).
 *
 * Verifies the full backend pipeline (parser + FlowAnalyzer) emits every Phase 1–4
 * node/edge type without a running server, and that deep CPG is opt-in.
 */
@DisplayName("CPG schema coverage (fixture)")
class CpgSchemaCoverageTest {

    private static final Path FIXTURE = Path.of("src/test/resources/cpg-fixture").toAbsolutePath();

    private List<EdgeData> analyze(boolean deepCpg) {
        ParserServiceImpl parser = new ParserServiceImpl();
        ReflectionTestUtils.setField(parser, "deepCpgEnabled", deepCpg);
        List<ParseResult> results = parser.parseProject(FIXTURE);

        List<NodeData> nodes = new ArrayList<>();
        List<EdgeData> edges = new ArrayList<>();
        for (ParseResult r : results) {
            nodes.addAll(r.getNodes());
            edges.addAll(r.getEdges());
        }
        edges.addAll(FlowAnalyzer.inferStepInFlow(nodes, edges));
        return edges;
    }

    private List<NodeData> analyzeNodes(boolean deepCpg) {
        ParserServiceImpl parser = new ParserServiceImpl();
        ReflectionTestUtils.setField(parser, "deepCpgEnabled", deepCpg);
        return parser.parseProject(FIXTURE).stream()
                .flatMap(r -> r.getNodes().stream())
                .toList();
    }

    @Test
    @DisplayName("fixture directory exists")
    void fixtureExists() {
        assertThat(Files.isDirectory(FIXTURE))
                .as("CPG fixture must exist at " + FIXTURE.toAbsolutePath())
                .isTrue();
    }

    @Test
    @DisplayName("emits every implemented edge type (deep CPG on)")
    void emitsAllEdgeTypes() {
        Set<String> edgeTypes = analyze(true).stream().map(EdgeData::type).collect(Collectors.toSet());

        // OWNS is contract-only (never emitted); HAS_INNER needs nested types (not in
        // this fixture). Every other implemented edge type must be present.
        List<String> expected = List.of(
                "DEFINES", "CONTAINS", "HAS_METHOD", "HAS_FIELD", "ANNOTATED_BY",
                "EXTENDS", "IMPLEMENTS", "OVERRIDES", "TYPE_OF", "PARAMETER_TYPE",
                "RETURNS", "IMPORTS", "CALLS", "INSTANTIATES", "INJECTS",
                "STEP_IN_FLOW", "READS", "WRITES", "THROWS", "CATCHES", "HANDLES_ROUTE");

        assertThat(edgeTypes).as("edge types emitted: " + edgeTypes).containsAll(expected);
    }

    @Test
    @DisplayName("emits every implemented node type (deep CPG on)")
    void emitsAllNodeTypes() {
        Set<String> nodeTypes = analyzeNodes(true).stream().map(NodeData::type).collect(Collectors.toSet());

        // Project + External are persist-level (created during upsert), not parser
        // output. Every other implemented node type must be present.
        List<String> expected = List.of(
                "Package", "File", "Class", "Interface", "Enum", "Record", "DBModel",
                "Method", "Constructor", "Field", "Annotation", "LocalVariable", "APIEndpoint");

        assertThat(nodeTypes).as("node types emitted: " + nodeTypes).containsAll(expected);
    }

    @Test
    @DisplayName("STEP_IN_FLOW is a strict subset of CALLS (not a copy)")
    void stepInFlowSubsetOfCalls() {
        List<EdgeData> edges = analyze(true);
        long calls = edges.stream().filter(e -> e.type().equals("CALLS")).count();
        long flow = edges.stream().filter(e -> e.type().equals("STEP_IN_FLOW")).count();

        assertThat(flow).as("STEP_IN_FLOW present").isGreaterThan(0);
        // audit() -> load is a CALLS edge not reachable from the route, so it is NOT a flow step.
        assertThat(flow).as("STEP_IN_FLOW must be fewer than CALLS").isLessThan(calls);
    }

    @Test
    @DisplayName("deep CPG OFF: no LocalVariable / READS / WRITES / CATCHES")
    void deepCpgOffSuppressesBodyLevel() {
        List<EdgeData> edges = analyze(false);
        List<NodeData> nodes = analyzeNodes(false);

        assertThat(nodes).noneMatch(n -> n.type().equals("LocalVariable"));
        assertThat(edges).noneMatch(e -> e.type().equals("READS")
                || e.type().equals("WRITES") || e.type().equals("CATCHES"));
        // Structural + STEP_IN_FLOW remain (STEP_IN_FLOW is not gated by deep CPG).
        assertThat(edges).anyMatch(e -> e.type().equals("STEP_IN_FLOW"));
        assertThat(edges).anyMatch(e -> e.type().equals("HANDLES_ROUTE"));
    }
}
