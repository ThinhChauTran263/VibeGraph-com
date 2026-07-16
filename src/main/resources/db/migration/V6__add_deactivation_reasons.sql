ALTER TABLE users
    ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deactivation_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS deactivation_reason_safe VARCHAR(240);
