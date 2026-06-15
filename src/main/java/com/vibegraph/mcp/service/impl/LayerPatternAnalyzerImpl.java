package com.vibegraph.mcp.service.impl;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.mcp.dto.response.LayerPatternResponse;
import com.vibegraph.mcp.service.LayerPatternAnalyzer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LayerPatternAnalyzerImpl implements LayerPatternAnalyzer {

    private static final int MAX_PROJECT_ID_LENGTH = 512;
    private static final int MAX_LAYER_LENGTH = 128;
    private static final int MAX_EXAMPLES = 10;
    private static final int MAX_DEPENDENCIES = 10;
    private static final int MAX_NODES_TO_PROCESS = 10_000;
    private static final int MAX_EDGES_TO_PROCESS = 50_000;
    private static final Set<String> KNOWN_LAYERS = Set.of("CONTROLLER", "SERVICE", "REPOSITORY", "CONFIG", "ROUTE");
    private static final Set<String> DEPENDENCY_EDGE_TYPES = Set.of("CALLS", "IMPORTS", "EXTENDS", "IMPLEMENTS", "INJECTS", "HANDLES_ROUTE");

    private final GraphService graphService;

    @Override
    public LayerPatternResponse analyzeLayer(String projectId, String layer) {
        String normalizedProjectId = validate(projectId, "projectId", MAX_PROJECT_ID_LENGTH);
        String requestedLayer = validate(layer, "layer", MAX_LAYER_LENGTH);
        String normalizedLayer = normalizeLayer(requestedLayer);
        if (!KNOWN_LAYERS.contains(normalizedLayer)) {
            return unknownLayerResponse(normalizedProjectId, requestedLayer, normalizedLayer);
        }
        try {
            GraphDataResponse graph = graphService.getFullGraph(normalizedProjectId);
            List<NodeDto> nodes = safeNodes(graph);
            List<EdgeDto> edges = safeEdges(graph);
            if (nodes.size() > MAX_NODES_TO_PROCESS || edges.size() > MAX_EDGES_TO_PROCESS) {
                return tooLargeResponse(normalizedProjectId, requestedLayer, normalizedLayer, nodes.size(), edges.size());
            }
            return toResponse(normalizedProjectId, requestedLayer, normalizedLayer, nodes, edges);
        } catch (ProjectNotFoundException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return unavailableResponse(normalizedProjectId, requestedLayer, normalizedLayer);
        }
    }

    private LayerPatternResponse toResponse(String projectId, String requestedLayer, String normalizedLayer, List<NodeDto> nodes, List<EdgeDto> edges) {
        List<NodeDto> layerNodes = nodes.stream()
                .filter(node -> normalizedLayer.equals(nodeLayer(node)))
                .sorted(nodeComparator())
                .toList();
        List<LayerPatternResponse.LayerExample> examples = layerNodes.stream()
                .limit(MAX_EXAMPLES)
                .map(this::toLayerExample)
                .toList();
        return LayerPatternResponse.builder()
                .projectId(projectId)
                .requestedLayer(requestedLayer)
                .normalizedLayer(normalizedLayer)
                .description(description(normalizedLayer))
                .examples(examples)
                .commonDependencies(commonDependencies(layerNodes, nodes, edges))
                .namingConventions(namingConventions(normalizedLayer))
                .doRules(doRules(normalizedLayer))
                .dontRules(dontRules(normalizedLayer))
                .patternNotes(patternNotes(normalizedLayer))
                .warnings(warnings(normalizedLayer, examples.size(), layerNodes.size()))
                .build();
    }

    private LayerPatternResponse unknownLayerResponse(String projectId, String requestedLayer, String normalizedLayer) {
        return LayerPatternResponse.builder()
                .projectId(projectId)
                .requestedLayer(requestedLayer)
                .normalizedLayer(normalizedLayer)
                .description(description(normalizedLayer))
                .examples(List.of())
                .commonDependencies(List.of())
                .namingConventions(namingConventions(normalizedLayer))
                .doRules(doRules(normalizedLayer))
                .dontRules(dontRules(normalizedLayer))
                .patternNotes(patternNotes(normalizedLayer))
                .warnings(List.of("Unknown layer: " + normalizedLayer))
                .build();
    }

    private LayerPatternResponse tooLargeResponse(String projectId, String requestedLayer, String normalizedLayer, int nodeCount, int edgeCount) {
        return LayerPatternResponse.builder()
                .projectId(projectId)
                .requestedLayer(requestedLayer)
                .normalizedLayer(normalizedLayer)
                .examples(List.of())
                .commonDependencies(List.of())
                .namingConventions(namingConventions(normalizedLayer))
                .doRules(doRules(normalizedLayer))
                .dontRules(dontRules(normalizedLayer))
                .patternNotes(patternNotes(normalizedLayer))
                .warnings(List.of("Graph is too large for layer pattern: " + nodeCount + " nodes, " + edgeCount + " edges."))
                .build();
    }

    private LayerPatternResponse unavailableResponse(String projectId, String requestedLayer, String normalizedLayer) {
        return LayerPatternResponse.builder()
                .projectId(projectId)
                .requestedLayer(requestedLayer)
                .normalizedLayer(normalizedLayer)
                .examples(List.of())
                .commonDependencies(List.of())
                .namingConventions(namingConventions(normalizedLayer))
                .doRules(doRules(normalizedLayer))
                .dontRules(dontRules(normalizedLayer))
                .patternNotes(patternNotes(normalizedLayer))
                .warnings(List.of("Layer pattern is temporarily unavailable."))
                .build();
    }

    private String validate(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength || hasControlCharacter(value)) {
            throw new IllegalArgumentException(fieldName + " must be non-blank, printable, and at most " + maxLength + " characters");
        }
        return value.trim();
    }

    private boolean hasControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private String normalizeLayer(String layer) {
        return layer.trim().replace('-', '_').replace(' ', '_').toUpperCase();
    }

    private List<NodeDto> safeNodes(GraphDataResponse graph) {
        if (graph == null || graph.getNodes() == null) {
            return List.of();
        }
        return graph.getNodes().stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private List<EdgeDto> safeEdges(GraphDataResponse graph) {
        if (graph == null || graph.getEdges() == null) {
            return List.of();
        }
        return graph.getEdges().stream()
                .filter(Objects::nonNull)
                .filter(edge -> edge.getSource() != null && edge.getTarget() != null)
                .toList();
    }

    private String nodeLayer(NodeDto node) {
        String springLayer = stringProperty(node, "springLayer");
        if (springLayer != null) {
            return normalizeLayer(springLayer);
        }
        if (isRouteNode(node)) {
            return "ROUTE";
        }
        return inferLayerFromName(node);
    }

    private boolean isRouteNode(NodeDto node) {
        return "Route".equals(node.getType()) || "APIEndpoint".equals(node.getType());
    }

    private String inferLayerFromName(NodeDto node) {
        String name = safeString(node.getFullName()) + "." + safeString(node.getName());
        if (name.contains("Controller")) {
            return "CONTROLLER";
        }
        if (name.contains("Service")) {
            return "SERVICE";
        }
        if (name.contains("Repository")) {
            return "REPOSITORY";
        }
        if (name.contains("Config") || name.contains("Configuration")) {
            return "CONFIG";
        }
        return "UNKNOWN";
    }

    private LayerPatternResponse.LayerExample toLayerExample(NodeDto node) {
        return LayerPatternResponse.LayerExample.builder()
                .id(node.getId())
                .type(node.getType())
                .name(node.getName())
                .fullName(node.getFullName())
                .lineNumber(node.getLineNumber())
                .build();
    }

    private List<LayerPatternResponse.DependencySummary> commonDependencies(List<NodeDto> layerNodes, List<NodeDto> nodes, List<EdgeDto> edges) {
        Map<String, NodeDto> nodesById = nodes.stream()
                .filter(node -> node.getId() != null && !node.getId().isBlank())
                .collect(Collectors.toMap(NodeDto::getId, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        Set<String> layerNodeIds = layerNodes.stream()
                .map(NodeDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, Long> counts = edges.stream()
                .filter(edge -> layerNodeIds.contains(edge.getSource()))
                .filter(edge -> DEPENDENCY_EDGE_TYPES.contains(edge.getType()))
                .map(edge -> dependencyKey(edge, nodesById))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .map(entry -> toDependencySummary(entry.getKey(), entry.getValue()))
                .sorted(dependencyComparator())
                .limit(MAX_DEPENDENCIES)
                .toList();
    }

    private String dependencyKey(EdgeDto edge, Map<String, NodeDto> nodesById) {
        NodeDto target = nodesById.get(edge.getTarget());
        if (target == null) {
            return null;
        }
        return edge.getType() + "|" + nodeLayer(target);
    }

    private LayerPatternResponse.DependencySummary toDependencySummary(String key, long count) {
        String[] parts = key.split("\\|", 2);
        return LayerPatternResponse.DependencySummary.builder()
                .relationType(parts[0])
                .targetLayer(parts.length > 1 ? parts[1] : "UNKNOWN")
                .count(Math.toIntExact(count))
                .build();
    }

    private String description(String layer) {
        return switch (layer) {
            case "CONTROLLER" -> "HTTP/API entry layer that accepts requests and delegates business work to services.";
            case "SERVICE" -> "Business logic layer that coordinates repositories, policies, and domain operations.";
            case "REPOSITORY" -> "Persistence boundary layer that encapsulates data access details.";
            case "CONFIG" -> "Application wiring layer for framework configuration and beans.";
            case "ROUTE" -> "Discovered route endpoints exposed by the analyzed application.";
            default -> "No built-in pattern is defined for this layer.";
        };
    }

    private Map<String, String> namingConventions(String layer) {
        Map<String, String> conventions = new LinkedHashMap<>();
        switch (layer) {
            case "CONTROLLER" -> {
                conventions.put("classSuffix", "*Controller");
                conventions.put("packageHint", "..controller..");
            }
            case "SERVICE" -> {
                conventions.put("classSuffix", "*Service or *ServiceImpl");
                conventions.put("packageHint", "..service..");
            }
            case "REPOSITORY" -> {
                conventions.put("classSuffix", "*Repository");
                conventions.put("packageHint", "..repository..");
            }
            case "CONFIG" -> {
                conventions.put("classSuffix", "*Config or *Configuration");
                conventions.put("packageHint", "..config..");
            }
            case "ROUTE" -> conventions.put("routeName", "HTTP method + path");
            default -> conventions.put("layer", "Use existing project naming for this layer");
        }
        return conventions;
    }

    private List<String> doRules(String layer) {
        return switch (layer) {
            case "CONTROLLER" -> List.of("Validate request boundaries", "Delegate business logic to services", "Return stable response DTOs");
            case "SERVICE" -> List.of("Keep business decisions here", "Depend on repositories through interfaces", "Use transactions around cohesive operations");
            case "REPOSITORY" -> List.of("Keep persistence details behind repository methods", "Use parameterized queries", "Return domain/DTO data without controller concerns");
            case "CONFIG" -> List.of("Use constructor-injected beans", "Keep configuration cohesive", "Externalize environment-specific values");
            case "ROUTE" -> List.of("Keep route metadata explicit", "Connect routes to handler symbols when available");
            default -> List.of("Follow the closest existing layer examples", "Keep dependencies flowing inward consistently");
        };
    }

    private List<String> dontRules(String layer) {
        return switch (layer) {
            case "CONTROLLER" -> List.of("Do not put business logic in controllers", "Do not access repositories directly unless the project already does so", "Do not leak internal exceptions");
            case "SERVICE" -> List.of("Do not depend on web-specific request objects", "Do not hide persistence failures silently", "Do not mix unrelated workflows in one method");
            case "REPOSITORY" -> List.of("Do not build queries by string-concatenating user input", "Do not return transport-layer envelopes", "Do not include business orchestration");
            case "CONFIG" -> List.of("Do not hardcode secrets", "Do not mix unrelated bean groups", "Do not use field injection");
            case "ROUTE" -> List.of("Do not treat route nodes as implementation classes", "Do not infer authorization from route presence alone");
            default -> List.of("Do not introduce cross-layer shortcuts", "Do not copy examples without matching their dependencies");
        };
    }

    private List<String> patternNotes(String layer) {
        if (KNOWN_LAYERS.contains(layer)) {
            return List.of("Examples are sampled from the analyzed graph for the requested layer.", "Dependency summaries count outgoing graph edges from sampled layer nodes.");
        }
        return List.of("Requested layer is not one of the built-in layers: CONTROLLER, SERVICE, REPOSITORY, CONFIG, ROUTE.");
    }

    private List<String> warnings(String layer, int returnedExamples, int totalExamples) {
        if (!KNOWN_LAYERS.contains(layer)) {
            return List.of("Unknown layer: " + layer);
        }
        if (totalExamples == 0) {
            return List.of("No examples found for layer: " + layer);
        }
        if (totalExamples > returnedExamples) {
            return List.of("examples truncated to " + returnedExamples + " of " + totalExamples);
        }
        return List.of();
    }

    private String stringProperty(NodeDto node, String key) {
        if (node.getProperties() == null) {
            return null;
        }
        Object value = node.getProperties().get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private Comparator<NodeDto> nodeComparator() {
        return Comparator.comparing((NodeDto node) -> safeString(node.getFullName()))
                .thenComparing(node -> safeString(node.getName()))
                .thenComparing(node -> safeString(node.getId()));
    }

    private Comparator<LayerPatternResponse.DependencySummary> dependencyComparator() {
        return Comparator.comparingInt(LayerPatternResponse.DependencySummary::getCount).reversed()
                .thenComparing(LayerPatternResponse.DependencySummary::getRelationType)
                .thenComparing(LayerPatternResponse.DependencySummary::getTargetLayer);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }
}
