package com.vibegraph.graph.service;

import com.vibegraph.graph.dto.request.GithubImportRequest;
import com.vibegraph.graph.dto.response.ProjectResponse;

/**
 * Streams a GitHub tarball directly to the parser without persisting files to disk.
 *
 * Pipeline:
 *   1. Pre-flight: GET https://api.github.com/repos/{owner}/{repo}
 *      → validate public, size < 100MB
 *   2. Stream tarball via commons-compress
 *      (GzipCompressorInputStream + TarArchiveInputStream)
 *   3. Iterate entries, filter *.java, parse in-memory
 *   4. Push progress via WebSocket /topic/projects/{id}/status
 *
 * Throws GithubImportException when validation or download fails.
 */
public interface TarballImportService {

    /**
     * Imports a GitHub repository as a project.
     *
     * @param request the GitHub URL request
     * @return ProjectResponse with status=ANALYZING (parsing runs async)
     */
    ProjectResponse importFromGithub(GithubImportRequest request);
}
