CREATE TABLE IF NOT EXISTS cli_device_authorizations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_code_hash    VARCHAR(64) NOT NULL UNIQUE,
    browser_secret_hash VARCHAR(64) NOT NULL,
    poll_secret_hash    VARCHAR(64) NOT NULL,
    code_challenge      VARCHAR(128) NOT NULL,
    user_code           VARCHAR(16) NOT NULL,
    device_name         VARCHAR(120),
    status              VARCHAR(16) NOT NULL,
    user_id             UUID REFERENCES users(id) ON DELETE CASCADE,
    project_id          VARCHAR(64) REFERENCES projects(project_id) ON DELETE SET NULL,
    project_name        VARCHAR(255),
    api_key_id          UUID REFERENCES api_keys(id) ON DELETE SET NULL,
    credential_cipher   TEXT,
    expires_at          TIMESTAMPTZ NOT NULL,
    approved_at         TIMESTAMPTZ,
    consumed_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_cli_device_status
        CHECK (status IN ('PENDING', 'APPROVED', 'CONSUMED', 'EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_cli_device_expires_at
    ON cli_device_authorizations (expires_at);

CREATE INDEX IF NOT EXISTS idx_cli_device_user_project
    ON cli_device_authorizations (user_id, project_id);
