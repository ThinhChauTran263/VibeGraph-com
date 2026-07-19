-- Add optional project binding to API keys without invalidating legacy keys.
ALTER TABLE api_keys
    ADD COLUMN IF NOT EXISTS project_id VARCHAR(64);

ALTER TABLE api_keys
    DROP CONSTRAINT IF EXISTS fk_api_keys_project;

ALTER TABLE api_keys
    ADD CONSTRAINT fk_api_keys_project
    FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_api_keys_project ON api_keys (project_id);
