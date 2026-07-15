-- =====================================================================
-- VibeGraph - Admin ops surfaces
-- =====================================================================

CREATE TABLE IF NOT EXISTS feature_flags (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    flag_key     VARCHAR(120) NOT NULL UNIQUE,
    scope        VARCHAR(20)  NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT true,
    description  VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_feature_flags_scope CHECK (scope IN ('GLOBAL', 'MCP_TOOL'))
);

CREATE TRIGGER trg_feature_flags_updated
    BEFORE UPDATE ON feature_flags
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE IF NOT EXISTS announcements (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    type        VARCHAR(40)   NOT NULL,
    severity    VARCHAR(20)   NOT NULL,
    target      VARCHAR(40)   NOT NULL,
    title       VARCHAR(160)  NOT NULL,
    body        VARCHAR(2000) NOT NULL,
    starts_at   TIMESTAMPTZ,
    ends_at     TIMESTAMPTZ,
    dismissible BOOLEAN       NOT NULL DEFAULT true,
    active      BOOLEAN       NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_announcements_type CHECK (type IN ('MAINTENANCE','PLAN_CHANGE','DISK_WARNING','CLI_UPDATE','SECURITY','GENERAL')),
    CONSTRAINT chk_announcements_severity CHECK (severity IN ('INFO','WARNING','CRITICAL')),
    CONSTRAINT chk_announcements_target CHECK (target IN ('ALL','USER','ADMIN')),
    CONSTRAINT chk_announcements_window CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)
);

CREATE TRIGGER trg_announcements_updated
    BEFORE UPDATE ON announcements
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE IF NOT EXISTS security_events (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(60)   NOT NULL,
    severity        VARCHAR(20)   NOT NULL,
    subject_user_id UUID          REFERENCES users(id) ON DELETE SET NULL,
    api_key_ref     VARCHAR(120),
    source          VARCHAR(40),
    description     VARCHAR(1000),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_security_events_type CHECK (event_type IN (
        'RATE_LIMIT','FAILED_LOGIN','SUSPICIOUS_API','SUSPICIOUS_API_KEY','SUSPICIOUS_MCP','SUSPICIOUS_CLI'
    )),
    CONSTRAINT chk_security_events_severity CHECK (severity IN ('INFO','WARNING','CRITICAL'))
);

CREATE INDEX IF NOT EXISTS idx_security_events_created
    ON security_events (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_security_events_user_created
    ON security_events (subject_user_id, created_at DESC)
    WHERE subject_user_id IS NOT NULL;
