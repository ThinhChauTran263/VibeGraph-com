package com.vibegraph.graph.dto.response;

import java.util.List;

/**
 * Result of browsing a directory on the backend host (server-side directory picker).
 *
 * @param path    absolute path of the directory being listed
 * @param parent  absolute path of the parent directory, or {@code null} when at the allowed base
 *                (so the client cannot navigate above the configured boundary)
 * @param entries immediate sub-directories, sorted by name
 */
public record DirectoryListing(
        String path,
        String parent,
        List<Entry> entries
) {
    /**
     * A single sub-directory entry.
     *
     * @param name         directory name (last path segment)
     * @param path         absolute directory path
     * @param containsJava whether this directory (recursively, bounded) appears to hold {@code .java} files
     */
    public record Entry(String name, String path, boolean containsJava) {
    }
}
