CREATE TABLE IF NOT EXISTS agent_tasks (
    id          VARCHAR(120) PRIMARY KEY,
    description VARCHAR(1000) NOT NULL,
    status      VARCHAR(40) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_agent_task_status CHECK (status IN (
        'PENDING',
        'IN_PROGRESS',
        'BLOCKED_ON_MERGE',
        'BLOCKED_ESCALATED',
        'BLOCKED_UPSTREAM_ESCALATION',
        'COMPLETED',
        'FAILED_TERMINAL'
    )),
    CONSTRAINT chk_agent_task_retries CHECK (retry_count >= 0 AND max_retries >= 0)
);

CREATE TABLE IF NOT EXISTS agent_task_dependencies (
    task_id            VARCHAR(120) NOT NULL REFERENCES agent_tasks(id) ON DELETE CASCADE,
    dependency_task_id VARCHAR(120) NOT NULL,
    PRIMARY KEY (task_id, dependency_task_id)
);

CREATE TABLE IF NOT EXISTS agent_task_dependents (
    task_id           VARCHAR(120) NOT NULL REFERENCES agent_tasks(id) ON DELETE CASCADE,
    dependent_task_id VARCHAR(120) NOT NULL,
    PRIMARY KEY (task_id, dependent_task_id)
);

CREATE INDEX IF NOT EXISTS idx_agent_task_dependencies_upstream
    ON agent_task_dependencies (dependency_task_id);

CREATE INDEX IF NOT EXISTS idx_agent_task_dependents_downstream
    ON agent_task_dependents (dependent_task_id);
