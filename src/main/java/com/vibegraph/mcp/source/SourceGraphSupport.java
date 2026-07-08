package com.vibegraph.mcp.source;

import org.springframework.stereotype.Component;

import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.service.GraphService;

import lombok.RequiredArgsConstructor;

/**
 * Loads a bounded {@link GraphView} for the source-reading MCP tools and exposes shared
 * helpers for reading line ranges from graph nodes.
 */
@Component
@RequiredArgsConstructor
public class SourceGraphSupport {

    public static final int MAX_NODES_TO_PROCESS = 50_000;
    public static final int MAX_EDGES_TO_PROCESS = 200_000;

    private final GraphService graphService;

    /** Load the full graph as a {@link GraphView}; returns {@code null} when it is too large. */
    public GraphView load(String projectId) {
        GraphDataResponse graph = graphService.getFullGraph(projectId);
        int nodeCount = graph == null || graph.getNodes() == null ? 0 : graph.getNodes().size();
        int edgeCount = graph == null || graph.getEdges() == null ? 0 : graph.getEdges().size();
        if (nodeCount > MAX_NODES_TO_PROCESS || edgeCount > MAX_EDGES_TO_PROCESS) {
            return null;
        }
        return new GraphView(
                graph == null ? null : graph.getNodes(),
                graph == null ? null : graph.getEdges());
    }

    /** End line declared on a node ({@code endLine} property), or null when unknown. */
    public static Integer endLineOf(NodeDto node) {
        if (node == null || node.getProperties() == null) {
            return null;
        }
        Object value = node.getProperties().get("endLine");
        if (value instanceof Number number) {
            int end = number.intValue();
            return end > 0 ? end : null;
        }
        return null;
    }

    /** String property accessor that treats blank/non-string values as absent. */
    public static String stringProperty(NodeDto node, String key) {
        if (node == null || node.getProperties() == null) {
            return null;
        }
        Object value = node.getProperties().get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    /**
     * Relativize an absolute file path (or any value that embeds one) so MCP responses never leak
     * drive letters, OS usernames, or absolute server paths. The heuristic strips everything up to
     * the first {@code /src/} or {@code /vibegraph-web/} segment; for other absolute paths it falls
     * back to the trailing filename. Values that are not absolute (e.g. dotted fully-qualified
     * names like {@code com.app.Foo}) are returned unchanged, so this is a safe no-op for
     * non-{@code File} graph nodes.
     */
    public static String relativizePath(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String fp = value.replace('\\', '/');
        int src = fp.indexOf("/src/");
        if (src >= 0) {
            return fp.substring(src + 1);
        }
        int web = fp.indexOf("/vibegraph-web/");
        if (web >= 0) {
            return fp.substring(web + 1);
        }
        boolean windowsAbsolute = fp.length() > 2 && Character.isLetter(fp.charAt(0)) && fp.charAt(1) == ':';
        if (windowsAbsolute || fp.startsWith("/")) {
            return fp.substring(fp.lastIndexOf('/') + 1);
        }
        return value;
    }
}
