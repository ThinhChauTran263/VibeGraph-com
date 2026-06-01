package com.vibegraph.graph.service.impl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.graph.service.AnalyzeService;
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

    @Override
    public AnalysisResult analyzeProject(String projectId, String projectPath) {
        Path root = Path.of(projectPath);
        log.info("Starting full analysis for project {} at {}", projectId, root);

        List<ParseResult> results = parserService.parseProject(root);

        List<NodeData> allNodes = new ArrayList<>();
        List<EdgeData> allEdges = new ArrayList<>();
        int totalWarnings = 0;

        for (ParseResult result : results) {
            allNodes.addAll(result.getNodes());
            allEdges.addAll(result.getEdges());
            totalWarnings += result.getWarnings().size();
        }

        graphRepository.upsertProject(projectId, projectId, projectPath);
        graphRepository.upsertNodes(projectId, allNodes);
        // upsertEdges returns the number of edges actually persisted (including
        // any that required an External stub target). This is the truthful count
        // to report — allEdges.size() would over-report if anything were dropped.
        int edgesPersisted = graphRepository.upsertEdges(projectId, allEdges);

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
}
