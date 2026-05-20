package com.vibegraph.steering.writer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Writes CLAUDE.md (project root) — section for Claude Code.
 *
 * TODO:
 * - Append/update VibeGraph section in existing CLAUDE.md
 * - Use markers <!-- vibegraph:start --> and <!-- vibegraph:end --> to identify our section
 */
@Component
@Slf4j
public class ClaudeRulesWriter implements SteeringWriter {

    @Override
    public boolean supports(String aiTool) {
        return "claude".equalsIgnoreCase(aiTool);
    }

    @Override
    public void write(String projectPath, String content) {
        // TODO: Implement
    }
}
