package com.vibegraph.mcp.orchestration;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;

/**
 * Owns validated, transactional state transitions for the agent task DAG.
 *
 * <p>Every mutation locks the task row before changing it. Retry creates a new task identity and
 * atomically re-points all downstream edges, so workers never continue following a failed node.
 */
@Service
@RequiredArgsConstructor
public class TaskDagManager {

    private final AgentTaskRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AgentTask registerTask(AgentTask task) {
        if (task == null) {
            throw new IllegalArgumentException("task is required");
        }
        return taskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public AgentTask getTask(String taskId) {
        return taskRepository.findById(taskId).orElse(null);
    }

    /** Adds an upstream -> downstream edge while keeping both entity collections consistent. */
    @Transactional
    public void linkDependency(String upstreamTaskId, String downstreamTaskId) {
        if (upstreamTaskId == null || downstreamTaskId == null
                || upstreamTaskId.isBlank() || downstreamTaskId.isBlank()) {
            throw new IllegalArgumentException("Both task IDs are required");
        }
        if (upstreamTaskId.equals(downstreamTaskId)) {
            throw new IllegalArgumentException("A task cannot depend on itself");
        }
        AgentTask upstream = requiredForUpdate(upstreamTaskId);
        AgentTask downstream = requiredForUpdate(downstreamTaskId);
        if (reachable(downstream.getId(), upstream.getId())) {
            throw new IllegalArgumentException("Adding the dependency would create a cycle");
        }
        upstream.addDependent(downstream.getId());
        downstream.addDependency(upstream.getId());
        taskRepository.save(upstream);
        taskRepository.save(downstream);
    }

    /**
     * Records a failure. While retry budget remains, a replacement task is created and all
     * downstream dependencies are re-pointed in the same transaction. Otherwise the task is
     * escalated and active downstream tasks are blocked.
     */
    @Transactional
    public AgentTask handleTaskFailure(String taskId, String reason) {
        AgentTask task = requiredForUpdate(taskId);
        requireFailureTransition(task);
        if (task.getRetryCount() < task.getMaxRetries()) {
            String replacementId = task.getId() + ":retry:" + (task.getRetryCount() + 1) + ":" + UUID.randomUUID();
            AgentTask replacement = AgentTask.retryOf(task, replacementId);
            taskRepository.save(replacement);
            rePointEdges(task, replacement);
            task.setStatus(TaskStatus.SUPERSEDED);
            task.setReplacementTaskId(replacement.getId());
            taskRepository.save(task);
            publishAfterCommit(new TaskEvent.TaskRetriedEvent(
                    task.getId(), replacement.getId(), safeReason(reason)));
            publishAfterCommit(new TaskEvent.RebaseEvent(
                    task.getId(), replacement.getId(), replacement.getDependents()));
            return replacement;
        }

        task.setStatus(TaskStatus.BLOCKED_ESCALATED);
        cascadeEscalationDownstream(task);
        taskRepository.save(task);
        publishAfterCommit(new TaskEvent.TaskEscalatedEvent(task.getId()));
        return task;
    }

    /** Rescues an escalated task and only releases downstream nodes whose full upstream set is done. */
    @Transactional
    public void handleTaskRescue(String taskId) {
        AgentTask task = requiredForUpdate(taskId);
        if (task.getStatus() != TaskStatus.BLOCKED_ESCALATED) {
            return;
        }
        task.setStatus(TaskStatus.COMPLETED);
        taskRepository.save(task);
        cascadeRecoveryDownstream(task);
        publishAfterCommit(new TaskEvent.TaskCompletedEvent(taskId));
    }

    /** Completes a task after its worker returns successfully. */
    @Transactional
    public void handleTaskSuccess(String taskId) {
        AgentTask task = requiredForUpdate(taskId);
        if (task.getStatus() != TaskStatus.PENDING && task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot complete task in status " + task.getStatus());
        }
        task.setStatus(TaskStatus.COMPLETED);
        taskRepository.save(task);
        cascadeRecoveryDownstream(task);
        publishAfterCommit(new TaskEvent.TaskCompletedEvent(taskId));
    }

    /** Marks a merge-gated task complete and applies the same dependency readiness rules. */
    @Transactional
    public void handleMergeSuccess(String taskId) {
        AgentTask task = requiredForUpdate(taskId);
        if (task.getStatus() != TaskStatus.BLOCKED_ON_MERGE) {
            return;
        }
        task.setStatus(TaskStatus.COMPLETED);
        taskRepository.save(task);
        cascadeRecoveryDownstream(task);
        publishAfterCommit(new TaskEvent.TaskCompletedEvent(taskId));
    }

    private AgentTask requiredForUpdate(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        return taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }

    private void requireFailureTransition(AgentTask task) {
        if (task.getStatus() != TaskStatus.PENDING
                && task.getStatus() != TaskStatus.IN_PROGRESS
                && task.getStatus() != TaskStatus.BLOCKED_ON_MERGE) {
            throw new IllegalStateException("Cannot fail task in status " + task.getStatus());
        }
    }

    private void rePointEdges(AgentTask failed, AgentTask replacement) {
        for (String upstreamId : Set.copyOf(failed.getDependsOn())) {
            AgentTask upstream = requiredForUpdate(upstreamId);
            upstream.removeDependent(failed.getId());
            upstream.addDependent(replacement.getId());
            taskRepository.save(upstream);
        }
        Set<String> downstreamIds = Set.copyOf(failed.getDependents());
        for (String downstreamId : downstreamIds) {
            AgentTask downstream = requiredForUpdate(downstreamId);
            downstream.removeDependency(failed.getId());
            downstream.addDependency(replacement.getId());
            replacement.addDependent(downstream.getId());
            taskRepository.save(downstream);
        }
        failed.getDependsOn().clear();
        failed.getDependents().clear();
    }

    private void cascadeEscalationDownstream(AgentTask task) {
        ArrayDeque<String> queue = new ArrayDeque<>(task.getDependents());
        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            AgentTask dependent = requiredForUpdate(queue.removeFirst());
            if (!visited.add(dependent.getId())) {
                continue;
            }
            if (isActive(dependent.getStatus())) {
                dependent.setStatus(TaskStatus.BLOCKED_UPSTREAM_ESCALATION);
                taskRepository.save(dependent);
                queue.addAll(dependent.getDependents());
            }
        }
    }

    private void cascadeRecoveryDownstream(AgentTask task) {
        ArrayDeque<String> queue = new ArrayDeque<>(task.getDependents());
        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            AgentTask dependent = requiredForUpdate(queue.removeFirst());
            if (!visited.add(dependent.getId())) {
                continue;
            }
            if (dependent.getStatus() == TaskStatus.BLOCKED_UPSTREAM_ESCALATION
                    && allDependenciesCompleted(dependent)) {
                dependent.setStatus(TaskStatus.PENDING);
                taskRepository.save(dependent);
                queue.addAll(dependent.getDependents());
            }
        }
    }

    private boolean allDependenciesCompleted(AgentTask task) {
        return task.getDependsOn().stream()
                .map(this::requiredForUpdate)
                .allMatch(upstream -> upstream.getStatus() == TaskStatus.COMPLETED);
    }

    private boolean reachable(String startId, String targetId) {
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(startId);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (targetId.equals(current)) {
                return true;
            }
            AgentTask task = requiredForUpdate(current);
            queue.addAll(task.getDependents());
        }
        return false;
    }

    private boolean isActive(TaskStatus status) {
        return status == TaskStatus.PENDING
                || status == TaskStatus.IN_PROGRESS
                || status == TaskStatus.BLOCKED_ON_MERGE;
    }

    private String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Task failed";
        }
        return reason.length() <= 1000 ? reason : reason.substring(0, 1000);
    }

    private void publishAfterCommit(TaskEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventPublisher.publishEvent(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publishEvent(event);
            }
        });
    }
}
