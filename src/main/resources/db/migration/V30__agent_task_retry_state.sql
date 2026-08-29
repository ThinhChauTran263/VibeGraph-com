ALTER TABLE agent_tasks
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS replacement_task_id VARCHAR(120);

ALTER TABLE agent_tasks
    DROP CONSTRAINT IF EXISTS chk_agent_task_status;

ALTER TABLE agent_tasks
    ADD CONSTRAINT chk_agent_task_status CHECK (status IN (
        'PENDING',
        'IN_PROGRESS',
        'BLOCKED_ON_MERGE',
        'BLOCKED_ESCALATED',
        'BLOCKED_UPSTREAM_ESCALATION',
        'SUPERSEDED',
        'COMPLETED',
        'FAILED_TERMINAL'
    ));

ALTER TABLE agent_tasks
    ADD CONSTRAINT fk_agent_task_replacement
    FOREIGN KEY (replacement_task_id) REFERENCES agent_tasks(id);

ALTER TABLE agent_task_dependencies
    ADD CONSTRAINT fk_agent_task_dependency_target
    FOREIGN KEY (dependency_task_id) REFERENCES agent_tasks(id) ON DELETE CASCADE;

ALTER TABLE agent_task_dependents
    ADD CONSTRAINT fk_agent_task_dependent_target
    FOREIGN KEY (dependent_task_id) REFERENCES agent_tasks(id) ON DELETE CASCADE;
