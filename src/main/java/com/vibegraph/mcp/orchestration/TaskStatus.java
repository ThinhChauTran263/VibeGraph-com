package com.vibegraph.mcp.orchestration;

/**
 * Represents the state of an Agentic Task within the Orchestrator DAG.
 */
public enum TaskStatus {
    /**
     * Task is waiting for upstream dependencies to complete or for OCC locks to clear.
     */
    PENDING,

    /**
     * Task is currently being executed by an Agent.
     */
    IN_PROGRESS,

    /**
     * Agent has submitted a PR/Patchset and is waiting for Tier-2 CI tests to pass.
     */
    BLOCKED_ON_MERGE,

    /**
     * Task has failed multiple times (max retries hit) and requires escalation to a Reviewer or Human.
     */
    BLOCKED_ESCALATED,

    /**
     * Task is pending, but an upstream dependency has failed and escalated, preventing this task from executing.
     */
    BLOCKED_UPSTREAM_ESCALATION,

    /**
     * The task was replaced by a retry task and must never be executed again.
     */
    SUPERSEDED,

    /**
     * Task has successfully completed and changes are merged into the main graph.
     */
    COMPLETED,

    /**
     * Task has terminally failed and cannot be recovered.
     */
    FAILED_TERMINAL
}
