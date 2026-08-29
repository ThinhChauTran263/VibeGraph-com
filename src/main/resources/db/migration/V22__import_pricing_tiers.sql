-- =====================================================================
-- VibeGraph - Tiered import pricing (small / medium / large / xlarge)
-- =====================================================================
-- Replaces the per-file formula (V21) for import/analyze operations with
-- fixed credits per project-size tier. Each operation owns its own tier
-- set: thresholds and credits are configured PER METHOD in the admin UI
-- ("Import Pricing"), nothing is hardcoded beyond the seeded defaults.
--
-- Tier selection: the first tier whose max_files covers the imported
-- .java file count wins; max_files NULL means "unlimited" (the top tier).
-- =====================================================================

CREATE TABLE IF NOT EXISTS import_pricing_tiers (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_code VARCHAR(64) NOT NULL,
    tier_code      VARCHAR(20) NOT NULL,
    max_files      INTEGER,
    credits        INTEGER     NOT NULL,
    sort_order     INTEGER     NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_import_pricing_tier UNIQUE (operation_code, tier_code),
    CONSTRAINT chk_import_tier_credits CHECK (credits >= 0),
    CONSTRAINT chk_import_tier_max_files CHECK (max_files IS NULL OR max_files >= 0),
    CONSTRAINT chk_import_tier_sort CHECK (sort_order >= 0)
);

CREATE TRIGGER trg_import_pricing_tiers_updated
    BEFORE UPDATE ON import_pricing_tiers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX IF NOT EXISTS idx_import_pricing_tiers_operation
    ON import_pricing_tiers (operation_code, sort_order);

INSERT INTO import_pricing_tiers (operation_code, tier_code, max_files, credits, sort_order)
VALUES
    -- Archive upload import
    ('IMPORT_ARCHIVE', 'SMALL',  100,  2, 10),
    ('IMPORT_ARCHIVE', 'MEDIUM', 500,  5, 20),
    ('IMPORT_ARCHIVE', 'LARGE',  2000, 15, 30),
    ('IMPORT_ARCHIVE', 'XLARGE', NULL, 40, 40),
    -- GitHub import
    ('IMPORT_GITHUB',  'SMALL',  100,  2, 10),
    ('IMPORT_GITHUB',  'MEDIUM', 500,  5, 20),
    ('IMPORT_GITHUB',  'LARGE',  2000, 15, 30),
    ('IMPORT_GITHUB',  'XLARGE', NULL, 40, 40),
    -- Local folder import (newly metered)
    ('IMPORT_LOCAL',   'SMALL',  100,  2, 10),
    ('IMPORT_LOCAL',   'MEDIUM', 500,  5, 20),
    ('IMPORT_LOCAL',   'LARGE',  2000, 15, 30),
    ('IMPORT_LOCAL',   'XLARGE', NULL, 40, 40),
    -- Re-analysis of an existing project
    ('PROJECT_ANALYZE','SMALL',  100,  2, 10),
    ('PROJECT_ANALYZE','MEDIUM', 500,  5, 20),
    ('PROJECT_ANALYZE','LARGE',  2000, 15, 30),
    ('PROJECT_ANALYZE','XLARGE', NULL, 40, 40)
ON CONFLICT (operation_code, tier_code) DO UPDATE
SET max_files = EXCLUDED.max_files,
    credits = EXCLUDED.credits,
    sort_order = EXCLUDED.sort_order;

-- The flat per-file rules from V21 are superseded by the tier table for
-- these operations; deactivate them so admins are not tempted to tune a
-- rule that is no longer consulted. MCP/CLI_PUSH keep the flat model.
UPDATE credit_pricing_rules
SET is_active = false
WHERE operation_code IN ('IMPORT_ARCHIVE', 'IMPORT_GITHUB', 'PROJECT_ANALYZE');
