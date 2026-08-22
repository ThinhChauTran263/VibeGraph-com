ALTER TABLE agent_tasks
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_agent_tasks_retention
    ON agent_tasks (COALESCE(completed_at, updated_at), id)
    WHERE status IN ('COMPLETED', 'SUPERSEDED', 'FAILED_TERMINAL');

CREATE INDEX IF NOT EXISTS idx_agent_tasks_replacement_task
    ON agent_tasks (replacement_task_id)
    WHERE replacement_task_id IS NOT NULL;
