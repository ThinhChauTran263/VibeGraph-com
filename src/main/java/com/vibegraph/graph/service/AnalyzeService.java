package com.vibegraph.graph.service;

/**
 * Project analysis service.
 *
 * TODO:
 * - analyzeProject(projectId, request) → trigger full analysis
 * - analyzeFile(projectId, filePath) → incremental update
 * - getStatus(projectId) → progress info
 *
 * Flow:
 * 1. Scan project directory for .java files
 * 2. For each file: parse → extract → save to Neo4j
 * 3. Notify WebSocket subscribers
 */
public interface AnalyzeService {
    // TODO: Define methods
}
