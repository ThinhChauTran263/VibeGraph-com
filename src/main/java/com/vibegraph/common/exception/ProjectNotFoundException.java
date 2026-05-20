package com.vibegraph.common.exception;

/**
 * Thrown when a project ID is not found in the system.
 */
public class ProjectNotFoundException extends RuntimeException {
    public ProjectNotFoundException(String message) {
        super(message);
    }
}
