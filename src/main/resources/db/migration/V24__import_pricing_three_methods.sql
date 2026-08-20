-- =====================================================================
-- VibeGraph - Import pricing aligned with the three import methods
-- =====================================================================
-- The product exposes exactly three import methods (archive upload,
-- GitHub, CLI push); the removed local-folder import and the re-analyze
-- operation no longer belong in the tier table ("Import Pricing" panel).
--
--   * CLI_PUSH moves from the flat per-file model to the tier table so
--     admins tune it in "Import Pricing" like the other import methods.
--   * IMPORT_LOCAL tiers are dropped together with the removed feature.
--   * PROJECT_ANALYZE returns to the flat model; its credit_pricing_rules
--     row is reactivated so it stays admin-editable in "Plans & Credits".
-- =====================================================================

-- CLI push joins the tier model with the same calibrated defaults.
INSERT INTO import_pricing_tiers (operation_code, tier_code, max_files, credits, sort_order)
VALUES
    ('CLI_PUSH', 'SMALL',  100,  2, 10),
    ('CLI_PUSH', 'MEDIUM', 500,  5, 20),
    ('CLI_PUSH', 'LARGE',  2000, 15, 30),
    ('CLI_PUSH', 'XLARGE', NULL, 40, 40)
ON CONFLICT (operation_code, tier_code) DO UPDATE
SET max_files = EXCLUDED.max_files,
    credits = EXCLUDED.credits,
    sort_order = EXCLUDED.sort_order;

-- Removed feature + operation no longer billed by tiers.
DELETE FROM import_pricing_tiers
WHERE operation_code IN ('IMPORT_LOCAL', 'PROJECT_ANALYZE');

-- Flat model: CLI_PUSH is superseded by tiers; PROJECT_ANALYZE is consulted again.
UPDATE credit_pricing_rules
SET is_active = false
WHERE operation_code = 'CLI_PUSH';

UPDATE credit_pricing_rules
SET is_active = true
WHERE operation_code = 'PROJECT_ANALYZE';

-- The local-folder import feature gate no longer exists.
DELETE FROM feature_flags WHERE flag_key = 'import.local';
