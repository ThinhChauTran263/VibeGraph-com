package com.vibegraph.mcp.source;

import java.nio.file.Path;
import java.util.List;

/**
 * Safe, read-only access to a project's imported source tree for the MCP source-reading
 * tools (Phase 6A).
 *
 * <p>Security contract — every method in the implementation MUST:
 * <ul>
 *   <li>resolve a file only under the project's import root (no path traversal / escape);</li>
 *   <li>serve only allow-listed text/source files (never {@code .env}, keys, archives, binaries,
 *       or build output);</li>
 *   <li>redact secret-looking lines;</li>
 *   <li>cap response size (max lines + max bytes) and flag truncation;</li>
 *   <li>return relative paths only — never an absolute host path.</li>
 * </ul>
 */
public interface SourceFileService {

    /**
     * Resolve the absolute, real import root for a project.
     *
     * @throws com.vibegraph.common.exception.ProjectNotFoundException if the project is unknown
     * @throws IllegalStateException if the registered root no longer exists on disk
     */
    Path resolveProjectRoot(String projectId);

    /**
     * Read a bounded, redacted slice of a single source file.
     *
     * @param projectId tenant identifier
     * @param rawPath   absolute path (from a graph node) or a project-relative path
     * @param startLine 1-based inclusive start (null -> 1)
     * @param endLine   1-based inclusive end (null -> bounded default)
     */
    SourceContent readRange(String projectId, String rawPath, Integer startLine, Integer endLine);

    /**
     * Case-insensitive literal text search across allow-listed source files under the root.
     *
     * @param fileGlob optional glob (e.g. {@code **&#47;*.java}) limiting which files are scanned
     */
    SearchOutcome search(String projectId, String query, String fileGlob, int maxResults);

    /** Bounded slice of a source file with truncation metadata. Paths are always relative. */
    record SourceContent(
            boolean found,
            String relativePath,
            String language,
            int startLine,
            int endLine,
            int totalLines,
            String content,
            boolean truncated,
            String truncationReason,
            List<String> warnings) {
    }

    /** A single search hit; {@code snippet} is trimmed and redacted. */
    record SearchHit(String relativePath, int lineNumber, String snippet) {
    }

    /** Search results plus the true total match count so callers can report truncation. */
    record SearchOutcome(List<SearchHit> hits, int totalMatches, boolean truncated, List<String> warnings) {
    }
}
