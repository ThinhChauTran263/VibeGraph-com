package com.vibegraph.mcp.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.mcp.dto.response.ArchitectureContextResponse;
import com.vibegraph.mcp.service.ArchitectureAnalyzer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArchitectureAnalyzerImpl implements ArchitectureAnalyzer {

    private static final int MAX_PROJECT_ID_LENGTH = 512;
    private static final List<String> CANONICAL_LAYERS = List.of("CONTROLLER", "SERVICE", "REPOSITORY");

    private final GraphService graphService;

    @Override
    public ArchitectureContextResponse analyzeProject(String projectId) {
        validateProjectId(projectId);
        GraphDataResponse graph = graphService.getFullGraph(projectId);
        List<NodeDto> nodes = safeNodes(graph);

        Map<String, Integer> summaryCounts = countByType(nodes);
        List<ArchitectureContextResponse.LayerSummary> layers = detectLayers(nodes);
        Map<String, String> patterns = detectPatterns(layers);
        Map<String, String> namingConventions = detectNamingConventions(layers);
        List<String> warnings = detectWarnings(nodes);

        return ArchitectureContextResponse.builder()
                .projectId(projectId)
                .summaryCounts(summaryCounts)
                .layers(layers)
                .patterns(patterns)
                .namingConventions(namingConventions)
                .warnings(warnings)
                .build();
    }

    private void validateProjectId(String projectId) {
        if (projectId == null || projectId.isBlank() || projectId.length() > MAX_PROJECT_ID_LENGTH) {
            throw new IllegalArgumentException("projectId must be non-blank and at most 512 characters");
        }
    }

    private List<NodeDto> safeNodes(GraphDataResponse graph) {
        if (graph == null || graph.getNodes() == null) {
            return List.of();
        }
        return graph.getNodes().stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, Integer> countByType(List<NodeDto> nodes) {
        Map<String, Integer> counts = new TreeMap<>();
        for (NodeDto node : nodes) {
            if (node.getType() != null && !node.getType().isBlank()) {
                counts.merge(node.getType(), 1, Integer::sum);
            }
        }
        return new LinkedHashMap<>(counts);
    }

    private List<ArchitectureContextResponse.LayerSummary> detectLayers(List<NodeDto> nodes) {
        Map<String, Integer> layerCounts = new TreeMap<>();
        for (NodeDto node : nodes) {
            String layer = detectLayer(node);
            if (layer != null) {
                layerCounts.merge(layer, 1, Integer::sum);
            }
        }
        return layerCounts.entrySet().stream()
                .map(entry -> ArchitectureContextResponse.LayerSummary.builder()
                        .name(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .toList();
    }

    private String detectLayer(NodeDto node) {
        Object springLayer = node.getProperties() == null ? null : node.getProperties().get("springLayer");
        if (springLayer instanceof String value && !value.isBlank()) {
            return value.toUpperCase(Locale.ROOT);
        }
        if (isRouteNode(node)) {
            return "ROUTE";
        }
        String name = node.getName() == null ? "" : node.getName();
        if (name.endsWith("Controller")) {
            return "CONTROLLER";
        }
        if (name.endsWith("Service")) {
            return "SERVICE";
        }
        if (name.endsWith("Repository")) {
            return "REPOSITORY";
        }
        return null;
    }

    private boolean isRouteNode(NodeDto node) {
        return "Route".equals(node.getType()) || "APIEndpoint".equals(node.getType());
    }

    private Map<String, String> detectPatterns(List<ArchitectureContextResponse.LayerSummary> layers) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        layers.stream()
                .sorted(Comparator.comparing(ArchitectureContextResponse.LayerSummary::getName))
                .forEach(layer -> counts.put(layer.getName(), layer.getCount()));

        Map<String, String> patterns = new TreeMap<>();
        boolean hasCanonicalLayers = CANONICAL_LAYERS.stream().allMatch(counts::containsKey);
        if (hasCanonicalLayers) {
            patterns.put("layeredArchitecture", "CONTROLLER -> SERVICE -> REPOSITORY");
        }
        int layeredComponents = CANONICAL_LAYERS.stream()
                .mapToInt(layer -> counts.getOrDefault(layer, 0))
                .sum();
        if (layeredComponents > 0) {
            patterns.put("layeredComponents", layeredComponents + " controller/service/repository components");
        }
        int routeCount = counts.getOrDefault("ROUTE", 0);
        if (routeCount > 0) {
            patterns.put("apiEndpoints", routeCount + " API endpoint nodes");
        }
        return new LinkedHashMap<>(patterns);
    }

    private Map<String, String> detectNamingConventions(List<ArchitectureContextResponse.LayerSummary> layers) {
        Map<String, String> conventions = new TreeMap<>();
        for (ArchitectureContextResponse.LayerSummary layer : layers) {
            switch (layer.getName()) {
                case "CONTROLLER" -> conventions.put("CONTROLLER", "*Controller");
                case "SERVICE" -> conventions.put("SERVICE", "*Service");
                case "REPOSITORY" -> conventions.put("REPOSITORY", "*Repository");
                default -> {
                }
            }
        }
        return new LinkedHashMap<>(conventions);
    }

    private List<String> detectWarnings(List<NodeDto> nodes) {
        if (nodes.isEmpty()) {
            return List.of("Graph is empty. Analyze the project before requesting architecture context.");
        }
        return new ArrayList<>();
    }
}
