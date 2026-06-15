package com.vibegraph.common.exception;

/**
 * Thrown when an operation requires a project to be fully analyzed but it is not
 * yet in the {@code ANALYZED} state (e.g. still CREATED/ANALYZING, or FAILED).
 *
 * <p>Used by diagram generation so the client gets a clear, typed error instead
 * of an empty/misleading diagram when the graph has not been built yet.
 */
public class ProjectNotAnalyzedException extends RuntimeException {
    public ProjectNotAnalyzedException(String message) {
        super(message);
    }
}
