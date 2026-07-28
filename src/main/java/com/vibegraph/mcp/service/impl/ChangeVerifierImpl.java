package com.vibegraph.mcp.service.impl;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.mcp.dto.response.RelatedTestsResponse;
import com.vibegraph.mcp.dto.response.VerifyChangeResponse;
import com.vibegraph.mcp.dto.response.VerifyChangeResponse.ChangedSymbol;
import com.vibegraph.mcp.dto.response.VerifyChangeResponse.RouteRef;
import com.vibegraph.mcp.dto.response.VerifyChangeResponse.SuggestedCommands;
import com.vibegraph.mcp.dto.response.VerifyChangeResponse.TestRef;
import com.vibegraph.mcp.service.ChangeVerifier;
import com.vibegraph.mcp.service.TestIntelligenceAnalyzer;
import com.vibegraph.mcp.source.GraphTraversals;
import com.vibegraph.mcp.source.GraphView;
import com.vibegraph.mcp.source.SourceFileService;
import com.vibegraph.mcp.source.SourceGraphSupport;

import lombok.RequiredArgsConstructor;

/**
 * The "am I done?" tool: an agent hands over the RELATIVE paths it changed and gets back
 * the graph symbols in those files, every API route that can reach them, and the concrete
 * tests to run. The agent supplies the file list (from its own git status) — the server
 * never executes git or any other process.
 */
@Service
@RequiredArgsConstructor
public class ChangeVerifierImpl implements ChangeVerifier {

    private static final int MAX_FILES = 20;
    private static final int MAX_SYMBOLS = 50;
    private static final int MAX_ROUTES = 25;
    private static final int MAX_TEST_CLASSES = 5;
    private static final int MAX_TESTS = 15;
    private static final int MAX_ROUTE_DEPTH = 8;
    private static final Set<String> CLASSLIKE = Set.of("Class", "Interface", "Enum", "Record");

    private final SourceGraphSupport graphSupport;
    private final SourceFileService sourceFileService;
    private final TestIntelligenceAnalyzer testIntelligenceAnalyzer;

    @Override
    public VerifyChangeResponse verifyChange(String projectId, List<String> changedFiles) {
        String normalizedProjectId = validate(projectId, "projectId", 512);
        List<String> files = normalizeFiles(changedFiles);

        GraphView graph = safeLoad(normalizedProjectId);
        if (graph == null) {
            return warning(normalizedProjectId, files, "Change verification is temporarily unavailable.");
        }
        Path root;
        try {
            root = sourceFileService.resolveProjectRoot(normalizedProjectId);
        } catch (ProjectNotFoundException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return warning(normalizedProjectId, files, "Project source root is unavailable.");
        }

        List<String> warnings = new ArrayList<>();
        List<ChangedSymbol> changedSymbols = new ArrayList<>();
        for (String file : files) {
            if (changedSymbols.size() >= MAX_SYMBOLS) {
                warnings.add("changedSymbols truncated to " + MAX_SYMBOLS + "; remaining files were not mapped.");
                break;
            }
            Path absolute;
            try {
                absolute = root.resolve(file).normalize();
            } catch (InvalidPathException ex) {
                warnings.add("Invalid path skipped: " + file);
                continue;
            }
            if (!absolute.startsWith(root)) {
                warnings.add("Path outside the project root skipped: " + file);
                continue;
            }
            List<NodeDto> nodes = graph.nodesInFile(absolute.toString());
            if (nodes.isEmpty()) {
                warnings.add("No graph symbols for " + file + " (new or unparsed file - re-run analyze).");
                continue;
            }
            for (NodeDto node : nodes) {
                if (changedSymbols.size() >= MAX_SYMBOLS) {
                    break;
                }
                changedSymbols.add(ChangedSymbol.builder()
                        .id(node.getId())
                        .type(node.getType())
                        .name(node.getName())
                        .fullName(node.getFullName())
                        .relativePath(file)
                        .lineNumber(node.getLineNumber())
                        .build());
            }
        }

        Set<String> seedIds = new LinkedHashSet<>();
        changedSymbols.forEach(symbol -> seedIds.add(symbol.getId()));
        List<RouteRef> routes = GraphTraversals.affectedRoutes(graph, seedIds, MAX_ROUTE_DEPTH, MAX_ROUTES).stream()
                .map(route -> RouteRef.builder()
                        .httpMethod(route.httpMethod())
                        .routePath(route.routePath())
                        .handlerFullName(route.handlerFullName())
                        .build())
                .toList();

        TestLookup testLookup = relatedTests(normalizedProjectId, changedSymbols);

        return VerifyChangeResponse.builder()
                .projectId(normalizedProjectId)
                .changedFiles(files)
                .changedSymbols(changedSymbols)
                .affectedRoutes(routes)
                .relatedTests(testLookup.tests)
                .suggestedCommands(testLookup.commands)
                .risks(risks(changedSymbols, routes, testLookup.tests))
                .warnings(warnings)
                .notes(List.of(
                        "Symbols are mapped from the analyzed graph; run analyze again if files were added.",
                        "Run the suggested tests, then get_impact_analysis on any symbol with a wide blast radius."))
                .build();
    }

    private record TestLookup(List<TestRef> tests, SuggestedCommands commands) {
    }

    /** Related tests for the changed class-like symbols, reusing the test-intelligence heuristics. */
    private TestLookup relatedTests(String projectId, List<ChangedSymbol> changedSymbols) {
        Map<String, TestRef> tests = new LinkedHashMap<>();
        SuggestedCommands commands = null;
        List<String> classFullNames = changedSymbols.stream()
                .filter(symbol -> CLASSLIKE.contains(symbol.getType()))
                .map(ChangedSymbol::getFullName)
                .distinct()
                .limit(MAX_TEST_CLASSES)
                .toList();
        for (String classFullName : classFullNames) {
            try {
                RelatedTestsResponse related = testIntelligenceAnalyzer.findRelatedTests(
                        projectId, null, classFullName, null, null, null, 10);
                for (RelatedTestsResponse.TestMatch match : safeMatches(related)) {
                    if (tests.size() >= MAX_TESTS) {
                        break;
                    }
                    tests.putIfAbsent(match.getId(), TestRef.builder()
                            .name(match.getName())
                            .fullName(match.getFullName())
                            .relativePath(match.getRelativePath())
                            .build());
                }
                if (commands == null && related.getSuggestedCommands() != null) {
                    commands = SuggestedCommands.builder()
                            .windows(related.getSuggestedCommands().getWindows())
                            .unix(related.getSuggestedCommands().getUnix())
                            .frontend(related.getSuggestedCommands().getFrontend())
                            .build();
                }
            } catch (RuntimeException ignored) {
                // One unresolvable class never blocks verification of the rest.
            }
        }
        return new TestLookup(List.copyOf(tests.values()), commands);
    }

    private List<RelatedTestsResponse.TestMatch> safeMatches(RelatedTestsResponse related) {
        return related == null || related.getMatches() == null ? List.of() : related.getMatches();
    }

    private List<String> risks(List<ChangedSymbol> symbols, List<RouteRef> routes, List<TestRef> tests) {
        List<String> risks = new ArrayList<>();
        if (!routes.isEmpty()) {
            risks.add(routes.size() + " API route(s) can reach the changed symbols - verify the endpoint contracts.");
        }
        if (symbols.stream().anyMatch(symbol -> "Interface".equals(symbol.getType()))) {
            risks.add("An interface changed - every implementation is affected; run get_impact_analysis on it.");
        }
        if (tests.isEmpty() && !symbols.isEmpty()) {
            risks.add("No related tests were found for the changed classes - add coverage before merging.");
        }
        return risks;
    }

    private List<String> normalizeFiles(List<String> changedFiles) {
        if (changedFiles == null || changedFiles.isEmpty()) {
            throw new IllegalArgumentException("changedFiles must contain at least one project-relative path");
        }
        List<String> files = new ArrayList<>();
        for (String file : changedFiles) {
            if (file == null || file.isBlank() || file.chars().anyMatch(Character::isISOControl)) {
                continue;
            }
            if (files.size() >= MAX_FILES) {
                break;
            }
            files.add(file.trim().replace('\\', '/'));
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException("changedFiles must contain at least one non-blank path");
        }
        return files;
    }

    private VerifyChangeResponse warning(String projectId, List<String> files, String message) {
        return VerifyChangeResponse.builder()
                .projectId(projectId)
                .changedFiles(files)
                .changedSymbols(List.of())
                .affectedRoutes(List.of())
                .relatedTests(List.of())
                .risks(List.of())
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
}
