-- =====================================================================
-- VibeGraph - Per-account credit override
-- =====================================================================
-- Enterprise and manually-negotiated accounts need credit limits that can
-- differ from the base plan. This mirrors storage_quota_override_bytes and
-- lets admin UI show:
--   plan credits + override credits = effective monthly credits.
-- =====================================================================

ALTER TABLE user_account_settings
    ADD COLUMN IF NOT EXISTS credit_quota_override INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_account_settings_credit_override'
    ) THEN
        ALTER TABLE user_account_settings
            ADD CONSTRAINT chk_account_settings_credit_override
            CHECK (credit_quota_override IS NULL OR credit_quota_override >= 0);
    END IF;
END;
$$;
