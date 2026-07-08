package com.vibegraph.graph.service;

import com.vibegraph.graph.dto.request.LocalImportRequest;
import com.vibegraph.graph.dto.response.DirectoryListing;
import com.vibegraph.graph.dto.response.ProjectResponse;

/**
 * Import a project from an existing directory on the backend host (no upload/extract), and
 * browse the host filesystem so a user can pick that directory.
 *
 * <p>Unlike archive/GitHub import (which materialize a server-side copy), a local import reads
 * the directory in place, so the file watcher then streams realtime graph updates as the user
 * edits those very files.
 */
public interface LocalImportService {

    /**
     * Register + analyze a local directory in place, then start watching it for realtime updates.
     *
     * @return the analyzed project
     * @throws IllegalArgumentException if the path is missing, not a directory, or outside the
     *                                  configured allowed root
     */
    ProjectResponse importLocal(LocalImportRequest request);

    /**
     * List the immediate sub-directories of {@code path} (or the starting view when blank).
     *
     * <p>When {@code vibegraph.projects.allowed-root} is set, browsing is confined to it. When it
     * is not set, browsing is unconfined (a blank path lists the filesystem roots/drives) so a
     * developer can navigate anywhere on the backend host; an inaccessible or non-directory path
     * is reported as an error.
     *
     * @param path absolute directory to list; blank/null lists the starting view
     */
    DirectoryListing browse(String path);
}
