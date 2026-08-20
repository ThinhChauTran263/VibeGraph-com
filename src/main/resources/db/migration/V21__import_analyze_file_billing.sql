-- =====================================================================
-- VibeGraph - File-count billing for imports and re-analysis
-- =====================================================================
-- Imports and project analysis were previously free even though V3/V9
-- already defined pricing rules for them. This activates metering based
-- on the number of imported .java files, known before the heavy analysis
-- runs: cost = base + ceil(files * per_file_credits), floored at
-- minimum_credits. All numbers remain admin-editable through
-- /api/admin/pricing-rules; the values below are product defaults
-- calibrated against the FREE plan's 100 credits/month.
-- =====================================================================

UPDATE credit_pricing_rules
SET base_credits = 2,
    per_file_credits = 0.01,
    per_mb_credits = 0,
    per_1k_nodes_credits = 0,
    minimum_credits = 2
WHERE operation_code IN ('IMPORT_ARCHIVE', 'IMPORT_GITHUB', 'PROJECT_ANALYZE');
