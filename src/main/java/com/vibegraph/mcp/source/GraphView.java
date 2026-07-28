package com.vibegraph.mcp.source;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.NodeDto;

/**
 * An in-memory, read-only view over a project's graph (nodes + edges) with deterministic
 * lookup helpers used by the source-reading MCP tools.
 *
 * <p>All matching happens in-memory against the already-loaded graph DTO, so user queries
 * are never interpolated into Cypher — there is no injection surface here.
 *
 * <p>All lookup structures (adjacency lists, id/fullName/filePath indexes, a pre-sorted
 * node list) are built once in the constructor, so {@link #outgoing(String)},
 * {@link #incoming(String)}, {@link #nodesInFile(String)} are O(1) map lookups and
 * {@link #resolve(String, Set)} is a single un-sorted pass — analyzers may call them in
 * loops without re-scanning the full edge list.
 */
public final class GraphView {

    private final List<NodeDto> nodes;
    private final List<EdgeDto> edges;
    private final Map<String, NodeDto> byId;

    /** Nodes pre-sorted by {@link #NODE_ORDER}; the deterministic pool for {@link #resolve}. */
    private final List<NodeDto> sortedNodes;
    /** All nodes sharing an id, in {@link #NODE_ORDER} (defensive: ids should be unique). */
    private final Map<String, List<NodeDto>> byIdAll;
    /** All nodes sharing a fullName, in {@link #NODE_ORDER}. */
    private final Map<String, List<NodeDto>> byFullName;
    /** Edges grouped by source node id, in original edge-list order. */
    private final Map<String, List<EdgeDto>> outgoingByNode;
    /** Edges grouped by target node id, in original edge-list order. */
    private final Map<String, List<EdgeDto>> incomingByNode;
    /** Non-File nodes grouped by filePath, sorted by line number. */
    private final Map<String, List<NodeDto>> nodesByFilePath;

    public GraphView(List<NodeDto> nodes, List<EdgeDto> edges) {
        this.nodes = nodes == null ? List.of() : nodes.stream().filter(Objects::nonNull).toList();
        this.edges = edges == null ? List.of()
                : edges.stream()
                        .filter(Objects::nonNull)
                        .filter(edge -> edge.getSource() != null && edge.getTarget() != null)
                        .toList();

        this.sortedNodes = this.nodes.stream().sorted(NODE_ORDER).toList();

        this.byId = new LinkedHashMap<>();
        this.byIdAll = new LinkedHashMap<>();
        this.byFullName = new LinkedHashMap<>();
        for (NodeDto node : sortedNodes) {
            if (node.getId() != null && !node.getId().isBlank()) {
                byId.putIfAbsent(node.getId(), node);
                byIdAll.computeIfAbsent(node.getId(), key -> new ArrayList<>()).add(node);
            }
            if (node.getFullName() != null && !node.getFullName().isBlank()) {
                byFullName.computeIfAbsent(node.getFullName(), key -> new ArrayList<>()).add(node);
            }
        }

        this.outgoingByNode = new LinkedHashMap<>();
        this.incomingByNode = new LinkedHashMap<>();
        for (EdgeDto edge : this.edges) {
            outgoingByNode.computeIfAbsent(edge.getSource(), key -> new ArrayList<>()).add(edge);
            incomingByNode.computeIfAbsent(edge.getTarget(), key -> new ArrayList<>()).add(edge);
        }

        Map<String, List<NodeDto>> byFile = new LinkedHashMap<>();
        for (NodeDto node : this.nodes) {
            if (!"File".equals(node.getType()) && node.getFilePath() != null && !node.getFilePath().isBlank()) {
                byFile.computeIfAbsent(node.getFilePath(), key -> new ArrayList<>()).add(node);
            }
        }
        byFile.values().forEach(list -> list.sort(Comparator.comparing(
                node -> node.getLineNumber() == null ? Integer.MAX_VALUE : node.getLineNumber())));
        this.nodesByFilePath = byFile;
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

    /** First node (in {@link #NODE_ORDER}) with this exact fullName, or null. */
    public NodeDto byFullName(String fullName) {
        if (fullName == null) {
            return null;
        }
        List<NodeDto> bucket = byFullName.get(fullName);
        return bucket == null || bucket.isEmpty() ? null : bucket.get(0);
    }

    /**
     * Resolve a single node by exact id, exact fullName, or — only when unambiguous —
     * a partial match (simple name or fullName suffix), restricted to {@code types} when given.
     */
    public Resolution resolve(String query, Set<String> types) {
        NodeDto exact = exactMatch(query, types);
        if (exact != null) {
            return Resolution.unique(exact);
        }

        String bareQuery = stripParens(query);
        Set<NodeDto> partial = new LinkedHashSet<>();
        for (NodeDto node : sortedNodes) {
            if (typeAllowed(node, types) && matchesPartial(node, query, bareQuery)) {
                partial.add(node);
            }
        }
        if (partial.size() == 1) {
            return Resolution.unique(partial.iterator().next());
        }
        if (partial.isEmpty()) {
            return Resolution.notFound();
        }
        return Resolution.ambiguous(new ArrayList<>(partial));
    }

    /** First node in {@link #NODE_ORDER} whose id or fullName equals the query, respecting the type filter. */
    private NodeDto exactMatch(String query, Set<String> types) {
        NodeDto best = null;
        for (NodeDto candidate : byIdAll.getOrDefault(query, List.of())) {
            if (typeAllowed(candidate, types)) {
                best = candidate;
                break;
            }
        }
        for (NodeDto candidate : byFullName.getOrDefault(query, List.of())) {
            if (typeAllowed(candidate, types)) {
                if (best == null || NODE_ORDER.compare(candidate, best) < 0) {
                    best = candidate;
                }
                break;
            }
        }
        return best;
    }

    private boolean typeAllowed(NodeDto node, Set<String> types) {
        return types == null || types.isEmpty() || types.contains(node.getType());
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
        return bareFull.endsWith("." + bareQuery) || bareFull.equals(bareQuery);
    }

    /** All non-File nodes declared in the given file path, sorted by line number. */
    public List<NodeDto> nodesInFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return List.of();
        }
        return nodesByFilePath.getOrDefault(filePath, List.of());
    }

    /** Edges where the node is the source. O(1) lookup against the prebuilt adjacency map. */
    public List<EdgeDto> outgoing(String nodeId) {
        return nodeId == null ? List.of() : outgoingByNode.getOrDefault(nodeId, List.of());
    }

    /** Edges where the node is the target. O(1) lookup against the prebuilt adjacency map. */
    public List<EdgeDto> incoming(String nodeId) {
        return nodeId == null ? List.of() : incomingByNode.getOrDefault(nodeId, List.of());
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
