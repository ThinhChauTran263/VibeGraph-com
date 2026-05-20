package com.vibegraph.steering.writer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Writes .kiro/steering/vibegraph-context.md
 *
 * TODO:
 * - Create .kiro/steering/ directory if not exists
 * - Write file with frontmatter (inclusion: always)
 * - Don't overwrite user's custom rules
 */
@Component
@Slf4j
public class KiroSteeringWriter implements SteeringWriter {

    @Override
    public boolean supports(String aiTool) {
        return "kiro".equalsIgnoreCase(aiTool);
    }

    @Override
    public void write(String projectPath, String content) {
        // TODO: Implement
    }
}
