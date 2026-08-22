package com.vibegraph.mcp.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentTaskTimestampTest {

    @Test
    void statusTransitionsExposeCompletionTimestampOnlyForTerminalStates() {
        AgentTask task = new AgentTask("task-1", "retention test", 0);

        assertThat(task.getCompletedAt()).isNull();
        task.setStatus(TaskStatus.COMPLETED);
        task.refreshTimestamps();

        assertThat(task.getCompletedAt()).isNotNull();
        assertThat(task.getCreatedAt()).isNotNull();
        assertThat(task.getUpdatedAt()).isAfterOrEqualTo(task.getCreatedAt());
    }
}
