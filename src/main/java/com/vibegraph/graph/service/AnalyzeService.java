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
     * @param projectId tenant identifier (also the Project node's stable graph id)
     * @param projectName human-readable display name (repo/owner-repo for GitHub
     *                    imports, user-provided name for archive uploads) used as the
     *                    Project node's {@code name} so the canvas shows a readable label
     *                    instead of the id
     * @param projectPath absolute path to project root containing .java files
     * @return summary of analysis result
     */
    AnalysisResult analyzeProject(String projectId, String projectName, String projectPath);

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
