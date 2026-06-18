package com.vibegraph.mcp.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.response.ImpactAnalysisResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.mcp.dto.response.CodeChangePlanResponse;
import com.vibegraph.mcp.dto.response.CodeChangePlanResponse.CandidateFile;
import com.vibegraph.mcp.dto.response.CodeChangePlanResponse.CandidateSymbol;
import com.vibegraph.mcp.dto.response.CodeChangePlanResponse.ImpactSummary;
import com.vibegraph.mcp.dto.response.CodeChangePlanResponse.SourceSnippet;
import com.vibegraph.mcp.dto.response.SourceSearchResponse;
import com.vibegraph.mcp.dto.response.TestPlanResponse;
import com.vibegraph.mcp.service.CodeChangePlanner;
import com.vibegraph.mcp.service.SourceSearchAnalyzer;
import com.vibegraph.mcp.service.TestIntelligenceAnalyzer;
import com.vibegraph.mcp.source.GraphView;
import com.vibegraph.mcp.source.SourceFileService;
import com.vibegraph.mcp.source.SourceFileService.SourceContent;
import com.vibegraph.mcp.source.SourceGraphSupport;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CodeChangePlannerImpl implements CodeChangePlanner {

    private static final int MAX_REQUEST_LEN = 4000;
    private static final int DEFAULT_MAX_FILES = 20;
    private static final int HARD_CAP_FILES = 50;
    private static final int MAX_KEYWORDS = 6;
    private static final int PER_KEYWORD_RESULTS = 20;
    private static final int MAX_SYMBOLS = 30;
    private static final int MAX_SNIPPETS = 3;
    private static final int SNIPPET_LINES = 60;
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{2,}");
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "add", "new", "with", "into", "from", "that", "this", "change", "update",
            "support", "make", "should", "when", "method", "class", "field", "want", "need", "able", "code");
    private static final Set<String> CLASSLIKE = Set.of("Class", "Interface", "Enum", "Record");

    private final SourceGraphSupport graphSupport;
    private final SourceSearchAnalyzer sourceSearchAnalyzer;
    private final TestIntelligenceAnalyzer testIntelligenceAnalyzer;
    private final GraphService graphService;
    private final SourceFileService sourceFileService;

    @Override
    public CodeChangePlanResponse planCodeChange(String projectId, String changeRequest, List<String> entrypoints,
            List<String> targetNodes, Integer maxFiles, boolean includeSourceSnippets) {
        String normalizedProjectId = validate(projectId, "projectId", 512);
        String request = validateMultiline(changeRequest, "changeRequest", MAX_REQUEST_LEN);
        int fileCap = boundFiles(maxFiles);

        GraphView graph = safeLoad(normalizedProjectId);
        List<String> warnings = new ArrayList<>();
        List<String> evidence = new ArrayList<>();

        List<String> keywords = keywords(request, entrypoints);
        if (keywords.isEmpty()) {
            keywords = new ArrayList<>();
        }

        // 1) candidate files via literal source search (bounded), aggregated by file.
        Map<String, FileScore> fileScores = new LinkedHashMap<>();
        Set<String> matchedNodeIds = new LinkedHashSet<>();
        for (String keyword : keywords) {
            SourceSearchResponse search;
            try {
                search = sourceSearchAnalyzer.searchSource(normalizedProjectId, keyword, null, null, PER_KEYWORD_RESULTS);
            } catch (RuntimeException ex) {
                continue;
            }
            if (search.getMatches() == null) {
                continue;
            }
            for (SourceSearchResponse.Match match : search.getMatches()) {
                FileScore fs = fileScores.computeIfAbsent(match.getRelativePath(), k -> new FileScore());
                fs.hits++;
                fs.keywords.add(keyword);
                if (match.getNodeId() != null) {
                    matchedNodeIds.add(match.getNodeId());
                }
            }
        }
        evidence.add("Searched " + keywords.size() + " keyword(s); matched " + fileScores.size() + " file(s).");

        List<CandidateFile> candidateFiles = fileScores.entrySet().stream()
                .map(e -> CandidateFile.builder()
                        .relativePath(e.getKey())
                        .score(e.getValue().hits * 10 + e.getValue().keywords.size())
                        .reason("Matched keyword(s): " + String.join(", ", e.getValue().keywords))
                        .build())
                .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
                .limit(fileCap)
                .toList();

        // 2) candidate symbols: from matched nodes + graph name matches + explicit targetNodes.
        List<CandidateSymbol> candidateSymbols = candidateSymbols(graph, matchedNodeIds, keywords, targetNodes);

        // 3) impact on the top class-like candidate symbol.
        ImpactSummary impact = topImpact(normalizedProjectId, candidateSymbols, warnings);

        // 4) endpoint awareness.
        boolean endpointy = request.toLowerCase(Locale.ROOT).matches(".*(endpoint|route|api|controller|http|get |post |put |delete ).*");
        if (endpointy && graph != null) {
            long endpoints = graph.nodes().stream().filter(n -> "APIEndpoint".equals(n.getType()) || "Route".equals(n.getType())).count();
            evidence.add("Request mentions an endpoint; project has " + endpoints + " route node(s). Use trace_endpoint for exact flow.");
        }

        // 5) test plan (reuse the test intelligence analyzer).
        List<String> testPlan = testPlan(normalizedProjectId, request, targetNodes,
                candidateFiles.stream().map(CandidateFile::getRelativePath).toList());

        // 6) optional bounded source snippets for the very top files.
        List<SourceSnippet> snippets = includeSourceSnippets
                ? snippets(normalizedProjectId, candidateFiles)
                : List.of();

        String confidence = confidence(candidateFiles, candidateSymbols);
        List<String> openQuestions = new ArrayList<>();
        if ("LOW".equals(confidence)) {
            openQuestions.add("The change request did not map to specific files/symbols. Can you name a class, endpoint, or file?");
        }
        if (candidateFiles.size() > 1) {
            openQuestions.add("Multiple candidate files matched; confirm which area is in scope before editing.");
        }
        if (graph == null) {
            warnings.add("Graph unavailable or too large; symbol/impact evidence is limited.");
        }

        return CodeChangePlanResponse.builder()
                .projectId(normalizedProjectId)
                .changeRequest(request)
                .summary(summary(candidateFiles, confidence))
                .confidence(confidence)
                .candidateFiles(candidateFiles)
                .candidateSymbols(candidateSymbols)
                .impact(impact)
                .proposedSteps(proposedSteps(candidateFiles))
                .testPlan(testPlan)
                .risks(risks(impact, endpointy))
                .openQuestions(openQuestions)
                .doNotTouch(List.of("Avoid editing files not in candidateFiles unless evidence links them to this change.",
                        "Do not change unrelated public APIs or shared config without explicit need."))
                .evidence(evidence)
                .sourceSnippets(snippets)
                .warnings(warnings)
                .notes(List.of("This plan is reconnaissance only; it does not modify code. Verify candidates before editing."))
                .build();
    }

    private List<CandidateSymbol> candidateSymbols(GraphView graph, Set<String> matchedNodeIds,
            List<String> keywords, List<String> targetNodes) {
        if (graph == null) {
            return List.of();
        }
        Map<String, CandidateSymbol> byId = new LinkedHashMap<>();
        for (String id : matchedNodeIds) {
            NodeDto node = graph.byId(id);
            if (node != null) {
                byId.putIfAbsent(node.getId(), toSymbol(node));
            }
        }
        if (targetNodes != null) {
            for (String t : targetNodes) {
                if (t == null || t.isBlank()) {
                    continue;
                }
                GraphView.Resolution res = graph.resolve(t.trim(), null);
                if (res.isUnique()) {
                    byId.putIfAbsent(res.node().getId(), toSymbol(res.node()));
                }
            }
        }
        Set<String> kw = new LinkedHashSet<>(keywords);
        for (NodeDto node : graph.nodes()) {
            if (byId.size() >= MAX_SYMBOLS) {
                break;
            }
            if (CLASSLIKE.contains(node.getType()) && node.getName() != null && kw.contains(node.getName())) {
                byId.putIfAbsent(node.getId(), toSymbol(node));
            }
        }
        return byId.values().stream().limit(MAX_SYMBOLS).toList();
    }

    private ImpactSummary topImpact(String projectId, List<CandidateSymbol> symbols, List<String> warnings) {
        String targetFullName = symbols.stream()
                .filter(s -> CLASSLIKE.contains(s.getType()))
                .map(CandidateSymbol::getFullName)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (targetFullName == null) {
            return null;
        }
        try {
            ImpactAnalysisResponse impact = graphService.getImpactAnalysis(projectId, targetFullName, 2);
            return ImpactSummary.builder()
                    .targetFullName(targetFullName)
                    .directDependents(impact.getDirectDependents())
                    .totalDependents(impact.getTotalDependents())
                    .riskLevel(impact.getRiskLevel())
                    .build();
        } catch (RuntimeException ex) {
            warnings.add("Impact analysis could not be computed for " + targetFullName + ".");
            return null;
        }
    }

    private List<String> testPlan(String projectId, String request, List<String> targetNodes, List<String> files) {
        try {
            TestPlanResponse plan = testIntelligenceAnalyzer.suggestTestPlan(projectId, request, targetNodes, files, "medium");
            return plan.getRecommendedLevels().stream()
                    .map(l -> l.getLevel() + ": " + l.getCommand())
                    .toList();
        } catch (RuntimeException ex) {
            return List.of("Run the project's unit tests for the changed classes before and after the edit.");
        }
    }

    private List<SourceSnippet> snippets(String projectId, List<CandidateFile> files) {
        List<SourceSnippet> out = new ArrayList<>();
        for (CandidateFile file : files.stream().limit(MAX_SNIPPETS).toList()) {
            try {
                SourceContent content = sourceFileService.readRange(projectId, file.getRelativePath(), 1, SNIPPET_LINES);
                if (content.found()) {
                    out.add(SourceSnippet.builder()
                            .relativePath(content.relativePath())
                            .startLine(content.startLine())
                            .endLine(content.endLine())
                            .content(content.content())
                            .truncated(content.truncated())
                            .build());
                }
            } catch (RuntimeException ignored) {
                // skip unreadable candidate
            }
        }
        return out;
    }

    private List<String> proposedSteps(List<CandidateFile> files) {
        List<String> steps = new ArrayList<>();
        if (files.isEmpty()) {
            steps.add("Clarify the target area; no candidate files were identified from the request.");
            return steps;
        }
        steps.add("Inspect first: " + files.stream().limit(3).map(CandidateFile::getRelativePath).toList());
        steps.add("Read the related tests (find_related_tests) for the top candidate before editing.");
        steps.add("Make the smallest change in the candidate file(s), preserving existing patterns.");
        steps.add("Run the suggested unit tests; widen to integration if persistence/endpoints are touched.");
        steps.add("Run get_impact_analysis on the changed symbol to confirm the blast radius.");
        return steps;
    }

    private List<String> risks(ImpactSummary impact, boolean endpointy) {
        List<String> risks = new ArrayList<>();
        if (impact != null && impact.getRiskLevel() != null) {
            risks.add("Impact risk on " + impact.getTargetFullName() + ": " + impact.getRiskLevel()
                    + " (" + impact.getDirectDependents() + " direct dependents).");
        }
        if (endpointy) {
            risks.add("Endpoint/contract change may break API consumers; verify request/response shape.");
        }
        risks.add("Search-based candidates may include false positives; confirm with source before editing.");
        return risks;
    }

    private String confidence(List<CandidateFile> files, List<CandidateSymbol> symbols) {
        if (files.isEmpty() && symbols.isEmpty()) {
            return "LOW";
        }
        if (!symbols.isEmpty() && !files.isEmpty() && files.get(0).getScore() >= 20) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private String summary(List<CandidateFile> files, String confidence) {
        String area = files.isEmpty() ? "no specific files" : files.get(0).getRelativePath();
        return "Reconnaissance for change request (confidence " + confidence + "). Most likely entry point: " + area + ".";
    }

    private CandidateSymbol toSymbol(NodeDto node) {
        return CandidateSymbol.builder()
                .id(node.getId())
                .type(node.getType())
                .name(node.getName())
                .fullName(node.getFullName())
                .layer(SourceGraphSupport.stringProperty(node, "springLayer"))
                .build();
    }

    private List<String> keywords(String request, List<String> entrypoints) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (entrypoints != null) {
            for (String e : entrypoints) {
                if (e != null && !e.isBlank()) {
                    String token = e.trim();
                    int dot = token.lastIndexOf('.');
                    result.add(dot >= 0 && dot < token.length() - 1 ? token.substring(dot + 1) : token);
                }
            }
        }
        var matcher = TOKEN.matcher(request);
        while (matcher.find() && result.size() < MAX_KEYWORDS * 3) {
            String token = matcher.group();
            boolean identifierish = token.chars().anyMatch(Character::isUpperCase) || token.contains("_");
            if (identifierish) {
                result.add(token);
            } else if (token.length() >= 4 && !STOPWORDS.contains(token.toLowerCase(Locale.ROOT))) {
                result.add(token);
            }
        }
        return result.stream().limit(MAX_KEYWORDS).toList();
    }

    private int boundFiles(Integer maxFiles) {
        if (maxFiles == null || maxFiles <= 0) {
            return DEFAULT_MAX_FILES;
        }
        return Math.min(maxFiles, HARD_CAP_FILES);
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

    private String validateMultiline(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || value.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
            throw new IllegalArgumentException(field + " must be non-blank and at most " + maxLength + " characters");
        }
        return value.trim();
    }

    private static final class FileScore {
        int hits;
        final Set<String> keywords = new LinkedHashSet<>();
    }
}
