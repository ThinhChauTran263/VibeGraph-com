-- API key lifecycle metadata and one-live-key-per-user-project protection.
ALTER TABLE api_keys
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS disabled_by VARCHAR(16),
    ADD COLUMN IF NOT EXISTS disabled_reason VARCHAR(255);

ALTER TABLE api_keys
    DROP CONSTRAINT IF EXISTS chk_api_keys_disabled_by;

ALTER TABLE api_keys
    ADD CONSTRAINT chk_api_keys_disabled_by
    CHECK (disabled_by IS NULL OR disabled_by IN ('USER', 'ADMIN'));

UPDATE api_keys
SET disabled_by = 'ADMIN',
    disabled_reason = COALESCE(disabled_reason, 'Legacy disabled key locked conservatively')
WHERE disabled_at IS NOT NULL AND disabled_by IS NULL;

CREATE INDEX IF NOT EXISTS idx_api_keys_deleted_at ON api_keys (deleted_at);
CREATE INDEX IF NOT EXISTS idx_api_keys_auth_candidates
    ON api_keys (key_prefix)
    WHERE deleted_at IS NULL AND disabled_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_api_keys_live_user_project
    ON api_keys (user_id, project_id)
    WHERE deleted_at IS NULL AND project_id IS NOT NULL;
