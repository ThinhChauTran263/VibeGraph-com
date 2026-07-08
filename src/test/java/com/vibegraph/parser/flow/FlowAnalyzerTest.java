package com.vibegraph.parser.flow;

import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for FlowAnalyzer — STEP_IN_FLOW inference from CALLS + HANDLES_ROUTE.
 *
 * STEP_IN_FLOW is a reachability-filtered, deduped subset of CALLS starting at
 * route handlers; it is NOT a copy of CALLS.
 */
@DisplayName("FlowAnalyzer (STEP_IN_FLOW)")
class FlowAnalyzerTest {

    private NodeData method(String fqcn) {
        return NodeData.of("Method", fqcn.substring(fqcn.lastIndexOf('.') + 1), fqcn, "", 1);
    }

    private EdgeData calls(String from, String to, int line) {
        return EdgeData.of("CALLS", from, to, Map.of("lineNumber", line));
    }

    private EdgeData handlesRoute(String handler, String routeId) {
        return EdgeData.of("HANDLES_ROUTE", handler, routeId, Map.of());
    }

    private List<EdgeData> steps(List<EdgeData> all) {
        return all.stream().filter(e -> e.type().equals("STEP_IN_FLOW")).toList();
    }

    private boolean hasStep(List<EdgeData> steps, String from, String to) {
        return steps.stream().anyMatch(e -> e.sourceFullName().equals(from) && e.targetFullName().equals(to));
    }

    @Test
    @DisplayName("simple route flow: handler -> service -> repository")
    void simpleRouteFlow() {
        var nodes = List.of(method("a.UserController.list()"), method("a.UserService.findAll()"),
                method("a.UserRepository.findAll()"));
        var edges = List.of(
                handlesRoute("a.UserController.list()", "GET /users"),
                calls("a.UserController.list()", "a.UserService.findAll()", 10),
                calls("a.UserService.findAll()", "a.UserRepository.findAll()", 20));

        var steps = steps(FlowAnalyzer.inferStepInFlow(nodes, edges));

        assertThat(hasStep(steps, "a.UserController.list()", "a.UserService.findAll()")).isTrue();
        assertThat(hasStep(steps, "a.UserService.findAll()", "a.UserRepository.findAll()")).isTrue();
        assertThat(steps).hasSize(2);
        // All endpoints are method nodes (by construction here).
        assertThat(steps).allSatisfy(e -> assertThat(e.type()).isEqualTo("STEP_IN_FLOW"));
    }

    @Test
    @DisplayName("multiple calls are ordered by line number via stepIndex metadata")
    void multipleCallsOrdered() {
        var nodes = List.of(method("a.C.handle()"), method("a.C.validate()"),
                method("a.S.save()"), method("a.C.audit()"));
        var edges = List.of(
                handlesRoute("a.C.handle()", "POST /x"),
                calls("a.C.handle()", "a.C.audit()", 14),
                calls("a.C.handle()", "a.C.validate()", 10),
                calls("a.C.handle()", "a.S.save()", 12));

        var steps = steps(FlowAnalyzer.inferStepInFlow(nodes, edges));

        // Ordered by source line: validate(10) -> save(12) -> audit(14).
        assertThat(stepIndexOf(steps, "a.C.handle()", "a.C.validate()")).isEqualTo(0);
        assertThat(stepIndexOf(steps, "a.C.handle()", "a.S.save()")).isEqualTo(1);
        assertThat(stepIndexOf(steps, "a.C.handle()", "a.C.audit()")).isEqualTo(2);
        // Metadata present and deterministic.
        assertThat(steps.get(0).properties()).containsEntry("sourceKind", "ROUTE_FLOW")
                .containsEntry("flowId", "a.C.handle()");
    }

    @Test
    @DisplayName("external / non-in-project call targets are excluded")
    void externalCallsExcluded() {
        // Only in-project methods are nodes; a CALLS to a non-node target must be dropped.
        var nodes = List.of(method("a.C.handle()"), method("a.S.run()"));
        var edges = List.of(
                handlesRoute("a.C.handle()", "GET /y"),
                calls("a.C.handle()", "a.S.run()", 10),
                calls("a.C.handle()", "java.io.PrintStream.println(java.lang.String)", 11),
                calls("a.S.run()", "java.util.Objects.requireNonNull(java.lang.Object)", 20));

        var steps = steps(FlowAnalyzer.inferStepInFlow(nodes, edges));

        assertThat(hasStep(steps, "a.C.handle()", "a.S.run()")).isTrue();
        assertThat(steps).noneSatisfy(e -> assertThat(e.targetFullName()).contains("PrintStream"));
        assertThat(steps).noneSatisfy(e -> assertThat(e.targetFullName()).contains("Objects"));
        assertThat(steps).hasSize(1);
    }

    @Test
    @DisplayName("branching: both branches included, ordered deterministically by line")
    void branchingIncludesBothDeterministically() {
        var nodes = List.of(method("a.C.handle()"), method("a.S.left()"), method("a.S.right()"));
        var edges = List.of(
                handlesRoute("a.C.handle()", "GET /z"),
                calls("a.C.handle()", "a.S.right()", 12),
                calls("a.C.handle()", "a.S.left()", 11));

        var steps = steps(FlowAnalyzer.inferStepInFlow(nodes, edges));

        assertThat(hasStep(steps, "a.C.handle()", "a.S.left()")).isTrue();
        assertThat(hasStep(steps, "a.C.handle()", "a.S.right()")).isTrue();
        assertThat(stepIndexOf(steps, "a.C.handle()", "a.S.left()")).isEqualTo(0); // line 11 first
        assertThat(stepIndexOf(steps, "a.C.handle()", "a.S.right()")).isEqualTo(1);
    }

    @Test
    @DisplayName("cycle / recursion does not infinite-loop and emits each pair once")
    void cycleGuard() {
        var nodes = List.of(method("a.A.x()"), method("a.B.y()"));
        var edges = List.of(
                handlesRoute("a.A.x()", "GET /c"),
                calls("a.A.x()", "a.B.y()", 10),
                calls("a.B.y()", "a.A.x()", 20)); // back-edge (cycle)

        var steps = steps(FlowAnalyzer.inferStepInFlow(nodes, edges));

        assertThat(hasStep(steps, "a.A.x()", "a.B.y()")).isTrue();
        assertThat(hasStep(steps, "a.B.y()", "a.A.x()")).isTrue();
        assertThat(steps).hasSize(2); // each pair once, no duplication / no hang
    }

    @Test
    @DisplayName("negative: no STEP_IN_FLOW when there is no route handler entrypoint")
    void noEntrypointNoFlow() {
        var nodes = List.of(method("a.S.a()"), method("a.S.b()"));
        var edges = List.of(calls("a.S.a()", "a.S.b()", 10)); // no HANDLES_ROUTE

        var steps = steps(FlowAnalyzer.inferStepInFlow(nodes, edges));

        assertThat(steps).isEmpty();
    }

    @Test
    @DisplayName("STEP_IN_FLOW count is a strict subset of CALLS (not a copy)")
    void stepInFlowIsSubsetNotCopy() {
        var nodes = List.of(method("a.C.handle()"), method("a.S.run()"),
                method("a.Unused.foo()"), method("a.Other.bar()"));
        var calls = List.of(
                calls("a.C.handle()", "a.S.run()", 10), // reachable from route
                calls("a.Unused.foo()", "a.Other.bar()", 99)); // NOT reachable from any route
        var edges = new ArrayList<EdgeData>(calls);
        edges.add(handlesRoute("a.C.handle()", "GET /q"));

        var all = FlowAnalyzer.inferStepInFlow(nodes, edges);
        var steps = steps(all);
        long callsCount = edges.stream().filter(e -> e.type().equals("CALLS")).count();

        assertThat(steps).hasSize(1); // only the reachable step
        assertThat((long) steps.size()).isLessThan(callsCount); // != raw CALLS
        // CALLS edges are untouched by the analyzer (still present, unchanged).
        assertThat(edges.stream().filter(e -> e.type().equals("CALLS")).count()).isEqualTo(2);
    }

    private int stepIndexOf(List<EdgeData> steps, String from, String to) {
        return steps.stream()
                .filter(e -> e.sourceFullName().equals(from) && e.targetFullName().equals(to))
                .findFirst()
                .map(e -> ((Number) e.properties().get("stepIndex")).intValue())
                .orElse(-1);
    }
}
