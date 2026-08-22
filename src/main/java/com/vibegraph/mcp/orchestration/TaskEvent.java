package com.vibegraph.mcp.orchestration;

/**
 * Base interface for all Task DAG events.
 */
public sealed interface TaskEvent permits
    TaskEvent.TaskCompletedEvent,
    TaskEvent.TaskEscalatedEvent,
    TaskEvent.TaskFailedEvent,
    TaskEvent.TaskRetriedEvent,
    TaskEvent.RebaseEvent {

    String taskId();

    record TaskCompletedEvent(String taskId) implements TaskEvent {}

    record TaskEscalatedEvent(String taskId) implements TaskEvent {}

    record TaskFailedEvent(String taskId, String reason) implements TaskEvent {}

    record TaskRetriedEvent(String previousTaskId, String replacementTaskId, String reason)
            implements TaskEvent {
        @Override
        public String taskId() {
            return replacementTaskId;
        }
    }

    /** Published after retry edges have been atomically re-pointed to the replacement task. */
    record RebaseEvent(String previousTaskId, String replacementTaskId, java.util.Set<String> repointedDependents)
            implements TaskEvent {
        public RebaseEvent {
            repointedDependents = java.util.Set.copyOf(repointedDependents);
        }

        @Override
        public String taskId() {
            return replacementTaskId;
        }
    }
}
