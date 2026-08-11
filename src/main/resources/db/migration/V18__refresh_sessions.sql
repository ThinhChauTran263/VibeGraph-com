-- Rotating refresh sessions. Raw refresh tokens are never stored.
CREATE TABLE IF NOT EXISTS refresh_sessions (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id      UUID         NOT NULL,
    token_hash     VARCHAR(64)  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_used_at   TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ,
    revoke_reason  VARCHAR(40),
    replaced_by_id UUID,
    CONSTRAINT uq_refresh_sessions_token_hash UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS idx_refresh_sessions_user_active
    ON refresh_sessions (user_id, revoked_at, expires_at);

CREATE INDEX IF NOT EXISTS idx_refresh_sessions_family
    ON refresh_sessions (family_id);
