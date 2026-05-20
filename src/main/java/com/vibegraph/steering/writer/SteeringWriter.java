package com.vibegraph.steering.writer;

/**
 * Steering file writer interface.
 * Strategy pattern: each AI tool has its own writer.
 */
public interface SteeringWriter {

    /**
     * @return true if this writer handles the given AI tool name (e.g., "kiro", "cursor", "claude")
     */
    boolean supports(String aiTool);

    /**
     * Write steering file with given content.
     */
    void write(String projectPath, String content);
}
