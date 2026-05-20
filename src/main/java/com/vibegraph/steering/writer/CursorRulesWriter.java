package com.vibegraph.steering.writer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Writes .cursor/rules/vibegraph.mdc
 *
 * TODO:
 * - Create .cursor/rules/ directory if not exists
 * - Write file with proper MDC frontmatter
 */
@Component
@Slf4j
public class CursorRulesWriter implements SteeringWriter {

    @Override
    public boolean supports(String aiTool) {
        return "cursor".equalsIgnoreCase(aiTool);
    }

    @Override
    public void write(String projectPath, String content) {
        // TODO: Implement
    }
}
