package com.vibegraph.mcp.source;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.NodeDto;

/**
 * An in-memory, read-only view over a project's graph (nodes + edges) with deterministic
 * lookup helpers used by the source-reading MCP tools.
 *
 * <p>All matching happens in-memory against the already-loaded graph DTO, so user queries
 * are never interpolated into Cypher — there is no injection surface here.
 */
public final class GraphView {

    private final List<NodeDto> nodes;
    private final List<EdgeDto> edges;
    private final Map<String, NodeDto> byId;

    public GraphView(List<NodeDto> nodes, List<EdgeDto> edges) {
        this.nodes = nodes == null ? List.of() : nodes.stream().filter(Objects::nonNull).toList();
        this.edges = edges == null ? List.of()
                : edges.stream()
                        .filter(Objects::nonNull)
                        .filter(edge -> edge.getSource() != null && edge.getTarget() != null)
                        .toList();
        this.byId = new LinkedHashMap<>();
        this.nodes.stream()
                .filter(node -> node.getId() != null && !node.getId().isBlank())
                .sorted(NODE_ORDER)
                .forEach(node -> byId.putIfAbsent(node.getId(), node));
    }

    public List<NodeDto> nodes() {
        return nodes;
    }

    public List<EdgeDto> edges() {
        return edges;
    }

    public NodeDto byId(String id) {
        return id == null ? null : byId.get(id);
    }

    /**
     * Resolve a single node by exact id, exact fullName, or — only when unambiguous —
     * a partial match (simple name or fullName suffix), restricted to {@code types} when given.
     */
    public Resolution resolve(String query, Set<String> types) {
        List<NodeDto> pool = nodes.stream()
                .filter(node -> types == null || types.isEmpty() || types.contains(node.getType()))
                .sorted(NODE_ORDER)
                .toList();

        Optional<NodeDto> exact = pool.stream()
                .filter(node -> query.equals(node.getId()) || query.equals(node.getFullName()))
                .findFirst();
        if (exact.isPresent()) {
            return Resolution.unique(exact.get());
        }

        String bareQuery = stripParens(query);
        List<NodeDto> partial = pool.stream()
                .filter(node -> matchesPartial(node, query, bareQuery))
                .distinct()
                .toList();
        if (partial.size() == 1) {
            return Resolution.unique(partial.get(0));
        }
        if (partial.isEmpty()) {
            return Resolution.notFound();
        }
        return Resolution.ambiguous(partial);
    }

    private boolean matchesPartial(NodeDto node, String query, String bareQuery) {
        if (query.equals(node.getName())) {
            return true;
        }
        String fullName = node.getFullName();
        if (fullName == null) {
            return false;
        }
        if (fullName.endsWith("." + query) || fullName.endsWith(query)) {
            return true;
        }
        String bareFull = stripParens(fullName);
        return bareFull.endsWith("." + bareQuery) && bareQuery.equals(stripParens(node.getName()));
    }

    /** All non-File nodes declared in the given file path. */
    public List<NodeDto> nodesInFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return List.of();
        }
        return nodes.stream()
                .filter(node -> !"File".equals(node.getType()))
                .filter(node -> filePath.equals(node.getFilePath()))
                .sorted(Comparator.comparing(node -> node.getLineNumber() == null ? Integer.MAX_VALUE : node.getLineNumber()))
                .toList();
    }

    /** Edges where the node is the source. */
    public List<EdgeDto> outgoing(String nodeId) {
        List<EdgeDto> result = new ArrayList<>();
        for (EdgeDto edge : edges) {
            if (edge.getSource().equals(nodeId)) {
                result.add(edge);
            }
        }
        return result;
    }

    /** Edges where the node is the target. */
    public List<EdgeDto> incoming(String nodeId) {
        List<EdgeDto> result = new ArrayList<>();
        for (EdgeDto edge : edges) {
            if (edge.getTarget().equals(nodeId)) {
                result.add(edge);
            }
        }
        return result;
    }

    public static String stripParens(String value) {
        if (value == null) {
            return "";
        }
        int paren = value.indexOf('(');
        return paren < 0 ? value : value.substring(0, paren);
    }

    public static final Comparator<NodeDto> NODE_ORDER = Comparator
            .comparing((NodeDto node) -> node.getFullName() == null ? "" : node.getFullName())
            .thenComparing(node -> node.getName() == null ? "" : node.getName())
            .thenComparing(node -> node.getId() == null ? "" : node.getId());

    /** Outcome of resolving a node query. */
    public record Resolution(NodeDto node, List<NodeDto> candidates, Kind kind) {
        public enum Kind { UNIQUE, AMBIGUOUS, NOT_FOUND }

        public static Resolution unique(NodeDto node) {
            return new Resolution(node, List.of(node), Kind.UNIQUE);
        }

        public static Resolution ambiguous(List<NodeDto> candidates) {
            return new Resolution(null, List.copyOf(candidates), Kind.AMBIGUOUS);
        }

        public static Resolution notFound() {
            return new Resolution(null, List.of(), Kind.NOT_FOUND);
        }

        public boolean isUnique() {
            return kind == Kind.UNIQUE;
        }
    }
}
