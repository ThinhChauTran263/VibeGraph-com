package com.vibegraph.diagram.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import com.vibegraph.diagram.dto.response.DiagramResponse;
import com.vibegraph.diagram.service.ClassDiagramService;
import com.vibegraph.diagram.service.MermaidGeneratorService;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.service.GraphService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@inheritDoc}
 *
 * <p>Reads the project graph through {@link GraphService#getFullGraph(String)}
 * (no direct Neo4j access) and renders a Mermaid {@code classDiagram}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClassDiagramServiceImpl implements ClassDiagramService {

    private static final String DIAGRAM_TYPE = "class";
    private static final String NL = "\n";
    private static final String INDENT = "    ";
    /** Keep diagrams readable/bounded (see MODULE-GUIDE size limit). */
    private static final int MAX_CLASSES = 50;

    private static final Set<String> CLASSIFIER_TYPES = Set.of("Class", "Interface", "Enum");

    private final GraphService graphService;
    private final MermaidGeneratorService mermaid;

    @Override
    public DiagramResponse generateClassDiagram(String projectId, String packageFilter) {
        GraphDataResponse graph = graphService.getFullGraph(projectId);

        List<NodeDto> nodes = graph != null && graph.getNodes() != null ? graph.getNodes() : List.of();
        List<EdgeDto> edges = graph != null && graph.getEdges() != null ? graph.getEdges() : List.of();

        Map<String, NodeDto> nodesById = new HashMap<>();
        for (NodeDto node : nodes) {
            if (node != null && node.getId() != null) {
                nodesById.put(node.getId(), node);
            }
        }

        String filter = packageFilter == null ? "" : packageFilter.trim();

        // Distinct packages across ALL classifiers (independent of the active filter), so the
        // UI can present the filter as a pick-list instead of a guess-the-name text box.
        Set<String> availablePackages = new TreeSet<>();
        for (NodeDto node : nodes) {
            if (node == null || node.getType() == null || !CLASSIFIER_TYPES.contains(node.getType())) {
                continue;
            }
            String fullName = node.getFullName();
            if (fullName == null || fullName.isBlank()) {
                continue;
            }
            String pkg = packageOf(fullName);
            if (!pkg.isBlank()) {
                availablePackages.add(pkg);
            }
        }

        // Selected classifiers, deterministically ordered by fullName, capped.
        Map<String, NodeDto> selected = new TreeMap<>();
        for (NodeDto node : nodes) {
            if (node == null || node.getType() == null || !CLASSIFIER_TYPES.contains(node.getType())) {
                continue;
            }
            String fullName = node.getFullName();
            if (fullName == null || fullName.isBlank()) {
                continue;
            }
            if (matchesPackage(fullName, filter)) {
                selected.put(fullName, node);
            }
        }
        if (selected.size() > MAX_CLASSES) {
            Map<String, NodeDto> capped = new TreeMap<>();
            int count = 0;
            for (Map.Entry<String, NodeDto> e : selected.entrySet()) {
                if (count++ >= MAX_CLASSES) {
                    break;
                }
                capped.put(e.getKey(), e.getValue());
            }
            log.debug("Class diagram capped from {} to {} classes (project={}, filter='{}')",
                    selected.size(), MAX_CLASSES, projectId, filter);
            selected = capped;
        }

        // Stable Mermaid id per classifier (deduped sanitized simple name).
        Map<String, String> idByFullName = assignClassIds(selected);

        // Members keyed by owner fullName.
        Map<String, Set<String>> methodsByOwner = collectMembers(edges, nodesById, "HAS_METHOD", selected.keySet(), this::renderMethod);
        Map<String, Set<String>> fieldsByOwner = collectMembers(edges, nodesById, "HAS_FIELD", selected.keySet(), this::renderField);

        String syntax = buildMermaid(selected, idByFullName, methodsByOwner, fieldsByOwner, edges);

        return DiagramResponse.builder()
                .diagramType(DIAGRAM_TYPE)
                .mermaidSyntax(syntax)
                .availablePackages(new ArrayList<>(availablePackages))
                .build();
    }

    private boolean matchesPackage(String fullName, String filter) {
        if (filter.isEmpty()) {
            return true;
        }
        String pkg = packageOf(fullName);
        return pkg.equals(filter) || pkg.startsWith(filter + ".");
    }

    private String packageOf(String fullName) {
        int idx = fullName.lastIndexOf('.');
        return idx < 0 ? "" : fullName.substring(0, idx);
    }

    private Map<String, String> assignClassIds(Map<String, NodeDto> selected) {
        Map<String, String> idByFullName = new TreeMap<>();
        Set<String> used = new HashSet<>();
        for (Map.Entry<String, NodeDto> entry : selected.entrySet()) {
            NodeDto node = entry.getValue();
            String base = node.getName() != null && !node.getName().isBlank()
                    ? node.getName()
                    : entry.getKey();
            String id = uniqueId(mermaid.sanitizeId(base), used);
            idByFullName.put(entry.getKey(), id);
        }
        return idByFullName;
    }

    private interface MemberRenderer {
        String render(NodeDto memberNode);
    }

    private Map<String, Set<String>> collectMembers(
            List<EdgeDto> edges,
            Map<String, NodeDto> nodesById,
            String edgeType,
            Set<String> owners,
            MemberRenderer renderer) {
        Map<String, Set<String>> byOwner = new HashMap<>();
        for (EdgeDto edge : edges) {
            if (edge == null || !edgeType.equals(edge.getType())) {
                continue;
            }
            String owner = edge.getSource();
            if (owner == null || !owners.contains(owner)) {
                continue;
            }
            NodeDto memberNode = nodesById.get(edge.getTarget());
            if (memberNode == null) {
                continue;
            }
            String line = renderer.render(memberNode);
            if (line != null && !line.isBlank()) {
                // TreeSet → deterministic ordering + natural dedupe of overloads
                // that render identically.
                byOwner.computeIfAbsent(owner, k -> new TreeSet<>()).add(line);
            }
        }
        return byOwner;
    }

    private String renderMethod(NodeDto method) {
        String visibility = visibilityMarker(stringProp(method, "visibility"));
        String name = safeMember(method.getName());
        String params = String.join(", ", typeList(listProp(method, "paramTypes")));
        String returnType = safeType(stringProp(method, "returnType"));
        StringBuilder sb = new StringBuilder();
        sb.append(visibility).append(name).append("(").append(params).append(")");
        if (!returnType.isBlank()) {
            sb.append(" ").append(returnType);
        }
        return sb.toString();
    }

    private String renderField(NodeDto field) {
        String visibility = visibilityMarker(stringProp(field, "visibility"));
        String name = safeMember(field.getName());
        String declaredType = safeType(stringProp(field, "declaredType"));
        if (declaredType.isBlank()) {
            return visibility + name;
        }
        return visibility + declaredType + " " + name;
    }

    private String buildMermaid(
            Map<String, NodeDto> selected,
            Map<String, String> idByFullName,
            Map<String, Set<String>> methodsByOwner,
            Map<String, Set<String>> fieldsByOwner,
            List<EdgeDto> edges) {

        StringBuilder sb = new StringBuilder();
        sb.append("classDiagram").append(NL);

        if (selected.isEmpty()) {
            sb.append(INDENT).append("%% No classes detected for this project");
            return sb.toString();
        }

        for (Map.Entry<String, NodeDto> entry : selected.entrySet()) {
            String fullName = entry.getKey();
            NodeDto node = entry.getValue();
            String classId = idByFullName.get(fullName);

            sb.append(INDENT).append("class ").append(classId).append(" {").append(NL);

            String stereotype = stereotypeFor(node);
            if (stereotype != null) {
                sb.append(INDENT).append(INDENT).append(stereotype).append(NL);
            }
            for (String field : fieldsByOwner.getOrDefault(fullName, Set.of())) {
                sb.append(INDENT).append(INDENT).append(field).append(NL);
            }
            for (String method : methodsByOwner.getOrDefault(fullName, Set.of())) {
                sb.append(INDENT).append(INDENT).append(method).append(NL);
            }
            sb.append(INDENT).append("}").append(NL);
        }

        for (String relation : relationships(edges, idByFullName)) {
            sb.append(INDENT).append(relation).append(NL);
        }

        if (sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private List<String> relationships(List<EdgeDto> edges, Map<String, String> idByFullName) {
        // TreeSet for deterministic ordering + dedupe.
        Set<String> rels = new TreeSet<>();
        for (EdgeDto edge : edges) {
            if (edge == null) {
                continue;
            }
            String src = idByFullName.get(edge.getSource());
            String tgt = idByFullName.get(edge.getTarget());
            if (src == null || tgt == null) {
                continue; // endpoint outside the selected/filtered set
            }
            switch (edge.getType()) {
                case "EXTENDS" -> rels.add(src + " --|> " + tgt + " : extends");
                case "IMPLEMENTS" -> rels.add(src + " ..|> " + tgt + " : implements");
                case "INJECTS" -> rels.add(src + " --> " + tgt + " : uses");
                default -> {
                    // ignore non-structural edges
                }
            }
        }
        return new ArrayList<>(rels);
    }

    private String stereotypeFor(NodeDto node) {
        String type = node.getType();
        if ("Interface".equals(type)) {
            return "<<interface>>";
        }
        if ("Enum".equals(type)) {
            return "<<enumeration>>";
        }
        if ("Class".equals(type) && Boolean.TRUE.equals(node.getProperties() != null
                ? node.getProperties().get("abstract") : null)) {
            return "<<abstract>>";
        }
        String layer = stringProp(node, "springLayer");
        if (!layer.isBlank() && !"NONE".equals(layer)) {
            return "<<" + layer + ">>";
        }
        return null;
    }

    private String visibilityMarker(String visibility) {
        if (visibility == null) {
            return "~";
        }
        return switch (visibility) {
            case "public" -> "+";
            case "private" -> "-";
            case "protected" -> "#";
            default -> "~";
        };
    }

    /** Member identifiers are Java names; strip anything that could break a member line. */
    private String safeMember(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[^A-Za-z0-9_$]", "");
    }

    /**
     * Mermaid classDiagram member lines are not quotable, so generics ({@code <>})
     * and stray separators must be neutralised rather than escaped. Generic args
     * are dropped (e.g. {@code List<String>} → {@code List}) for a clean,
     * render-safe display.
     */
    private String safeType(String raw) {
        if (raw == null) {
            return "";
        }
        String noGenerics = raw.replaceAll("<[^<>]*>", "");
        // Re-run to flatten nested generics that survived the first pass.
        while (noGenerics.contains("<")) {
            String next = noGenerics.replaceAll("<[^<>]*>", "");
            if (next.equals(noGenerics)) {
                next = noGenerics.replace("<", "").replace(">", "");
            }
            noGenerics = next;
        }
        return noGenerics.replaceAll("[{}\":~]", "").trim();
    }

    private List<String> typeList(List<String> rawTypes) {
        List<String> out = new ArrayList<>(rawTypes.size());
        for (String raw : rawTypes) {
            String t = safeType(raw);
            if (!t.isBlank()) {
                out.add(t);
            }
        }
        return out;
    }

    private String stringProp(NodeDto node, String key) {
        if (node.getProperties() == null) {
            return "";
        }
        Object value = node.getProperties().get(key);
        return value == null ? "" : value.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> listProp(NodeDto node, String key) {
        if (node.getProperties() == null) {
            return List.of();
        }
        Object value = node.getProperties().get(key);
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o != null) {
                    out.add(o.toString());
                }
            }
            return out;
        }
        return List.of();
    }

    private String uniqueId(String base, Set<String> used) {
        if (used.add(base)) {
            return base;
        }
        int suffix = 2;
        String candidate = base + "_" + suffix;
        while (!used.add(candidate)) {
            suffix++;
            candidate = base + "_" + suffix;
        }
        return candidate;
    }
}
