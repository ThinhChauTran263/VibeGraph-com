package com.vibegraph.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vibegraph.parser.node.NodeData;

/**
 * Assigns a coarse architectural layer to parsed nodes.
 */
public final class NodeLayerClassifier {

    private static final String PRESENTATION = "PRESENTATION";
    private static final String SERVICE = "SERVICE";
    private static final String DATA_ACCESS = "DATA_ACCESS";
    private static final String DOMAIN = "DOMAIN";
    private static final String OTHER = "OTHER";

    private NodeLayerClassifier() {
    }

    public static List<NodeData> withLayers(List<NodeData> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }

        Map<String, NodeData> byFullName = new HashMap<>();
        for (NodeData node : nodes) {
            byFullName.put(node.fullName(), node);
        }
        Map<String, String> fileLayerByPath = fileLayers(nodes);

        Map<String, String> layerCache = new HashMap<>();
        List<NodeData> layered = new ArrayList<>(nodes.size());
        for (NodeData node : nodes) {
            String layer = layerOf(node, byFullName, fileLayerByPath, layerCache);
            Map<String, Object> properties = new HashMap<>();
            if (node.properties() != null) {
                properties.putAll(node.properties());
            }
            properties.put("layer", layer);
            layered.add(NodeData.of(
                    node.type(),
                    node.name(),
                    node.fullName(),
                    node.filePath(),
                    node.lineNumber(),
                    node.endLine(),
                    properties));
        }
        return layered;
    }

    private static String layerOf(NodeData node, Map<String, NodeData> byFullName,
            Map<String, String> fileLayerByPath, Map<String, String> cache) {
        if (node == null) {
            return OTHER;
        }
        String cached = cache.get(node.fullName());
        if (cached != null) {
            return cached;
        }

        String explicit = explicitLayer(node);
        if (explicit != null) {
            cache.put(node.fullName(), explicit);
            return explicit;
        }

        if ("File".equals(node.type())) {
            String fileLayer = fileLayerByPath.get(node.filePath());
            if (fileLayer != null) {
                cache.put(node.fullName(), fileLayer);
                return fileLayer;
            }
        }

        String inherited = inheritedLayer(node, byFullName, fileLayerByPath, cache);
        if (inherited != null) {
            cache.put(node.fullName(), inherited);
            return inherited;
        }

        String heuristic = heuristicLayer(node);
        cache.put(node.fullName(), heuristic);
        return heuristic;
    }

    private static String explicitLayer(NodeData node) {
        if ("DBModel".equals(node.type())) {
            return DOMAIN;
        }
        Object springLayer = node.properties() == null ? null : node.properties().get("springLayer");
        if (!(springLayer instanceof String value) || value.isBlank()) {
            return null;
        }
        return switch (value) {
            case "CONTROLLER" -> PRESENTATION;
            case "SERVICE" -> SERVICE;
            case "REPOSITORY" -> DATA_ACCESS;
            case "ENTITY" -> DOMAIN;
            case "CONFIG", "COMPONENT" -> OTHER;
            default -> null;
        };
    }

    private static String inheritedLayer(NodeData node, Map<String, NodeData> byFullName,
            Map<String, String> fileLayerByPath, Map<String, String> cache) {
        String owner = ownerFullName(node);
        if (owner == null) {
            return null;
        }
        NodeData ownerNode = byFullName.get(owner);
        if (ownerNode == null || ownerNode.fullName().equals(node.fullName())) {
            return null;
        }
        return layerOf(ownerNode, byFullName, fileLayerByPath, cache);
    }

    private static String heuristicLayer(NodeData node) {
        String type = node.type();
        String name = node.name() == null ? "" : node.name();
        if ("APIEndpoint".equals(type) || "Route".equals(type)) {
            return PRESENTATION;
        }
        if ("Record".equals(type) || "Enum".equals(type)) {
            return DOMAIN;
        }
        if ("DBModel".equals(type)) {
            return DOMAIN;
        }
        if ("Method".equals(type) || "Constructor".equals(type) || "Field".equals(type) || "LocalVariable".equals(type)) {
            return OTHER;
        }
        if (matches(name, "Controller", "RestController", "Route")) {
            return PRESENTATION;
        }
        if (matches(name, "Service", "ServiceImpl")) {
            return SERVICE;
        }
        if (matches(name, "Repository", "RepositoryImpl", "Dao", "DAO", "Mapper", "DBModel")) {
            return DATA_ACCESS;
        }
        if (matches(name, "Entity", "Dto", "DTO", "Model", "Record", "Enum")) {
            return DOMAIN;
        }
        if (matches(name, "Config", "Configuration", "Util", "Utils", "Utility", "Aspect")) {
            return OTHER;
        }
        return OTHER;
    }

    private static Map<String, String> fileLayers(List<NodeData> nodes) {
        Map<String, String> result = new HashMap<>();
        Map<String, Integer> scores = new HashMap<>();
        for (NodeData node : nodes) {
            if (node == null || node.filePath() == null || node.filePath().isBlank() || "File".equals(node.type())) {
                continue;
            }
            if (!isFileLayerCandidate(node)) {
                continue;
            }
            String layer = explicitLayer(node);
            if (layer == null) {
                layer = heuristicLayer(node);
            }
            int score = fileLayerScore(layer);
            if (score > scores.getOrDefault(node.filePath(), -1)) {
                scores.put(node.filePath(), score);
                result.put(node.filePath(), layer);
            }
        }
        return result;
    }

    private static boolean isFileLayerCandidate(NodeData node) {
        return "Class".equals(node.type())
                || "Interface".equals(node.type())
                || "Enum".equals(node.type())
                || "Record".equals(node.type())
                || "DBModel".equals(node.type());
    }

    private static int fileLayerScore(String layer) {
        return switch (layer) {
            case PRESENTATION -> 50;
            case SERVICE -> 40;
            case DATA_ACCESS -> 30;
            case DOMAIN -> 20;
            default -> 0;
        };
    }

    private static boolean matches(String value, String... suffixes) {
        for (String suffix : suffixes) {
            if (value.equals(suffix) || value.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static String ownerFullName(NodeData node) {
        if (node == null || node.fullName() == null || node.fullName().isBlank()) {
            return null;
        }
        if ("LocalVariable".equals(node.type())) {
            int hash = node.fullName().indexOf('#');
            return hash > 0 ? node.fullName().substring(0, hash) : null;
        }
        if ("Field".equals(node.type()) || "APIEndpoint".equals(node.type()) || "Route".equals(node.type())) {
            int dot = node.fullName().lastIndexOf('.');
            return dot > 0 ? node.fullName().substring(0, dot) : null;
        }
        if ("Method".equals(node.type()) || "Constructor".equals(node.type())) {
            int paren = node.fullName().indexOf('(');
            int dot = paren > 0 ? node.fullName().lastIndexOf('.', paren) : node.fullName().lastIndexOf('.');
            return dot > 0 ? node.fullName().substring(0, dot) : null;
        }
        return null;
    }
}
