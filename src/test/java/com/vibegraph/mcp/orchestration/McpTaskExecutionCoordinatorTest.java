package com.vibegraph.mcp.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpTaskExecutionCoordinatorTest {

    @Mock
    private TaskDagManager taskDagManager;

    @Test
    void successfulInvocationIsRegisteredAndCompleted() {
        McpTaskExecutionCoordinator coordinator = new McpTaskExecutionCoordinator(taskDagManager);

        String result = coordinator.execute("verify_change", "project-1", () -> "ok");

        assertThat(result).isEqualTo("ok");
        ArgumentCaptor<AgentTask> task = ArgumentCaptor.forClass(AgentTask.class);
        InOrder order = inOrder(taskDagManager);
        order.verify(taskDagManager).registerTask(task.capture());
        order.verify(taskDagManager).handleTaskSuccess(task.getValue().getId());
        assertThat(task.getValue().getDescription()).contains("verify_change", "project-1");
    }

    @Test
    void failedInvocationIsRegisteredAndEscalated() {
        McpTaskExecutionCoordinator coordinator = new McpTaskExecutionCoordinator(taskDagManager);
        IllegalStateException failure = new IllegalStateException("delegate failed");

        assertThatThrownBy(() -> coordinator.execute("verify_change", "project-1", () -> {
            throw failure;
        })).isSameAs(failure);

        ArgumentCaptor<AgentTask> task = ArgumentCaptor.forClass(AgentTask.class);
        verify(taskDagManager).registerTask(task.capture());
        verify(taskDagManager).handleTaskFailure(task.getValue().getId(), "delegate failed");
    }

    @Test
    void completionPersistenceFailureDoesNotMaskSuccessfulToolResult() {
        McpTaskExecutionCoordinator coordinator = new McpTaskExecutionCoordinator(taskDagManager);
        doThrow(new IllegalStateException("database unavailable"))
                .when(taskDagManager).handleTaskSuccess(org.mockito.ArgumentMatchers.anyString());

        assertThat(coordinator.execute("list_projects", null, () -> "ok")).isEqualTo("ok");
        verify(taskDagManager).registerTask(org.mockito.ArgumentMatchers.any(AgentTask.class));
        verify(taskDagManager).handleTaskSuccess(org.mockito.ArgumentMatchers.anyString());
        verifyNoMoreInteractions(taskDagManager);
    }

    @Test
    void registrationPersistenceFailureDoesNotBlockToolInvocation() {
        McpTaskExecutionCoordinator coordinator = new McpTaskExecutionCoordinator(taskDagManager);
        doThrow(new IllegalStateException("database unavailable"))
                .when(taskDagManager).registerTask(org.mockito.ArgumentMatchers.any(AgentTask.class));

        assertThat(coordinator.execute("list_projects", null, () -> "ok")).isEqualTo("ok");
        verify(taskDagManager).registerTask(org.mockito.ArgumentMatchers.any(AgentTask.class));
        verifyNoMoreInteractions(taskDagManager);
    }

    @Test
    void failurePersistenceDoesNotMaskOriginalToolFailure() {
        McpTaskExecutionCoordinator coordinator = new McpTaskExecutionCoordinator(taskDagManager);
        IllegalStateException failure = new IllegalStateException("delegate failed");
        doThrow(new IllegalStateException("database unavailable"))
                .when(taskDagManager).handleTaskFailure(
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("delegate failed"));

        assertThatThrownBy(() -> coordinator.execute("verify_change", "project-1", () -> {
            throw failure;
        })).isSameAs(failure);
    }
}
