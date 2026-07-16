-- =====================================================================
-- VibeGraph — Phase 4 account foundation
-- =====================================================================
-- Adds plan/account settings, source-storage usage, feedback report
-- storage, and aligns API key columns without adding management APIs.
-- =====================================================================

CREATE TABLE plans (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(32)  NOT NULL UNIQUE,
    name                VARCHAR(120) NOT NULL,
    storage_limit_bytes BIGINT       NOT NULL,
    api_key_limit       INTEGER      NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_plans_storage_limit CHECK (storage_limit_bytes >= 0),
    CONSTRAINT chk_plans_api_key_limit CHECK (api_key_limit >= 0)
);

CREATE TRIGGER trg_plans_updated
    BEFORE UPDATE ON plans
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO plans (code, name, storage_limit_bytes, api_key_limit)
VALUES ('FREE', 'Free', 524288000, 3)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    storage_limit_bytes = EXCLUDED.storage_limit_bytes,
    api_key_limit = EXCLUDED.api_key_limit;

CREATE TABLE user_account_settings (
    user_id                      UUID        PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    plan_id                      UUID        NOT NULL REFERENCES plans(id),
    storage_quota_override_bytes BIGINT,
    api_key_creation_disabled    BOOLEAN     NOT NULL DEFAULT false,
    blocked_at                   TIMESTAMPTZ,
    blocked_reason               VARCHAR(255),
    blocked_reason_safe          VARCHAR(255),
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_account_settings_quota_override
        CHECK (storage_quota_override_bytes IS NULL OR storage_quota_override_bytes >= 0)
);

CREATE TRIGGER trg_user_account_settings_updated
    BEFORE UPDATE ON user_account_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_user_account_settings_plan ON user_account_settings (plan_id);

INSERT INTO user_account_settings (user_id, plan_id)
SELECT u.id, p.id
FROM users u
CROSS JOIN plans p
WHERE p.code = 'FREE'
ON CONFLICT (user_id) DO NOTHING;

CREATE TABLE project_usage (
    project_id    VARCHAR(64) PRIMARY KEY REFERENCES projects(project_id) ON DELETE CASCADE,
    owner_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    storage_bytes BIGINT      NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_project_usage_storage CHECK (storage_bytes >= 0)
);

CREATE TRIGGER trg_project_usage_updated
    BEFORE UPDATE ON project_usage
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO project_usage (project_id, owner_id, storage_bytes)
SELECT project_id, owner_id, size_bytes
FROM projects
ON CONFLICT (project_id) DO NOTHING;

CREATE INDEX idx_project_usage_owner ON project_usage (owner_id);

CREATE TABLE feedback_reports (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         REFERENCES users(id) ON DELETE SET NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    category     VARCHAR(20)  NOT NULL,
    title        VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at    TIMESTAMPTZ,
    delete_after TIMESTAMPTZ,
    CONSTRAINT chk_feedback_reports_status CHECK (status IN ('OPEN','CLOSED')),
    CONSTRAINT chk_feedback_reports_category CHECK (category IN ('BUG','PROJECT','QUOTA','FEATURE','OTHER')),
    CONSTRAINT chk_feedback_reports_delete_after CHECK (status = 'CLOSED' OR delete_after IS NULL)
);

CREATE INDEX idx_feedback_reports_user ON feedback_reports (user_id);
CREATE INDEX idx_feedback_reports_status ON feedback_reports (status);
CREATE INDEX idx_feedback_reports_delete_after ON feedback_reports (delete_after)
    WHERE delete_after IS NOT NULL;

CREATE TABLE feedback_messages (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id      UUID        NOT NULL REFERENCES feedback_reports(id) ON DELETE CASCADE,
    sender_user_id UUID        REFERENCES users(id) ON DELETE SET NULL,
    sender_role    VARCHAR(20) NOT NULL,
    body           TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_feedback_messages_sender_role CHECK (sender_role IN ('USER','ADMIN'))
);

CREATE INDEX idx_feedback_messages_report ON feedback_messages (report_id);
CREATE INDEX idx_feedback_messages_sender_user ON feedback_messages (sender_user_id);

ALTER TABLE api_keys RENAME COLUMN prefix TO key_prefix;
ALTER TABLE api_keys RENAME COLUMN revoked_at TO disabled_at;
