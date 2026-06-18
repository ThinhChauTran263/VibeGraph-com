package com.vibegraph.mcp.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.mcp.dto.response.RelatedTestsResponse;
import com.vibegraph.mcp.dto.response.RelatedTestsResponse.Candidate;
import com.vibegraph.mcp.dto.response.RelatedTestsResponse.SuggestedCommands;
import com.vibegraph.mcp.dto.response.RelatedTestsResponse.TestMatch;
import com.vibegraph.mcp.dto.response.TestPlanResponse;
import com.vibegraph.mcp.dto.response.TestPlanResponse.TestLevel;
import com.vibegraph.mcp.service.TestIntelligenceAnalyzer;
import com.vibegraph.mcp.source.GraphView;
import com.vibegraph.mcp.source.SourceGraphSupport;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TestIntelligenceAnalyzerImpl implements TestIntelligenceAnalyzer {

    private static final int MAX_LEN = 1024;
    private static final int DEFAULT_MAX_RESULTS = 50;
    private static final int HARD_CAP = 100;
    private static final int MAX_CANDIDATES = 20;
    private static final List<String> TEST_SUFFIXES = List.of("Test", "Tests", "IT", "ITCase", "IntegrationTest");
    private static final Set<String> CLASSLIKE = Set.of("Class", "Interface", "Enum", "Record");
    private static final Set<String> REFERENCE_EDGES = Set.of("IMPORTS", "CALLS", "TYPE_OF", "EXTENDS", "INSTANTIATES", "PARAMETER_TYPE", "RETURNS");

    private final SourceGraphSupport graphSupport;

    // ---- find_related_tests ------------------------------------------------------------------

    @Override
    public RelatedTestsResponse findRelatedTests(String projectId, String nodeId, String className,
            String methodId, String relativePath, String query, Integer maxResults) {
        String normalizedProjectId = validate(projectId, "projectId", 512);
        String effectiveQuery = firstNonBlank(nodeId, methodId, className, query, relativePath);
        if (effectiveQuery == null) {
            throw new IllegalArgumentException("Provide one of: nodeId, className, methodId, relativePath, query");
        }
        effectiveQuery = validate(effectiveQuery, "query", MAX_LEN);
        int cap = bound(maxResults);

        GraphView graph = safeLoad(normalizedProjectId);
        if (graph == null) {
            return relatedWarning(normalizedProjectId, effectiveQuery, "Test intelligence is temporarily unavailable.");
        }

        ResolvedContext ctx = resolveTarget(graph, nodeId, methodId, className, query, relativePath);
        if (ctx == null) {
            return relatedWarning(normalizedProjectId, effectiveQuery, "Target not found: " + effectiveQuery);
        }
        if (ctx.ambiguous != null) {
            return ambiguousRelated(normalizedProjectId, effectiveQuery, ctx.ambiguous);
        }

        Map<String, TestMatch> matchesById = new LinkedHashMap<>();
        collectGraphReferenceTests(graph, ctx, matchesById);
        collectNamingConventionTests(graph, ctx, matchesById);

        List<TestMatch> all = matchesById.values().stream()
                .sorted((a, b) -> {
                    int byConf = Integer.compare(confidenceRank(a.getConfidence()), confidenceRank(b.getConfidence()));
                    if (byConf != 0) {
                        return byConf;
                    }
                    return safe(a.getFullName()).compareTo(safe(b.getFullName()));
                })
                .toList();
        int total = all.size();
        List<TestMatch> returned = all.stream().limit(cap).toList();

        List<String> gaps = new ArrayList<>();
        if (total == 0) {
            gaps.add("No direct or naming-convention tests found for " + safe(ctx.simpleName)
                    + ". Suggested new test: " + ctx.simpleName + "Test.");
        } else if (all.stream().noneMatch(m -> "GRAPH_REFERENCE".equals(m.getMatchType()))) {
            gaps.add("No test references this symbol in the graph; matches are naming-convention only.");
        }
        if (ctx.frontend) {
            gaps.add("Target looks like frontend code; backend Maven commands may not apply.");
        }

        return RelatedTestsResponse.builder()
                .projectId(normalizedProjectId)
                .query(effectiveQuery)
                .resolvedTarget(RelatedTestsResponse.ResolvedTarget.builder()
                        .id(ctx.node == null ? null : ctx.node.getId())
                        .type(ctx.node == null ? null : ctx.node.getType())
                        .name(ctx.simpleName)
                        .fullName(ctx.node == null ? null : ctx.node.getFullName())
                        .relativePath(ctx.relativePath)
                        .frontend(ctx.frontend)
                        .build())
                .matches(returned)
                .totalMatches(total)
                .returnedMatches(returned.size())
                .truncated(total > returned.size())
                .suggestedCommands(commandsFor(ctx, returned))
                .candidates(List.of())
                .gaps(gaps)
                .warnings(List.of())
                .notes(List.of("Confidence: GRAPH_REFERENCE=high (graph edge), NAMING_CONVENTION=medium."))
                .build();
    }

    private void collectGraphReferenceTests(GraphView graph, ResolvedContext ctx, Map<String, TestMatch> out) {
        if (ctx.targetIds.isEmpty()) {
            return;
        }
        for (EdgeDto edge : graph.edges()) {
            if (!REFERENCE_EDGES.contains(edge.getType()) || !ctx.targetIds.contains(edge.getTarget())) {
                continue;
            }
            NodeDto source = graph.byId(edge.getSource());
            if (source == null || !isTestNode(source)) {
                continue;
            }
            out.putIfAbsent(source.getId(), TestMatch.builder()
                    .id(source.getId())
                    .type(source.getType())
                    .name(source.getName())
                    .fullName(source.getFullName())
                    .relativePath(relativeOf(source))
                    .matchType("GRAPH_REFERENCE")
                    .confidence("HIGH")
                    .evidence(edge.getType() + " edge to target")
                    .build());
        }
    }

    private void collectNamingConventionTests(GraphView graph, ResolvedContext ctx, Map<String, TestMatch> out) {
        if (ctx.simpleName == null) {
            return;
        }
        Set<String> expected = TEST_SUFFIXES.stream()
                .map(suffix -> ctx.simpleName + suffix)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (NodeDto node : graph.nodes()) {
            if (!CLASSLIKE.contains(node.getType()) || node.getName() == null) {
                continue;
            }
            if (expected.contains(node.getName()) && isTestNode(node)) {
                out.putIfAbsent(node.getId(), TestMatch.builder()
                        .id(node.getId())
                        .type(node.getType())
                        .name(node.getName())
                        .fullName(node.getFullName())
                        .relativePath(relativeOf(node))
                        .matchType("NAMING_CONVENTION")
                        .confidence("MEDIUM")
                        .evidence("Name matches " + ctx.simpleName + "<suffix>")
                        .build());
            }
        }
    }

    private SuggestedCommands commandsFor(ResolvedContext ctx, List<TestMatch> matches) {
        if (ctx.frontend) {
            return SuggestedCommands.builder()
                    .frontend("npm --prefix vibegraph-web run test:unit")
                    .build();
        }
        List<String> classNames = matches.stream()
                .filter(m -> m.getName() != null && TEST_SUFFIXES.stream().anyMatch(m.getName()::endsWith))
                .map(TestMatch::getName)
                .distinct()
                .limit(10)
                .toList();
        String selector = classNames.isEmpty() ? ctx.simpleName + "Test" : String.join(",", classNames);
        return SuggestedCommands.builder()
                .windows(".\\mvnw.cmd -q \"-Dtest=" + selector + "\" test")
                .unix("./mvnw -q -Dtest=" + selector + " test")
                .build();
    }

    // ---- suggest_test_plan -------------------------------------------------------------------

    @Override
    public TestPlanResponse suggestTestPlan(String projectId, String changeDescription, List<String> targetNodes,
            List<String> files, String riskTolerance) {
        String normalizedProjectId = validate(projectId, "projectId", 512);
        String change = validateMultiline(changeDescription, "changeDescription", 4000);
        String risk = normalizeRisk(riskTolerance);
        String lower = change.toLowerCase(Locale.ROOT);
        List<String> allFiles = files == null ? List.of() : files;
        boolean frontend = allFiles.stream().anyMatch(this::looksFrontend)
                || lower.contains("vue") || lower.contains("frontend") || lower.contains("component");
        boolean persistence = lower.contains("repository") || lower.contains("neo4j") || lower.contains("cypher")
                || lower.contains("persist") || lower.contains("database") || lower.contains("entity");
        boolean endpoint = lower.contains("endpoint") || lower.contains("api") || lower.contains("controller")
                || lower.contains("route") || lower.contains("mcp");

        List<TestLevel> levels = new ArrayList<>();
        levels.add(TestLevel.builder()
                .level("unit")
                .command(".\\mvnw.cmd -q \"-Dtest=<RelevantTest>\" test   (unix: ./mvnw -q -Dtest=<RelevantTest> test)")
                .rationale("Cover the changed class/method logic in isolation.")
                .failureImplication("Core logic regression in the changed unit.")
                .build());
        if (persistence) {
            levels.add(TestLevel.builder()
                    .level("integration/Testcontainers")
                    .command(".\\mvnw.cmd verify   (runs *IT with Testcontainers Neo4j; requires Docker)")
                    .rationale("Change touches persistence/Neo4j; verify real Cypher and mapping.")
                    .failureImplication("Schema/query/mapping mismatch against a real database.")
                    .build());
        }
        if (endpoint) {
            levels.add(TestLevel.builder()
                    .level("mcp-live / api")
                    .command("Start backend, POST /mcp tools/list + tools/call, or curl the REST endpoint.")
                    .rationale("Change affects an endpoint/MCP tool; verify the wire contract end to end.")
                    .failureImplication("Broken request/response contract or routing/registration.")
                    .build());
        }
        if (frontend) {
            levels.add(TestLevel.builder()
                    .level("frontend-unit")
                    .command("npm --prefix vibegraph-web run test:unit; run type-check, lint, build-only")
                    .rationale("Change touches Vue/TS; verify components, types, lint, and build.")
                    .failureImplication("Component/regression or type/build break in the frontend.")
                    .build());
            levels.add(TestLevel.builder()
                    .level("browser-smoke")
                    .command("Manual or Playwright smoke of the affected screen at key breakpoints.")
                    .rationale("Visual/interaction regressions are not caught by unit tests.")
                    .failureImplication("Broken rendering or interaction in the running app.")
                    .build());
        }
        if (!"low".equals(risk)) {
            // medium/high risk tolerance: keep the plan lean; drop optional levels for high.
            if ("high".equals(risk) && levels.size() > 2) {
                levels = new ArrayList<>(levels.subList(0, 2));
            }
        }

        List<String> notCovered = new ArrayList<>();
        if (!persistence) {
            notCovered.add("Database/integration behavior (no persistence change detected).");
        }
        if (!frontend) {
            notCovered.add("Frontend/UI behavior (no frontend change detected).");
        }
        notCovered.add("Performance/load characteristics are not addressed by this plan.");

        List<String> notes = new ArrayList<>();
        notes.add("Risk tolerance: " + risk + ". Replace <RelevantTest> using find_related_tests.");
        notes.add("Commands are advisory; verify the project's actual test setup before running.");
        if (targetNodes != null && !targetNodes.isEmpty()) {
            notes.add("Target nodes considered: " + targetNodes.size());
        }

        return TestPlanResponse.builder()
                .projectId(normalizedProjectId)
                .changeDescription(change)
                .riskTolerance(risk)
                .recommendedLevels(levels)
                .notCovered(notCovered)
                .warnings(List.of())
                .notes(notes)
                .build();
    }

    // ---- resolution & helpers ----------------------------------------------------------------

    private ResolvedContext resolveTarget(GraphView graph, String nodeId, String methodId, String className,
            String query, String relativePath) {
        String q = firstNonBlank(nodeId, methodId, className, query);
        ResolvedContext ctx = new ResolvedContext();
        if (q != null) {
            GraphView.Resolution res = graph.resolve(q.trim(), null);
            if (res.kind() == GraphView.Resolution.Kind.AMBIGUOUS) {
                ctx.ambiguous = res.candidates();
                return ctx;
            }
            if (res.isUnique()) {
                ctx.node = res.node();
            }
        }
        if (ctx.node == null && relativePath != null && !relativePath.isBlank()) {
            String rp = relativePath.trim().replace('\\', '/');
            ctx.node = graph.nodes().stream()
                    .filter(n -> CLASSLIKE.contains(n.getType()))
                    .filter(n -> rp.equals(relativeOf(n)))
                    .findFirst().orElse(null);
            ctx.relativePath = rp;
            ctx.frontend = looksFrontend(rp);
            if (ctx.node == null && !ctx.frontend) {
                // path with no class node: derive simple name from filename
                ctx.simpleName = simpleNameFromPath(rp);
                ctx.targetIds = Set.of();
                return ctx.simpleName == null ? null : ctx;
            }
        }
        if (ctx.node == null) {
            return ctx.simpleName != null ? ctx : null;
        }
        // owning class node
        NodeDto classNode = owningClass(graph, ctx.node);
        ctx.simpleName = classNode != null ? classNode.getName() : ctx.node.getName();
        ctx.relativePath = ctx.relativePath != null ? ctx.relativePath : relativeOf(classNode != null ? classNode : ctx.node);
        ctx.frontend = ctx.relativePath != null && looksFrontend(ctx.relativePath);
        ctx.targetIds = targetIds(graph, classNode != null ? classNode : ctx.node);
        return ctx;
    }

    private NodeDto owningClass(GraphView graph, NodeDto node) {
        if (CLASSLIKE.contains(node.getType())) {
            return node;
        }
        String bare = GraphView.stripParens(node.getFullName());
        String ownerFqn = bare.contains(".") ? bare.substring(0, bare.lastIndexOf('.')) : null;
        if (ownerFqn == null) {
            return null;
        }
        return graph.nodes().stream()
                .filter(n -> CLASSLIKE.contains(n.getType()))
                .filter(n -> ownerFqn.equals(n.getFullName()))
                .findFirst().orElse(null);
    }

    private Set<String> targetIds(GraphView graph, NodeDto classNode) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(classNode.getId());
        String prefix = classNode.getFullName() == null ? null : classNode.getFullName() + ".";
        if (prefix != null) {
            for (NodeDto n : graph.nodes()) {
                if (n.getFullName() != null && n.getFullName().startsWith(prefix)) {
                    ids.add(n.getId());
                }
            }
        }
        return ids;
    }

    private boolean isTestNode(NodeDto node) {
        String rp = relativeOf(node);
        if (rp != null) {
            String norm = rp.toLowerCase(Locale.ROOT);
            if (norm.contains("src/test/") || norm.contains("/test/java/") || norm.contains("test/java/")) {
                return true;
            }
        }
        String name = node.getName();
        return name != null && TEST_SUFFIXES.stream().anyMatch(name::endsWith);
    }

    private String relativeOf(NodeDto node) {
        if (node == null || node.getFilePath() == null || node.getFilePath().isBlank()) {
            return null;
        }
        String fp = node.getFilePath().replace('\\', '/');
        int idx = fp.indexOf("/src/");
        if (idx >= 0) {
            return fp.substring(idx + 1);
        }
        int web = fp.indexOf("/vibegraph-web/");
        if (web >= 0) {
            return fp.substring(web + 1);
        }
        return fp.substring(fp.lastIndexOf('/') + 1);
    }

    private boolean looksFrontend(String path) {
        if (path == null) {
            return false;
        }
        String p = path.toLowerCase(Locale.ROOT);
        return p.endsWith(".vue") || p.endsWith(".ts") || p.endsWith(".tsx") || p.endsWith(".js")
                || p.contains("vibegraph-web/");
    }

    private String simpleNameFromPath(String path) {
        String file = path.substring(path.lastIndexOf('/') + 1);
        int dot = file.indexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }

    private RelatedTestsResponse ambiguousRelated(String projectId, String query, List<NodeDto> candidates) {
        List<Candidate> mapped = candidates.stream().limit(MAX_CANDIDATES)
                .map(n -> Candidate.builder().id(n.getId()).type(n.getType()).name(n.getName()).fullName(n.getFullName()).build())
                .toList();
        return RelatedTestsResponse.builder()
                .projectId(projectId).query(query)
                .matches(List.of()).totalMatches(0).returnedMatches(0).truncated(false)
                .candidates(mapped).gaps(List.of())
                .warnings(List.of("Target query is ambiguous; refine it. Candidates: " + mapped.size()))
                .notes(List.of())
                .build();
    }

    private RelatedTestsResponse relatedWarning(String projectId, String query, String message) {
        return RelatedTestsResponse.builder()
                .projectId(projectId).query(query)
                .matches(List.of()).totalMatches(0).returnedMatches(0).truncated(false)
                .candidates(List.of()).gaps(List.of())
                .warnings(List.of(message)).notes(List.of())
                .build();
    }

    private int confidenceRank(String confidence) {
        return switch (confidence) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private String normalizeRisk(String riskTolerance) {
        if (riskTolerance == null || riskTolerance.isBlank()) {
            return "medium";
        }
        String r = riskTolerance.trim().toLowerCase(Locale.ROOT);
        return switch (r) {
            case "low", "medium", "high" -> r;
            default -> throw new IllegalArgumentException("riskTolerance must be low, medium, or high");
        };
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private int bound(Integer maxResults) {
        if (maxResults == null || maxResults <= 0) {
            return DEFAULT_MAX_RESULTS;
        }
        return Math.min(maxResults, HARD_CAP);
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

    /** Like {@link #validate} but tolerates newlines/tabs for free-form multi-line text. */
    private String validateMultiline(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || value.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
            throw new IllegalArgumentException(
                    field + " must be non-blank and at most " + maxLength + " characters");
        }
        return value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class ResolvedContext {
        NodeDto node;
        String simpleName;
        String relativePath;
        boolean frontend;
        Set<String> targetIds = Set.of();
        List<NodeDto> ambiguous;
    }
}
