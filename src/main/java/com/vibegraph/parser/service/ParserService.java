package com.vibegraph.parser.service;

import java.nio.file.Path;
import java.util.List;

import com.vibegraph.parser.node.ParseResult;

/**
 * Java source code parser service.
 * Orchestrates JavaParser to extract nodes/edges from .java files.
 */
public interface ParserService {

    /**
     * Parse a single .java file.
     * @param filePath absolute path to the .java file
     * @return ParseResult containing nodes, edges, warnings
     */
    ParseResult parseFile(Path filePath);

    /**
     * Parse all .java files in a project directory (recursive).
     * @param projectRoot root directory of the Java project
     * @return aggregated list of ParseResult (one per file)
     */
    default List<ParseResult> parseProject(Path projectRoot) {
        return parseProject(projectRoot, ParseProgressListener.NOOP);
    }

    /**
     * Parse all .java files in a project directory (recursive), reporting per-file
     * progress so callers can surface a smooth progress indicator.
     *
     * @param projectRoot      root directory of the Java project
     * @param progressListener invoked after each file is parsed; never {@code null}
     *                         (use {@link ParseProgressListener#NOOP})
     * @return aggregated list of ParseResult (one per file)
     */
    List<ParseResult> parseProject(Path projectRoot, ParseProgressListener progressListener);

    /**
     * Parses a project with hard aggregate graph limits. Implementations may stop outstanding
     * parser work as soon as either limit is exceeded so the guard protects heap usage.
     */
    default List<ParseResult> parseProject(Path projectRoot, ParseProgressListener progressListener,
            int maxNodes, int maxEdges) {
        return parseProject(projectRoot, progressListener);
    }
}
