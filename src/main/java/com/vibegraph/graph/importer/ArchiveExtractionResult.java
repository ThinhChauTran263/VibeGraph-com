package com.vibegraph.graph.importer;

import java.nio.file.Path;
import java.util.List;

/**
 * Outcome of a safe archive extraction.
 *
 * @param extractedRoot      the workspace directory the {@code .java} files were materialized under
 * @param javaFiles          absolute paths of the extracted {@code .java} files
 * @param relativeJavaPaths  each file's path relative to {@code extractedRoot} (POSIX separators),
 *                           preserved as the basis for the parser's {@code filePath}
 */
public record ArchiveExtractionResult(
        Path extractedRoot,
        List<Path> javaFiles,
        List<String> relativeJavaPaths
) {
}
