package com.vibegraph.parser.flow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

/**
 * Infers {@code STEP_IN_FLOW} edges — an ordered, high-level execution-flow view
 * derived from the already-resolved {@code CALLS} graph, starting at route handler
 * methods ({@code HANDLES_ROUTE} sources).
 *
 * <p><b>STEP_IN_FLOW is NOT a copy of CALLS.</b> It is a reachability-filtered,
 * de-duplicated subset:
 * <ul>
 *   <li>Only steps reachable from a route handler entrypoint are included.</li>
 *   <li>Only in-project method/constructor targets participate (library/JDK/Spring
 *       calls are never in {@code CALLS} to begin with, so they are excluded).</li>
 *   <li>At most one edge per {@code (from,to)} pair (deduped), unlike multiple
 *       call sites.</li>
 * </ul>
 *
 * <p><b>Direction:</b> caller step → callee next step
 * ({@code (:Method|:Constructor)-[:STEP_IN_FLOW]->(:Method|:Constructor)}).
 *
 * <p><b>Ordering / metadata:</b> calls inside a method are ordered by line number
 * (then target name) to approximate source sequence; each edge carries
 * {@code flowId}, {@code entrypoint}, {@code stepIndex}, {@code sourceKind},
 * {@code confidence}, {@code lineNumber} as properties. NOTE: the graph edge model
 * MERGEs on {@code (from)-[type]->(to)} (no per-step key), so when the same call
 * participates in several flows only one edge survives; its metadata reflects the
 * first flow that reached it (entrypoints processed in sorted order → deterministic).
 *
 * <p><b>Branches/loops/recursion:</b> all resolvable in-project calls in every
 * branch are included (deterministically ordered by line); each method is visited
 * at most once per flow, which guards cycles/recursion against infinite traversal.
 */
public final class FlowAnalyzer {

    private static final String EDGE_TYPE = "STEP_IN_FLOW";
    private static final String SOURCE_KIND = "ROUTE_FLOW";
    private static final double FLOW_CONFIDENCE = 0.9;

    private FlowAnalyzer() {
    }

    private record Call(String target, int line) {
    }

    public static List<EdgeData> inferStepInFlow(List<NodeData> nodes, List<EdgeData> edges) {
        if (nodes == null || edges == null) {
            return List.of();
        }

        Set<String> methodIds = new LinkedHashSet<>();
        for (NodeData node : nodes) {
            if ("Method".equals(node.type()) || "Constructor".equals(node.type())) {
                methodIds.add(node.fullName());
            }
        }

        // In-project call adjacency, ordered deterministically per method.
        Map<String, List<Call>> callAdjacency = new LinkedHashMap<>();
        for (EdgeData edge : edges) {
            if (!"CALLS".equals(edge.type())) {
                continue;
            }
            String source = edge.sourceFullName();
            String target = edge.targetFullName();
            if (!methodIds.contains(source) || !methodIds.contains(target)) {
                continue;
            }
            callAdjacency.computeIfAbsent(source, key -> new ArrayList<>())
                    .add(new Call(target, lineOf(edge)));
        }
        for (List<Call> calls : callAdjacency.values()) {
            calls.sort(Comparator.comparingInt(Call::line).thenComparing(Call::target));
        }

        // Entrypoints: route handler methods (HANDLES_ROUTE sources), deterministic order.
        List<String> entrypoints = edges.stream()
                .filter(edge -> "HANDLES_ROUTE".equals(edge.type()))
                .map(EdgeData::sourceFullName)
                .filter(methodIds::contains)
                .distinct()
                .sorted()
                .toList();

        List<EdgeData> result = new ArrayList<>();
        Set<String> emittedPairs = new HashSet<>();

        for (String entrypoint : entrypoints) {
            traverse(entrypoint, entrypoint, callAdjacency, new LinkedHashSet<>(), emittedPairs,
                    result, new int[] {0});
        }
        return result;
    }

    private static void traverse(String entrypoint, String current, Map<String, List<Call>> callAdjacency,
                                 Set<String> flowVisited, Set<String> emittedPairs,
                                 List<EdgeData> result, int[] step) {
        // Visit each method at most once per flow — guards cycles/recursion and bounds work.
        if (!flowVisited.add(current)) {
            return;
        }
        for (Call call : callAdjacency.getOrDefault(current, List.of())) {
            String pairKey = current + "\u0000" + call.target();
            if (emittedPairs.add(pairKey)) {
                Map<String, Object> props = new LinkedHashMap<>();
                props.put("flowId", entrypoint);
                props.put("entrypoint", entrypoint);
                props.put("stepIndex", step[0]++);
                props.put("sourceKind", SOURCE_KIND);
                props.put("confidence", FLOW_CONFIDENCE);
                props.put("lineNumber", call.line());
                result.add(EdgeData.of(EDGE_TYPE, current, call.target(), props));
            }
            traverse(entrypoint, call.target(), callAdjacency, flowVisited, emittedPairs, result, step);
        }
    }

    private static int lineOf(EdgeData edge) {
        Object value = edge.properties() == null ? null : edge.properties().get("lineNumber");
        return value instanceof Number number ? number.intValue() : 0;
    }
}
