package com.vibegraph.graph.service.impl;

import com.vibegraph.common.exception.GithubImportException;
import com.vibegraph.graph.dto.request.GithubImportRequest;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.service.TarballImportService;
import org.springframework.stereotype.Service;

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
        // TODO: implement pipeline (pre-flight → stream → parse → notify)
        throw new GithubImportException("TarballImportService not implemented yet");
    }
}
