CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE feedback_reports (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID,
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

CREATE INDEX idx_feedback_reports_user ON feedback_reports (user_id, created_at DESC);
CREATE INDEX idx_feedback_reports_status ON feedback_reports (status);
CREATE INDEX idx_feedback_reports_delete_after ON feedback_reports (delete_after)
    WHERE delete_after IS NOT NULL;

CREATE TABLE feedback_messages (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id      UUID        NOT NULL REFERENCES feedback_reports(id) ON DELETE CASCADE,
    sender_user_id UUID,
    sender_role    VARCHAR(20) NOT NULL,
    body           TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_feedback_messages_sender_role CHECK (sender_role IN ('USER','ADMIN'))
);

CREATE INDEX idx_feedback_messages_report ON feedback_messages (report_id, created_at);

CREATE TABLE project_runtime_status (
    project_id VARCHAR(64) PRIMARY KEY,
    status     VARCHAR(40) NOT NULL,
    progress   INTEGER NOT NULL,
    message    TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_project_runtime_progress CHECK (progress BETWEEN 0 AND 100)
);

CREATE INDEX idx_project_runtime_status_updated ON project_runtime_status (updated_at DESC);

CREATE TABLE request_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID,
    api_key_ref VARCHAR(120),
    ip_address  VARCHAR(120) NOT NULL,
    route       VARCHAR(240) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    status      INTEGER NOT NULL,
    event_type  VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_request_events_occurred ON request_events (occurred_at DESC);
CREATE INDEX idx_request_events_user_minute ON request_events (user_id, occurred_at DESC);
CREATE INDEX idx_request_events_ip_minute ON request_events (ip_address, occurred_at DESC);

CREATE TABLE security_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(60) NOT NULL,
    severity        VARCHAR(20) NOT NULL,
    subject_user_id UUID,
    api_key_ref     VARCHAR(120),
    source          VARCHAR(40),
    description     VARCHAR(1000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_security_events_severity CHECK (severity IN ('INFO','WARNING','CRITICAL'))
);

CREATE INDEX idx_security_events_created ON security_events (created_at DESC);
CREATE INDEX idx_security_events_user_created ON security_events (subject_user_id, created_at DESC)
    WHERE subject_user_id IS NOT NULL;

CREATE TABLE announcements (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type               VARCHAR(40) NOT NULL,
    severity           VARCHAR(20) NOT NULL,
    target             VARCHAR(40) NOT NULL,
    title              VARCHAR(160) NOT NULL,
    body               VARCHAR(2000) NOT NULL,
    starts_at          TIMESTAMPTZ,
    ends_at            TIMESTAMPTZ,
    dismissible        BOOLEAN NOT NULL DEFAULT true,
    active             BOOLEAN NOT NULL DEFAULT true,
    created_by_user_id UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_announcements_type CHECK (type IN ('MAINTENANCE','PLAN_CHANGE','DISK_WARNING','CLI_UPDATE','SECURITY','GENERAL')),
    CONSTRAINT chk_announcements_severity CHECK (severity IN ('INFO','WARNING','CRITICAL')),
    CONSTRAINT chk_announcements_target CHECK (target IN ('ALL','USER','ADMIN')),
    CONSTRAINT chk_announcements_window CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)
);

CREATE TRIGGER trg_announcements_updated
    BEFORE UPDATE ON announcements
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_announcements_active_window
    ON announcements (active, starts_at, ends_at, created_at DESC);

CREATE TABLE user_notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    announcement_id UUID NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    read_at         TIMESTAMPTZ,
    dismissed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_notifications_user_announcement UNIQUE (user_id, announcement_id)
);

CREATE INDEX idx_user_notifications_user_created
    ON user_notifications (user_id, created_at DESC);
CREATE INDEX idx_user_notifications_dismissed
    ON user_notifications (dismissed_at) WHERE dismissed_at IS NOT NULL;
