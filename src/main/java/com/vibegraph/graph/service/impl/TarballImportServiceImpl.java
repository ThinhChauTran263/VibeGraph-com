package com.vibegraph.graph.service.impl;

import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.FeatureNotImplementedException;
import com.vibegraph.graph.dto.request.GithubImportRequest;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.service.TarballImportService;

/**
 * Default implementation streaming GitHub tarballs into the parser.
 *
 * TODO:
 *  - Inject WebClient (GitHub API + tarball download)
 *  - Inject ParserService (parse in-memory)
 *  - Inject GraphRepository (upsert nodes/edges)
 *  - Inject SimpMessagingTemplate (push progress)
 *  - Async parse phase (return projectId immediately after pre-flight)
 */
@Service
public class TarballImportServiceImpl implements TarballImportService {

    @Override
    public ProjectResponse importFromGithub(GithubImportRequest request) {
        // Not built yet (Sprint 2). Signal honestly with 501 instead of letting the
        // controller's 202 imply the import was accepted. Domain failures of the real
        // pipeline (private repo, oversize, bad URL) will use GithubImportException → 4xx.
        throw new FeatureNotImplementedException(
                "GitHub import is not available yet (planned for Sprint 2).");
    }
}
