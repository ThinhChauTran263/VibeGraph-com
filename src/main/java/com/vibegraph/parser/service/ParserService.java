package com.vibegraph.parser.service;

import com.vibegraph.parser.node.ParseResult;

import java.nio.file.Path;
import java.util.List;

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
    List<ParseResult> parseProject(Path projectRoot);

    /**
     * Parse a file, using SHA-256 checksum cache to skip unchanged files.
     * @param filePath absolute path to the .java file
     * @param projectId tenant identifier for cache scoping
     * @return ParseResult (from cache if checksum matches, else fresh parse)
     */
    ParseResult parseFileWithCache(Path filePath, String projectId);
}
