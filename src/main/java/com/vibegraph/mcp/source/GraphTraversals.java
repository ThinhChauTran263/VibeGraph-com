package com.vibegraph.mcp.source;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.NodeDto;

/**
 * Java-aware graph walks shared by the impact / change-verification MCP tools.
 * All walks are bounded (depth + result caps) and read-only against a {@link GraphView}.
 */
public final class GraphTraversals {

    private static final Set<String> CALL_EDGES = Set.of("CALLS", "STEP_IN_FLOW");
    private static final Set<String> HIERARCHY_EDGES = Set.of("EXTENDS", "IMPLEMENTS");
    private static final Set<String> CLASSLIKE = Set.of("Class", "Interface", "Enum", "Record");
    private static final Set<String> METHODLIKE = Set.of("Method", "Constructor");

    private GraphTraversals() {
    }

    /** A route reachable (via reverse CALLS/STEP_IN_FLOW) from a changed/impacted symbol. */
    public record RouteRef(String httpMethod, String routePath, String handlerFullName) {
    }

    /** A bounded symbol in the reverse reachable set of a changed seed. */
    public record ReachableSymbol(String id, String type, String fullName, int depth) {
    }

    /**
     * Returns only the symbols reachable from the changed seeds through impact edges. The walk
     * is deterministic and bounded, so callers can serialize a minimal diff context instead of
     * sending the entire project graph to an agent.
     */
    public static List<ReachableSymbol> reachableSymbols(
            GraphView graph, Set<String> seedIds, int maxDepth, int maxSymbols) {
        if (graph == null || seedIds == null || seedIds.isEmpty() || maxDepth < 0 || maxSymbols <= 0) {
            return List.of();
        }
        Map<String, Integer> depthOf = new LinkedHashMap<>();
        Deque<String> frontier = new ArrayDeque<>();
        for (String seed : seedIds) {
            if (seed != null && graph.byId(seed) != null && !depthOf.containsKey(seed)) {
                depthOf.put(seed, 0);
                frontier.add(seed);
            }
        }
        while (!frontier.isEmpty() && depthOf.size() < maxSymbols) {
            String current = frontier.removeFirst();
            int depth = depthOf.get(current);
            if (depth >= maxDepth) {
                continue;
            }
            for (EdgeDto edge : graph.incoming(current)) {
                if (!CALL_EDGES.contains(edge.getType()) && !HIERARCHY_EDGES.contains(edge.getType())
                        && !"INJECTS".equals(edge.getType()) && !"IMPORTS".equals(edge.getType())) {
                    continue;
                }
                if (!depthOf.containsKey(edge.getSource())) {
                    depthOf.put(edge.getSource(), depth + 1);
                    frontier.addLast(edge.getSource());
                    if (depthOf.size() >= maxSymbols) {
                        break;
                    }
                }
            }
        }
        return depthOf.entrySet().stream()
                .map(entry -> {
                    NodeDto node = graph.byId(entry.getKey());
                    return new ReachableSymbol(node.getId(), node.getType(), node.getFullName(), entry.getValue());
                })
                .toList();
    }

    /**
     * Routes whose handler methods can reach any of the seed symbols — i.e. the API surface
     * an agent would break by changing those symbols. Reverse-BFS over incoming
     * CALLS/STEP_IN_FLOW edges from the seeds; every visited node's outgoing HANDLES_ROUTE
     * edges contribute a route.
     */
    public static List<RouteRef> affectedRoutes(GraphView graph, Set<String> seedIds, int maxDepth, int maxRoutes) {
        if (graph == null || seedIds == null || seedIds.isEmpty()) {
            return List.of();
        }
        Map<String, RouteRef> routes = new LinkedHashMap<>();
        Set<String> visited = new LinkedHashSet<>();
        Map<String, Integer> depthOf = new HashMap<>();
        Deque<String> frontier = new ArrayDeque<>();
        for (String seed : seedIds) {
            if (seed != null && visited.add(seed)) {
                depthOf.put(seed, 0);
                frontier.add(seed);
            }
        }
        while (!frontier.isEmpty() && routes.size() < maxRoutes) {
            String currentId = frontier.poll();
            collectRoutesOf(graph, currentId, routes, maxRoutes);
            int depth = depthOf.getOrDefault(currentId, 0);
            if (depth >= maxDepth) {
                continue;
            }
            for (EdgeDto edge : graph.incoming(currentId)) {
                if (CALL_EDGES.contains(edge.getType()) && visited.add(edge.getSource())) {
                    depthOf.put(edge.getSource(), depth + 1);
                    frontier.add(edge.getSource());
                }
            }
        }
        return List.copyOf(routes.values());
    }

    private static void collectRoutesOf(GraphView graph, String nodeId, Map<String, RouteRef> routes, int maxRoutes) {
        for (EdgeDto edge : graph.outgoing(nodeId)) {
            if (!"HANDLES_ROUTE".equals(edge.getType()) || routes.size() >= maxRoutes) {
                continue;
            }
            NodeDto route = graph.byId(edge.getTarget());
            if (route == null) {
                continue;
            }
            NodeDto handler = graph.byId(nodeId);
            routes.putIfAbsent(route.getId(), new RouteRef(
                    SourceGraphSupport.stringProperty(route, "httpMethod"),
                    SourceGraphSupport.stringProperty(route, "routePath"),
                    handler == null ? null : handler.getFullName()));
        }
    }

    /**
     * Interface/override counterparts of a method: same-named methods declared on the owner's
     * supertypes (interface/abstract declarations the target implements) AND on subtypes
     * (implementations overriding the target). Changing the target's signature will break these
     * too — the classic blind spot of a plain caller walk.
     */
    public static List<String> overrideCounterparts(GraphView graph, NodeDto method, int max) {
        if (graph == null || method == null || method.getFullName() == null
                || !METHODLIKE.contains(method.getType())) {
            return List.of();
        }
        String bare = GraphView.stripParens(method.getFullName());
        int lastDot = bare.lastIndexOf('.');
        if (lastDot <= 0) {
            return List.of();
        }
        NodeDto owner = graph.byFullName(bare.substring(0, lastDot));
        if (owner == null || !CLASSLIKE.contains(owner.getType())) {
            return List.of();
        }
        Set<String> counterparts = new LinkedHashSet<>();
        // Upward: supertypes the owner extends/implements.
        for (EdgeDto edge : graph.outgoing(owner.getId())) {
            if (HIERARCHY_EDGES.contains(edge.getType())) {
                addMatchingMethods(graph, edge.getTarget(), method, counterparts, max);
            }
        }
        // Downward: subtypes extending/implementing the owner.
        for (EdgeDto edge : graph.incoming(owner.getId())) {
            if (HIERARCHY_EDGES.contains(edge.getType())) {
                addMatchingMethods(graph, edge.getSource(), method, counterparts, max);
            }
        }
        counterparts.remove(method.getFullName());
        return counterparts.stream().limit(max).toList();
    }

    private static void addMatchingMethods(GraphView graph, String typeId, NodeDto target,
            Set<String> out, int max) {
        NodeDto type = graph.byId(typeId);
        if (type == null || !CLASSLIKE.contains(type.getType()) || out.size() >= max + 1) {
            return;
        }
        for (EdgeDto edge : graph.outgoing(type.getId())) {
            if (!"HAS_METHOD".equals(edge.getType())) {
                continue;
            }
            NodeDto candidate = graph.byId(edge.getTarget());
            if (candidate != null
                    && Objects.equals(target.getName(), candidate.getName())
                    && paramsCompatible(target, candidate)) {
                out.add(candidate.getFullName());
            }
        }
    }

    /** Lenient overload guard: compare paramTypes when both sides carry them, else name-match. */
    private static boolean paramsCompatible(NodeDto first, NodeDto second) {
        List<String> a = listProperty(first);
        List<String> b = listProperty(second);
        if (a.isEmpty() || b.isEmpty()) {
            return true;
        }
        return a.equals(b);
    }

    private static List<String> listProperty(NodeDto node) {
        if (node.getProperties() == null) {
            return List.of();
        }
        Object value = node.getProperties().get("paramTypes");
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        return List.of();
    }
}
