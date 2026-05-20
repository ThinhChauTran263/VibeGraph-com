package com.vibegraph.common.exception;

/**
 * Thrown when a graph node ID is not found in Neo4j.
 */
public class NodeNotFoundException extends RuntimeException {
    public NodeNotFoundException(String message) {
        super(message);
    }
}
