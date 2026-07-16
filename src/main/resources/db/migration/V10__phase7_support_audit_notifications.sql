-- =====================================================================
-- VibeGraph - Phase 7 support notifications and audit logs
-- =====================================================================

ALTER TABLE announcements
    ADD COLUMN IF NOT EXISTS created_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_announcements_active_window
    ON announcements (active, starts_at, ends_at, created_at DESC);

CREATE TABLE IF NOT EXISTS user_notifications (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    announcement_id UUID        NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    read_at         TIMESTAMPTZ,
    dismissed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_notifications_user_announcement UNIQUE (user_id, announcement_id)
);

CREATE INDEX IF NOT EXISTS idx_user_notifications_user_created
    ON user_notifications (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS audit_logs (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    action          VARCHAR(80)   NOT NULL,
    actor_user_id   UUID          REFERENCES users(id) ON DELETE SET NULL,
    target_user_id  UUID          REFERENCES users(id) ON DELETE SET NULL,
    target_type     VARCHAR(80),
    target_id       VARCHAR(160),
    outcome         VARCHAR(20)   NOT NULL,
    ip_address      VARCHAR(64),
    details         VARCHAR(4000) NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_audit_logs_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created
    ON audit_logs (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_action_created
    ON audit_logs (action, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_created
    ON audit_logs (actor_user_id, created_at DESC)
    WHERE actor_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_audit_logs_target_user_created
    ON audit_logs (target_user_id, created_at DESC)
    WHERE target_user_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS audit_retention_settings (
    id              SMALLINT    PRIMARY KEY,
    retention_days  INTEGER     NOT NULL DEFAULT 90,
    updated_by      UUID        REFERENCES users(id) ON DELETE SET NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_audit_retention_days CHECK (retention_days BETWEEN 1 AND 3650)
);

CREATE TRIGGER trg_audit_retention_settings_updated
    BEFORE UPDATE ON audit_retention_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO audit_retention_settings (id, retention_days)
VALUES (1, 90)
ON CONFLICT (id) DO NOTHING;
