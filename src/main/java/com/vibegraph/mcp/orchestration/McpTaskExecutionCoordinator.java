package com.vibegraph.mcp.orchestration;

import com.vibegraph.mcp.orchestration.entity.AgentTask;

import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Records the lifecycle of one MCP tool execution without coupling metering to DAG policy. */
@Service
public class McpTaskExecutionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(McpTaskExecutionCoordinator.class);

    private final TaskDagManager taskDagManager;

    public McpTaskExecutionCoordinator(TaskDagManager taskDagManager) {
        this.taskDagManager = taskDagManager;
    }

    public String execute(String toolName, String projectId, Supplier<String> invocation) {
        String taskId = "mcp:" + UUID.randomUUID();
        String description = projectId == null || projectId.isBlank()
                ? "MCP tool: " + toolName
                : "MCP tool: " + toolName + " project=" + projectId;
        AgentTask task = new AgentTask(taskId, description, 0);
        task.setStatus(TaskStatus.IN_PROGRESS);
        try {
            taskDagManager.registerTask(task);
        } catch (RuntimeException orchestrationFailure) {
            log.warn("Could not register MCP task {} for tool {}: {}", taskId, toolName,
                    orchestrationFailure.getMessage());
            return invocation.get();
        }
        try {
            String result = invocation.get();
            completeQuietly(taskId, toolName);
            return result;
        } catch (RuntimeException ex) {
            failQuietly(taskId, toolName, ex);
            throw ex;
        }
    }

    private void completeQuietly(String taskId, String toolName) {
        try {
            taskDagManager.handleTaskSuccess(taskId);
        } catch (RuntimeException orchestrationFailure) {
            log.warn("Could not complete MCP task {} for tool {}: {}", taskId, toolName,
                    orchestrationFailure.getMessage());
        }
    }

    private void failQuietly(String taskId, String toolName, RuntimeException invocationFailure) {
        try {
            taskDagManager.handleTaskFailure(taskId, invocationFailure.getMessage());
        } catch (RuntimeException orchestrationFailure) {
            log.warn("Could not record failed MCP task {} for tool {}: {}", taskId, toolName,
                    orchestrationFailure.getMessage());
        }
    }
}
