-- Phase 7 anti-abuse request monitoring and exact IP blocks.
CREATE TABLE IF NOT EXISTS request_events (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         REFERENCES users(id) ON DELETE SET NULL,
    api_key_ref  VARCHAR(120),
    ip_address   VARCHAR(120)  NOT NULL,
    route        VARCHAR(240)  NOT NULL,
    http_method  VARCHAR(10)   NOT NULL,
    status       INTEGER       NOT NULL,
    event_type   VARCHAR(40)   NOT NULL,
    occurred_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_request_events_occurred ON request_events (occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_request_events_user_minute ON request_events (user_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_request_events_ip_minute ON request_events (ip_address, occurred_at DESC);

CREATE TABLE IF NOT EXISTS ip_blocks (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    ip_address  VARCHAR(120) NOT NULL UNIQUE,
    safe_reason VARCHAR(240) NOT NULL,
    expires_at  TIMESTAMPTZ,
    created_by  UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    active      BOOLEAN      NOT NULL DEFAULT true
);
CREATE INDEX IF NOT EXISTS idx_ip_blocks_active ON ip_blocks (ip_address, active, expires_at);
CREATE TRIGGER trg_ip_blocks_updated BEFORE UPDATE ON ip_blocks
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
