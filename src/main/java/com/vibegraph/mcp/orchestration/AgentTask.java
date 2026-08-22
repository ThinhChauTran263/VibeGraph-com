package com.vibegraph.mcp.orchestration;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a node in the Task DAG managed by the Orchestrator.
 */
@Entity
@Table(name = "agent_tasks")
public class AgentTask {

    @Id
    @Column(name = "id", nullable = false, length = 120)
    private String id;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TaskStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;
    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "replacement_task_id", length = 120)
    private String replacementTaskId;

    // IDs of tasks this task explicitly depends on
    @ElementCollection
    @CollectionTable(name = "agent_task_dependencies", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "dependency_task_id", nullable = false, length = 120)
    private Set<String> dependsOn;

    // IDs of tasks that explicitly depend on this task
    @ElementCollection
    @CollectionTable(name = "agent_task_dependents", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "dependent_task_id", nullable = false, length = 120)
    private Set<String> dependents;

    // Protected no-arg constructor required by JPA
    protected AgentTask() {
        this.dependsOn = new HashSet<>();
        this.dependents = new HashSet<>();
    }

    public AgentTask(String id, String description, int maxRetries) {
        this();
        if (id == null || id.isBlank() || id.length() > 120) {
            throw new IllegalArgumentException("Task id must be non-blank and at most 120 characters");
        }
        if (description == null || description.isBlank() || description.length() > 1000) {
            throw new IllegalArgumentException("Task description must be non-blank and at most 1000 characters");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be non-negative");
        }
        this.id = id;
        this.description = description.trim();
        this.status = TaskStatus.PENDING;
        this.retryCount = 0;
        this.maxRetries = maxRetries;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public Set<String> getDependsOn() {
        return dependsOn;
    }

    public void addDependency(String upstreamTaskId) {
        this.dependsOn.add(upstreamTaskId);
    }

    public void removeDependency(String upstreamTaskId) {
        this.dependsOn.remove(upstreamTaskId);
    }

    public Set<String> getDependents() {
        return dependents;
    }

    public void addDependent(String downstreamTaskId) {
        this.dependents.add(downstreamTaskId);
    }

    public void removeDependent(String downstreamTaskId) {
        this.dependents.remove(downstreamTaskId);
    }

    public String getReplacementTaskId() {
        return replacementTaskId;
    }

    void setReplacementTaskId(String replacementTaskId) {
        this.replacementTaskId = replacementTaskId;
    }

    static AgentTask retryOf(AgentTask source, String replacementId) {
        AgentTask replacement = new AgentTask(replacementId, source.description, source.maxRetries);
        replacement.setRetryCount(source.retryCount + 1);
        replacement.dependsOn.addAll(source.dependsOn);
        replacement.dependents.addAll(source.dependents);
        replacement.status = TaskStatus.IN_PROGRESS;
        return replacement;
    }
}
