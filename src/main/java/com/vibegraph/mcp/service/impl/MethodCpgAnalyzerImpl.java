package com.vibegraph.mcp.service.impl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.mcp.dto.response.MethodCpgContextResponse;
import com.vibegraph.mcp.dto.response.MethodCpgContextResponse.Candidate;
import com.vibegraph.mcp.dto.response.MethodCpgContextResponse.ControlFlow;
import com.vibegraph.mcp.dto.response.MethodCpgContextResponse.DataFlow;
import com.vibegraph.mcp.dto.response.MethodCpgContextResponse.FlowStep;
import com.vibegraph.mcp.dto.response.MethodCpgContextResponse.NodeRef;
import com.vibegraph.mcp.dto.response.MethodCpgContextResponse.Parameter;
import com.vibegraph.mcp.dto.response.MethodCpgContextResponse.ResolvedMethod;
import com.vibegraph.mcp.dto.response.MethodCpgContextResponse.Signature;
import com.vibegraph.mcp.dto.response.MethodCpgContextResponse.SourceSnippet;
import com.vibegraph.mcp.service.MethodCpgAnalyzer;
import com.vibegraph.mcp.source.GraphView;
import com.vibegraph.mcp.source.SourceFileService;
import com.vibegraph.mcp.source.SourceFileService.SourceContent;
import com.vibegraph.mcp.source.SourceGraphSupport;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MethodCpgAnalyzerImpl implements MethodCpgAnalyzer {

    private static final int MAX_LEN = 512;
    private static final int DEFAULT_MAX_RELATIONS = 100;
    private static final int HARD_CAP_RELATIONS = 500;
    private static final int MAX_CANDIDATES = 20;
    private static final int SOURCE_FALLBACK_WINDOW = 80;
    private static final java.util.Set<String> METHOD_TYPES = java.util.Set.of("Method", "Constructor");

    private final SourceGraphSupport graphSupport;
    private final SourceFileService sourceFileService;

    @Override
    public MethodCpgContextResponse analyzeMethodCpg(String projectId, String methodId, String className,
            String methodName, String query, boolean includeSource, Integer maxRelations, String profile) {
        String normalizedProjectId = validate(projectId, "projectId", MAX_LEN);
        String effectiveQuery = resolveQueryString(methodId, className, methodName, query);
        Profile prof = Profile.from(profile);
        int cap = boundCap(maxRelations);

        GraphView graph = safeLoad(normalizedProjectId);
        if (graph == null) {
            return warning(normalizedProjectId, effectiveQuery, prof, "Method CPG context is temporarily unavailable.");
        }

        GraphView.Resolution resolution = graph.resolve(effectiveQuery, METHOD_TYPES);
        if (resolution.kind() == GraphView.Resolution.Kind.NOT_FOUND) {
            return warning(normalizedProjectId, effectiveQuery, prof, "Method not found: " + effectiveQuery);
        }
        if (resolution.kind() == GraphView.Resolution.Kind.AMBIGUOUS) {
            return ambiguous(normalizedProjectId, effectiveQuery, prof, resolution.candidates());
        }

        NodeDto method = resolution.node();
        List<EdgeDto> outgoing = graph.outgoing(method.getId());

        Map<String, Integer> counts = new LinkedHashMap<>();
        boolean[] truncated = {false};

        DataFlow dataFlow = null;
        ControlFlow controlFlow = null;
        if (prof.includesData()) {
            dataFlow = DataFlow.builder()
                    .reads(refs(graph, outgoing, "READS", cap, counts, "reads", truncated))
                    .writes(refs(graph, outgoing, "WRITES", cap, counts, "writes", truncated))
                    .typeLinks(typeLinks(graph, outgoing, cap, counts, truncated))
                    .build();
        } else {
            countOnly(outgoing, counts, "reads", "READS");
            countOnly(outgoing, counts, "writes", "WRITES");
        }
        if (prof.includesControl()) {
            controlFlow = ControlFlow.builder()
                    .calls(refs(graph, outgoing, "CALLS", cap, counts, "calls", truncated))
                    .flowSteps(flowSteps(graph, outgoing, cap, counts, truncated))
                    .catches(refs(graph, outgoing, "CATCHES", cap, counts, "catches", truncated))
                    .build();
        } else {
            countOnly(outgoing, counts, "calls", "CALLS");
            countOnly(outgoing, counts, "flowSteps", "STEP_IN_FLOW");
            countOnly(outgoing, counts, "catches", "CATCHES");
        }

        String relativePath = relativePath(normalizedProjectId, method.getFilePath());
        SourceSnippet source = includeSource ? sourceSnippet(normalizedProjectId, method) : null;

        List<String> notes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        buildLimitations(method, outgoing, relativePath, truncated[0], notes, warnings);

        return MethodCpgContextResponse.builder()
                .projectId(normalizedProjectId)
                .methodQuery(effectiveQuery)
                .profile(prof.apiValue)
                .resolvedMethod(ResolvedMethod.builder()
                        .id(method.getId())
                        .name(method.getName())
                        .ownerClass(ownerClass(method.getFullName()))
                        .relativePath(relativePath)
                        .lineNumber(method.getLineNumber())
                        .endLine(SourceGraphSupport.endLineOf(method))
                        .visibility(SourceGraphSupport.stringProperty(method, "visibility"))
                        .annotations(listProp(method, "annotations"))
                        .build())
                .signature(signature(method))
                .dataFlow(dataFlow)
                .controlFlow(controlFlow)
                .counts(counts)
                .source(source)
                .truncated(truncated[0])
                .candidates(List.of())
                .warnings(warnings)
                .notes(notes)
                .build();
    }

    private Signature signature(NodeDto method) {
        List<String> paramTypes = listProp(method, "paramTypes");
        List<String> paramNames = listProp(method, "paramNames");
        List<Parameter> parameters = new ArrayList<>();
        for (int i = 0; i < paramTypes.size(); i++) {
            parameters.add(Parameter.builder()
                    .position(i)
                    .type(paramTypes.get(i))
                    .name(i < paramNames.size() ? paramNames.get(i) : null)
                    .build());
        }
        return Signature.builder()
                .returnType(SourceGraphSupport.stringProperty(method, "returnType"))
                .parameters(parameters)
                .thrownTypes(listProp(method, "throwsTypes"))
                .build();
    }

    private List<NodeRef> refs(GraphView graph, List<EdgeDto> edges, String type, int cap,
            Map<String, Integer> counts, String countKey, boolean[] truncated) {
        List<EdgeDto> matching = edges.stream().filter(e -> type.equals(e.getType())).toList();
        counts.put(countKey, matching.size());
        if (matching.size() > cap) {
            truncated[0] = true;
        }
        return matching.stream()
                .limit(cap)
                .map(e -> nodeRef(graph.byId(e.getTarget()), e.getTarget(), type, e.getLineNumber()))
                .filter(Objects::nonNull)
                .toList();
    }

    private List<NodeRef> typeLinks(GraphView graph, List<EdgeDto> edges, int cap,
            Map<String, Integer> counts, boolean[] truncated) {
        List<EdgeDto> matching = edges.stream()
                .filter(e -> "PARAMETER_TYPE".equals(e.getType())
                        || "RETURNS".equals(e.getType()) || "TYPE_OF".equals(e.getType()))
                .toList();
        counts.put("typeLinks", matching.size());
        if (matching.size() > cap) {
            truncated[0] = true;
        }
        return matching.stream()
                .limit(cap)
                .map(e -> nodeRef(graph.byId(e.getTarget()), e.getTarget(), e.getType(), e.getLineNumber()))
                .filter(Objects::nonNull)
                .toList();
    }

    private List<FlowStep> flowSteps(GraphView graph, List<EdgeDto> edges, int cap,
            Map<String, Integer> counts, boolean[] truncated) {
        List<EdgeDto> matching = edges.stream().filter(e -> "STEP_IN_FLOW".equals(e.getType())).toList();
        counts.put("flowSteps", matching.size());
        if (matching.size() > cap) {
            truncated[0] = true;
        }
        List<FlowStep> steps = new ArrayList<>();
        int index = 0;
        for (EdgeDto edge : matching.stream().limit(cap).toList()) {
            NodeDto target = graph.byId(edge.getTarget());
            steps.add(FlowStep.builder()
                    .index(++index)
                    .nodeId(edge.getTarget())
                    .name(target == null ? null : target.getName())
                    .fullName(target == null ? null : target.getFullName())
                    .lineNumber(target == null ? edge.getLineNumber() : target.getLineNumber())
                    .confidence(edge.getConfidence())
                    .relationshipType("STEP_IN_FLOW")
                    .build());
        }
        return steps;
    }

    private void countOnly(List<EdgeDto> edges, Map<String, Integer> counts, String key, String type) {
        counts.put(key, (int) edges.stream().filter(e -> type.equals(e.getType())).count());
    }

    private NodeRef nodeRef(NodeDto node, String fallbackId, String relationshipType, Integer edgeLine) {
        if (node == null) {
            return NodeRef.builder().id(fallbackId).relationshipType(relationshipType).lineNumber(edgeLine).build();
        }
        return NodeRef.builder()
                .id(node.getId())
                .type(node.getType())
                .name(node.getName())
                .fullName(node.getFullName())
                .lineNumber(node.getLineNumber() != null ? node.getLineNumber() : edgeLine)
                .relationshipType(relationshipType)
                .build();
    }

    private SourceSnippet sourceSnippet(String projectId, NodeDto method) {
        if (method.getFilePath() == null || method.getFilePath().isBlank() || method.getLineNumber() == null) {
            return null;
        }
        int start = Math.max(1, method.getLineNumber());
        Integer endLine = SourceGraphSupport.endLineOf(method);
        int end = endLine == null ? start + SOURCE_FALLBACK_WINDOW : Math.max(start, endLine);
        try {
            SourceContent content = sourceFileService.readRange(projectId, method.getFilePath(), start, end);
            if (!content.found()) {
                return null;
            }
            return SourceSnippet.builder()
                    .relativePath(content.relativePath())
                    .startLine(content.startLine())
                    .endLine(content.endLine())
                    .content(content.content())
                    .truncated(content.truncated())
                    .truncationReason(content.truncationReason())
                    .build();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void buildLimitations(NodeDto method, List<EdgeDto> outgoing, String relativePath,
            boolean truncated, List<String> notes, List<String> warnings) {
        boolean hasDeepCpg = outgoing.stream().anyMatch(e ->
                "READS".equals(e.getType()) || "WRITES".equals(e.getType()) || "CATCHES".equals(e.getType()));
        if (!hasDeepCpg) {
            notes.add("No READS/WRITES/CATCHES edges found. Deep CPG is on by default now; this project was "
                    + "likely analyzed before that (or with VIBEGRAPH_PARSER_DEEP_CPG=false) - re-run analyze "
                    + "to populate body-level data flow.");
        }
        if (SourceGraphSupport.endLineOf(method) == null) {
            notes.add("endLine is unknown for this method; source/flow line ranges may be approximate.");
        }
        boolean hasFlow = outgoing.stream().anyMatch(e -> "STEP_IN_FLOW".equals(e.getType()));
        if (hasFlow) {
            notes.add("STEP_IN_FLOW steps are inferred from the CALLS graph and de-duplicated by relation key; "
                    + "confidence is per-edge where available.");
        }
        if (relativePath == null && method.getFilePath() != null && !method.getFilePath().isBlank()) {
            warnings.add("Source root unavailable; returning graph relations without a resolved relative path.");
        }
        if (truncated) {
            warnings.add("Relations were truncated to the requested maxRelations cap.");
        }
    }

    private String relativePath(String projectId, String absoluteFilePath) {
        if (absoluteFilePath == null || absoluteFilePath.isBlank()) {
            return null;
        }
        try {
            Path root = sourceFileService.resolveProjectRoot(projectId);
            Path filePath = Path.of(absoluteFilePath).normalize();
            if (!filePath.startsWith(root)) {
                return null;
            }
            return root.relativize(filePath).toString().replace('\\', '/');
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String resolveQueryString(String methodId, String className, String methodName, String query) {
        if (methodId != null && !methodId.isBlank()) {
            return validate(methodId, "methodId", MAX_LEN);
        }
        if (className != null && !className.isBlank() && methodName != null && !methodName.isBlank()) {
            return validate(className.trim() + "." + methodName.trim(), "className.methodName", MAX_LEN);
        }
        if (query != null && !query.isBlank()) {
            return validate(query, "query", MAX_LEN);
        }
        throw new IllegalArgumentException("Provide one of: methodId, className+methodName, or query");
    }

    private List<String> listProp(NodeDto node, String key) {
        if (node.getProperties() == null) {
            return List.of();
        }
        Object value = node.getProperties().get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String ownerClass(String fullName) {
        String bare = GraphView.stripParens(fullName);
        return bare.contains(".") ? bare.substring(0, bare.lastIndexOf('.')) : null;
    }

    private int boundCap(Integer maxRelations) {
        if (maxRelations == null || maxRelations <= 0) {
            return DEFAULT_MAX_RELATIONS;
        }
        return Math.min(maxRelations, HARD_CAP_RELATIONS);
    }

    private MethodCpgContextResponse ambiguous(String projectId, String query, Profile prof, List<NodeDto> candidates) {
        List<Candidate> mapped = candidates.stream()
                .limit(MAX_CANDIDATES)
                .map(n -> Candidate.builder().id(n.getId()).type(n.getType()).name(n.getName()).fullName(n.getFullName()).build())
                .toList();
        return MethodCpgContextResponse.builder()
                .projectId(projectId).methodQuery(query).profile(prof.apiValue)
                .counts(Map.of()).candidates(mapped)
                .warnings(List.of("Method query is ambiguous; refine using the full signature. Candidates: " + mapped.size()))
                .notes(List.of())
                .build();
    }

    private MethodCpgContextResponse warning(String projectId, String query, Profile prof, String message) {
        return MethodCpgContextResponse.builder()
                .projectId(projectId).methodQuery(query).profile(prof.apiValue)
                .counts(Map.of()).candidates(List.of())
                .warnings(List.of(message)).notes(List.of())
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

    private enum Profile {
        SUMMARY("summary", false, false),
        DATA_FLOW("data-flow", true, false),
        CONTROL_FLOW("control-flow", false, true),
        FULL("full", true, true);

        final String apiValue;
        private final boolean data;
        private final boolean control;

        Profile(String apiValue, boolean data, boolean control) {
            this.apiValue = apiValue;
            this.data = data;
            this.control = control;
        }

        boolean includesData() {
            return data;
        }

        boolean includesControl() {
            return control;
        }

        static Profile from(String value) {
            if (value == null || value.isBlank()) {
                return FULL;
            }
            String v = value.trim().toLowerCase(Locale.ROOT);
            for (Profile p : values()) {
                if (p.apiValue.equals(v)) {
                    return p;
                }
            }
            throw new IllegalArgumentException("profile must be one of: summary, data-flow, control-flow, full");
        }
    }
}
