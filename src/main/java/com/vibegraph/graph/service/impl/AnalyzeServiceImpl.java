package com.vibegraph.graph.service.impl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.graph.service.AnalysisProgressListener;
import com.vibegraph.graph.service.AnalyzeService;
import com.vibegraph.parser.flow.FlowAnalyzer;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;
import com.vibegraph.parser.node.ParseResult;
import com.vibegraph.parser.service.ParserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyzeServiceImpl implements AnalyzeService {

    private final ParserService parserService;
    private final GraphRepository graphRepository;

    /** Overall % at which the parse phase begins (after the project workspace is ready). */
    private static final int PARSE_START_PCT = 5;
    /** Overall % at which the parse phase ends (remaining budget covers graph persistence). */
    private static final int PARSE_END_PCT = 70;

    @Override
    public AnalysisResult analyzeProject(String projectId, String projectName, String projectPath,
                                         AnalysisProgressListener progressListener) {
        AnalysisProgressListener progress = progressListener != null ? progressListener : AnalysisProgressListener.NOOP;
        Path root = Path.of(projectPath);
        log.info("Starting full analysis for project {} at {}", projectId, root);

        progress.onProgress(PARSE_START_PCT, "Scanning files");

        // Parsing is the dominant phase; map per-file callbacks across PARSE_START..PARSE_END
        // so the bar advances smoothly while files are processed.
        List<ParseResult> results = parserService.parseProject(root, (filesParsed, totalFiles) -> {
            int pct = totalFiles <= 0
                    ? PARSE_END_PCT
                    : PARSE_START_PCT + (int) Math.round((filesParsed / (double) totalFiles) * (PARSE_END_PCT - PARSE_START_PCT));
            progress.onProgress(Math.min(PARSE_END_PCT, pct),
                    "Parsing files (" + filesParsed + "/" + totalFiles + ")");
        });

        progress.onProgress(72, "Building relationships");

        List<NodeData> allNodes = new ArrayList<>();
        List<EdgeData> allEdges = new ArrayList<>();
        int totalWarnings = 0;

        for (ParseResult result : results) {
            allNodes.addAll(result.getNodes());
            allEdges.addAll(result.getEdges());
            totalWarnings += result.getWarnings().size();
        }

        // Project -> Package containment. The parser emits Package nodes and
        // Package -[:CONTAINS]-> File edges per file but cannot know the projectId
        // (the Project node's fullName). Wire Project -[:CONTAINS]-> Package here
        // for every distinct package so the hierarchy is Project -> Package -> File.
        // Additive only: existing nodes/edges are untouched.
        allEdges.addAll(projectContainsPackageEdges(projectId, allNodes));

        // STEP_IN_FLOW: inferred execution-flow steps from route handlers through the
        // already-resolved in-project CALLS graph. Computed from the CALLS/HANDLES_ROUTE
        // edges gathered above (before this line), then appended. Additive only — CALLS
        // is left unchanged. NOT a copy of CALLS (reachability-filtered + deduped).
        allEdges.addAll(FlowAnalyzer.inferStepInFlow(allNodes, allEdges));

        progress.onProgress(80, "Saving nodes");

        // Project node name = human-readable display name (repo/owner-repo for GitHub,
        // user-provided name for archive). The stable graph id stays projectId. Fall back
        // to projectId if no name was supplied so the node is never left without a label.
        String displayName = (projectName != null && !projectName.isBlank()) ? projectName : projectId;
        graphRepository.upsertProject(projectId, displayName, projectPath);
        graphRepository.upsertNodes(projectId, allNodes);

        progress.onProgress(94, "Saving relationships");

        // upsertEdges returns the number of edges actually persisted (including
        // any that required an External stub target). This is the truthful count
        // to report — allEdges.size() would over-report if anything were dropped.
        int edgesPersisted = graphRepository.upsertEdges(projectId, allEdges);

        progress.onProgress(98, "Finalizing");

        log.info("Analysis complete: {} files, {} nodes, {} edges persisted ({} parsed), {} warnings",
                results.size(), allNodes.size(), edgesPersisted, allEdges.size(), totalWarnings);

        return new AnalysisResult(
                projectId,
                results.size(),
                allNodes.size(),
                edgesPersisted,
                totalWarnings
        );
    }

    /**
     * Build deterministic Project -[:CONTAINS]-> Package edges from the distinct
     * Package nodes the parser produced. The Project node's fullName is the
     * projectId (see {@code upsertProject}). Returns at most one edge per package.
     */
    private List<EdgeData> projectContainsPackageEdges(String projectId, List<NodeData> nodes) {
        return nodes.stream()
                .filter(node -> "Package".equals(node.type()))
                .map(NodeData::fullName)
                .distinct()
                .sorted()
                .map(packageFullName -> EdgeData.of("CONTAINS", projectId, packageFullName))
                .toList();
    }
}
