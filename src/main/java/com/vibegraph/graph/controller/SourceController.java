package com.vibegraph.graph.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;
import com.vibegraph.mcp.source.SourceFileService;
import com.vibegraph.mcp.source.SourceFileService.SourceContent;

import lombok.RequiredArgsConstructor;

/**
 * Read-only source-code access for the in-app code viewer.
 *
 * <p>Backs the "View source" action triggered from a graph node: given the node's file path,
 * returns a bounded, redacted slice of the underlying source file so the user can read the code
 * without leaving the graph.
 *
 * <p>All security (project-root confinement, path-traversal rejection, allow-listed extensions,
 * secret redaction, size caps) is delegated to {@link SourceFileService}, the same hardened
 * service used by the MCP source tools. This controller adds no filesystem access of its own.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/source")
@RequiredArgsConstructor
public class SourceController {

    private final SourceFileService sourceFileService;
    private final ProjectOwnershipGuard ownershipGuard;

    /**
     * Read a bounded slice of a single source file.
     *
     * @param projectId tenant identifier
     * @param path      absolute path (from a graph node) or a project-relative path; resolved and
     *                  confined to the project's source root
     * @param startLine 1-based inclusive start (optional; defaults to 1)
     * @param endLine   1-based inclusive end (optional; the service caps the window size)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<SourceContent>> getSource(
            @PathVariable String projectId,
            @RequestParam String path,
            @RequestParam(required = false) Integer startLine,
            @RequestParam(required = false) Integer endLine) {
        ownershipGuard.assertOwner(projectId);
        SourceContent content = sourceFileService.readRange(projectId, path, startLine, endLine);
        return ResponseEntity.ok(ApiResponse.success(content));
    }
}
