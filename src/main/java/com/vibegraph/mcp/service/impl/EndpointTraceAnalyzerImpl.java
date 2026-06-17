package com.vibegraph.mcp.service.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.mcp.dto.response.EndpointTraceResponse;
import com.vibegraph.mcp.dto.response.EndpointTraceResponse.EndpointInfo;
import com.vibegraph.mcp.dto.response.EndpointTraceResponse.FlowStep;
import com.vibegraph.mcp.dto.response.EndpointTraceResponse.HandlerInfo;
import com.vibegraph.mcp.dto.response.EndpointTraceResponse.NodeRef;
import com.vibegraph.mcp.service.EndpointTraceAnalyzer;
import com.vibegraph.mcp.source.GraphView;
import com.vibegraph.mcp.source.SourceGraphSupport;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EndpointTraceAnalyzerImpl implements EndpointTraceAnalyzer {

    private static final int MAX_PROJECT_ID_LENGTH = 512;
    private static final int MAX_PATH_LENGTH = 512;
    private static final int MAX_DEPTH_CAP = 10;
    private static final int DEFAULT_DEPTH = 5;
    private static final int MAX_FLOW_STEPS = 100;
    private static final int MAX_RELATED_TYPES = 25;
    private static final Set<String> HTTP_METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
    private static final Set<String> ENDPOINT_TYPES = Set.of("APIEndpoint", "Route");
    private static final Set<String> RELATED_TYPES = Set.of("DBModel", "Record");

    private final SourceGraphSupport graphSupport;

    @Override
    public EndpointTraceResponse traceEndpoint(String projectId, String httpMethod, String routePath, Integer maxDepth) {
        String normalizedProjectId = validate(projectId, "projectId", MAX_PROJECT_ID_LENGTH);
        String method = validateHttpMethod(httpMethod);
        String path = validate(routePath, "routePath", MAX_PATH_LENGTH);
        int depth = validateDepth(maxDepth);

        GraphView graph = safeLoad(normalizedProjectId);
        if (graph == null) {
            return warning(normalizedProjectId, method, path, "Endpoint trace is temporarily unavailable.");
        }

        Optional<NodeDto> endpoint = findEndpoint(graph, method, path);
        if (endpoint.isEmpty()) {
            return warning(normalizedProjectId, method, path, "No endpoint found for " + method + " " + path);
        }
        NodeDto endpointNode = endpoint.get();

        Optional<NodeDto> handler = graph.incoming(endpointNode.getId()).stream()
                .filter(edge -> "HANDLES_ROUTE".equals(edge.getType()))
                .map(edge -> graph.byId(edge.getSource()))
                .filter(node -> node != null)
                .sorted(GraphView.NODE_ORDER)
                .findFirst();

        List<String> warnings = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        if (handler.isEmpty()) {
            return EndpointTraceResponse.builder()
                    .projectId(normalizedProjectId)
                    .httpMethod(method)
                    .routePath(path)
                    .endpoint(toEndpointInfo(endpointNode))
                    .flowSteps(List.of())
                    .relatedTypes(List.of())
                    .traceStrategy("none")
                    .warnings(List.of("Endpoint has no handler method in the graph."))
                    .notes(List.of())
                    .build();
        }

        NodeDto handlerNode = handler.get();
        boolean hasFlow = graph.outgoing(handlerNode.getId()).stream()
                .anyMatch(edge -> "STEP_IN_FLOW".equals(edge.getType()));
        String flowEdgeType = hasFlow ? "STEP_IN_FLOW" : "CALLS";
        String strategy = hasFlow ? "STEP_IN_FLOW" : "CALLS_FALLBACK";
        if (!hasFlow) {
            notes.add("No STEP_IN_FLOW edges from the handler; traced via CALLS (lower confidence).");
        }

        List<FlowStep> flowSteps = trace(graph, handlerNode, flowEdgeType, depth);
        List<NodeRef> relatedTypes = relatedTypes(graph, handlerNode, flowSteps);

        return EndpointTraceResponse.builder()
                .projectId(normalizedProjectId)
                .httpMethod(method)
                .routePath(path)
                .endpoint(toEndpointInfo(endpointNode))
                .handlerMethod(toHandlerInfo(handlerNode))
                .traceStrategy(strategy)
                .flowSteps(flowSteps)
                .relatedTypes(relatedTypes)
                .warnings(warnings)
                .notes(notes)
                .build();
    }

    private List<FlowStep> trace(GraphView graph, NodeDto handler, String edgeType, int maxDepth) {
        List<FlowStep> steps = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        visited.add(handler.getId());
        Deque<NodeAtDepth> queue = new ArrayDeque<>();
        queue.add(new NodeAtDepth(handler.getId(), 0));
        int index = 0;

        while (!queue.isEmpty() && steps.size() < MAX_FLOW_STEPS) {
            NodeAtDepth current = queue.poll();
            if (current.depth() >= maxDepth) {
                continue;
            }
            List<EdgeDto> outgoing = graph.outgoing(current.id()).stream()
                    .filter(edge -> edgeType.equals(edge.getType()))
                    .sorted((a, b) -> {
                        int ai = a.getLineNumber() == null ? Integer.MAX_VALUE : a.getLineNumber();
                        int bi = b.getLineNumber() == null ? Integer.MAX_VALUE : b.getLineNumber();
                        return Integer.compare(ai, bi);
                    })
                    .toList();
            for (EdgeDto edge : outgoing) {
                String targetId = edge.getTarget();
                if (visited.add(targetId)) {
                    NodeDto target = graph.byId(targetId);
                    if (target != null) {
                        steps.add(FlowStep.builder()
                                .index(++index)
                                .nodeId(target.getId())
                                .type(target.getType())
                                .name(target.getName())
                                .fullName(target.getFullName())
                                .lineNumber(target.getLineNumber())
                                .relationshipType(edge.getType())
                                .build());
                        queue.add(new NodeAtDepth(targetId, current.depth() + 1));
                    }
                    if (steps.size() >= MAX_FLOW_STEPS) {
                        break;
                    }
                }
            }
        }
        return steps;
    }

    private List<NodeRef> relatedTypes(GraphView graph, NodeDto handler, List<FlowStep> flowSteps) {
        Set<String> flowNodeIds = new LinkedHashSet<>();
        flowNodeIds.add(handler.getId());
        flowSteps.forEach(step -> flowNodeIds.add(step.getNodeId()));

        LinkedHashSet<NodeRef> related = new LinkedHashSet<>();
        for (String nodeId : flowNodeIds) {
            for (EdgeDto edge : graph.outgoing(nodeId)) {
                NodeDto target = graph.byId(edge.getTarget());
                if (target != null && RELATED_TYPES.contains(target.getType())) {
                    related.add(NodeRef.builder()
                            .id(target.getId())
                            .type(target.getType())
                            .name(target.getName())
                            .fullName(target.getFullName())
                            .build());
                }
            }
            if (related.size() >= MAX_RELATED_TYPES) {
                break;
            }
        }
        return new ArrayList<>(related);
    }

    private Optional<NodeDto> findEndpoint(GraphView graph, String method, String path) {
        String composite = method + " " + path;
        return graph.nodes().stream()
                .filter(node -> ENDPOINT_TYPES.contains(node.getType()))
                .filter(node -> matchesEndpoint(node, method, path, composite))
                .sorted(GraphView.NODE_ORDER)
                .findFirst();
    }

    private boolean matchesEndpoint(NodeDto node, String method, String path, String composite) {
        String nodeMethod = SourceGraphSupport.stringProperty(node, "httpMethod");
        String nodePath = SourceGraphSupport.stringProperty(node, "routePath");
        if (nodeMethod != null && nodePath != null) {
            return method.equalsIgnoreCase(nodeMethod) && path.equals(nodePath);
        }
        return composite.equals(node.getFullName()) || composite.equals(node.getName()) || composite.equals(node.getId());
    }

    private EndpointInfo toEndpointInfo(NodeDto node) {
        return EndpointInfo.builder()
                .id(node.getId())
                .type(node.getType())
                .name(node.getName())
                .httpMethod(SourceGraphSupport.stringProperty(node, "httpMethod"))
                .routePath(SourceGraphSupport.stringProperty(node, "routePath"))
                .build();
    }

    private HandlerInfo toHandlerInfo(NodeDto node) {
        return HandlerInfo.builder()
                .id(node.getId())
                .name(node.getName())
                .fullName(node.getFullName())
                .lineNumber(node.getLineNumber())
                .endLine(SourceGraphSupport.endLineOf(node))
                .build();
    }

    private String validateHttpMethod(String httpMethod) {
        String normalized = validate(httpMethod, "httpMethod", 16).toUpperCase(Locale.ROOT);
        if (!HTTP_METHODS.contains(normalized)) {
            throw new IllegalArgumentException("httpMethod must be one of " + HTTP_METHODS);
        }
        return normalized;
    }

    private int validateDepth(Integer maxDepth) {
        if (maxDepth == null) {
            return DEFAULT_DEPTH;
        }
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be at least 1");
        }
        return Math.min(maxDepth, MAX_DEPTH_CAP);
    }

    private EndpointTraceResponse warning(String projectId, String method, String path, String message) {
        return EndpointTraceResponse.builder()
                .projectId(projectId)
                .httpMethod(method)
                .routePath(path)
                .flowSteps(List.of())
                .relatedTypes(List.of())
                .traceStrategy("none")
                .warnings(List.of(message))
                .notes(List.of())
                .build();
    }

    private GraphView safeLoad(String projectId) {
        try {
            return graphSupport.load(projectId);
        } catch (ProjectNotFoundException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String validate(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    field + " must be non-blank, printable, and at most " + maxLength + " characters");
        }
        return value.trim();
    }

    private record NodeAtDepth(String id, int depth) {
    }
}
