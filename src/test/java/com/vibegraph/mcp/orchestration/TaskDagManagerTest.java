package com.vibegraph.mcp.orchestration;

import com.vibegraph.mcp.orchestration.entity.AgentTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TaskDagManagerTest {

    @Mock
    private AgentTaskRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Map<String, AgentTask> tasks = new HashMap<>();
    private TaskDagManager manager;

    @BeforeEach
    void setUp() {
        manager = new TaskDagManager(repository, eventPublisher);
        org.mockito.Mockito.lenient().when(repository.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(tasks.get(invocation.getArgument(0))));
        org.mockito.Mockito.lenient().when(repository.findByIdForUpdate(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(tasks.get(invocation.getArgument(0))));
        org.mockito.Mockito.lenient().when(repository.save(any(AgentTask.class))).thenAnswer(invocation -> {
            AgentTask task = invocation.getArgument(0);
            tasks.put(task.getId(), task);
            return task;
        });
    }

    @Test
    void retryCreatesReplacementAndRepointsDownstreamEdges() {
        AgentTask root = task("root", 1);
        AgentTask upstream = task("upstream", 0);
        AgentTask downstream = task("downstream", 0);
        upstream.addDependent(root.getId());
        root.addDependency(upstream.getId());
        root.addDependent(downstream.getId());
        downstream.addDependency(root.getId());
        save(upstream, root, downstream);

        AgentTask replacement = manager.handleTaskFailure(root.getId(), "compile failed");

        assertThat(replacement.getId()).isNotEqualTo(root.getId());
        assertThat(replacement.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(replacement.getRetryCount()).isEqualTo(1);
        assertThat(root.getStatus()).isEqualTo(TaskStatus.SUPERSEDED);
        assertThat(root.getReplacementTaskId()).isEqualTo(replacement.getId());
        assertThat(upstream.getDependents()).containsExactly(replacement.getId());
        assertThat(replacement.getDependsOn()).containsExactly(upstream.getId());
        assertThat(downstream.getDependsOn()).containsExactly(replacement.getId());
        assertThat(replacement.getDependents()).containsExactly(downstream.getId());
        assertThat(root.getDependents()).isEmpty();
        verify(eventPublisher).publishEvent(any(TaskEvent.TaskRetriedEvent.class));
    }

    @Test
    void recoveryWaitsUntilEveryUpstreamDependencyIsCompleted() {
        AgentTask first = task("first", 0);
        AgentTask second = task("second", 0);
        AgentTask downstream = task("downstream", 0);
        first.setStatus(TaskStatus.BLOCKED_ESCALATED);
        second.setStatus(TaskStatus.BLOCKED_ESCALATED);
        downstream.setStatus(TaskStatus.BLOCKED_UPSTREAM_ESCALATION);
        first.addDependent(downstream.getId());
        second.addDependent(downstream.getId());
        downstream.addDependency(first.getId());
        downstream.addDependency(second.getId());
        save(first, second, downstream);

        manager.handleTaskRescue(first.getId());
        assertThat(downstream.getStatus()).isEqualTo(TaskStatus.BLOCKED_UPSTREAM_ESCALATION);

        manager.handleTaskRescue(second.getId());
        assertThat(downstream.getStatus()).isEqualTo(TaskStatus.PENDING);
    }

    @Test
    void maxRetriesIsTheNumberOfReplacementAttempts() {
        AgentTask root = task("root", 0);
        save(root);

        manager.handleTaskFailure(root.getId(), "first failure");

        assertThat(root.getStatus()).isEqualTo(TaskStatus.BLOCKED_ESCALATED);
        assertThat(root.getRetryCount()).isZero();
    }

    @Test
    void linkingDependencyUpdatesBothDirectionsAndRejectsCycles() {
        AgentTask first = task("first", 0);
        AgentTask second = task("second", 0);
        AgentTask third = task("third", 0);
        save(first, second, third);

        manager.linkDependency(first.getId(), second.getId());
        manager.linkDependency(second.getId(), third.getId());

        assertThat(first.getDependents()).containsExactly(second.getId());
        assertThat(second.getDependsOn()).containsExactly(first.getId());
        assertThat(second.getDependents()).containsExactly(third.getId());
        assertThat(third.getDependsOn()).containsExactly(second.getId());
        assertThatThrownBy(() -> manager.linkDependency(third.getId(), first.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void invalidTerminalTransitionIsRejected() {
        AgentTask completed = task("completed", 1);
        completed.setStatus(TaskStatus.COMPLETED);
        save(completed);

        assertThatThrownBy(() -> manager.handleTaskFailure(completed.getId(), "late failure"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLETED");
    }

    private AgentTask task(String id, int maxRetries) {
        return new AgentTask(id, id, maxRetries);
    }

    private void save(AgentTask... values) {
        for (AgentTask value : values) {
            tasks.put(value.getId(), value);
        }
    }
}
