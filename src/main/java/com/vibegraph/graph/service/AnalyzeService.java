package com.vibegraph.graph.service;

/**
 * Project analysis service.
 *
 * Sprint 1 scope: trigger full analysis (parse all .java files → upsert to Neo4j).
 */
public interface AnalyzeService {

    /**
     * Run full analysis on a project: parse all .java files and persist to graph store.
     *
     * @param projectId tenant identifier
     * @param projectPath absolute path to project root containing .java files
     * @return summary of analysis result
     */
    AnalysisResult analyzeProject(String projectId, String projectPath);

    /**
     * Result summary of an analysis run.
     */
    record AnalysisResult(
            String projectId,
            int filesParsed,
            int nodesUpserted,
            int edgesUpserted,
            int warnings
    ) {}
}
