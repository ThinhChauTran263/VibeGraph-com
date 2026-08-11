-- =====================================================================
-- VibeGraph - Project trash (soft delete with retention)
-- =====================================================================
-- Deleting a project moves it to a trash state instead of destroying it
-- immediately. The data plane (Neo4j graph + extracted sources) is kept
-- untouched during the retention window so a restore is a pure
-- control-plane operation. A scheduled job performs the real, irreversible
-- delete once the window expires.
--
-- NULL deleted_at means "live". Every owner-scoped listing filters on it,
-- so a trashed project disappears from the API, the UI and the MCP tools
-- without each call site needing its own check.
--
-- Trashed projects still occupy storage, so they intentionally keep
-- counting toward the owner's quota until they are purged.
-- =====================================================================

ALTER TABLE projects ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- Partial index: only trashed rows are ever scanned by the trash listing and
-- the retention sweep, and live lookups stay off this index entirely.
CREATE INDEX IF NOT EXISTS idx_projects_deleted_at
    ON projects (deleted_at)
    WHERE deleted_at IS NOT NULL;

COMMENT ON COLUMN projects.deleted_at IS
    'When the owner moved this project to trash. NULL means live. A scheduled job '
    'permanently deletes rows whose retention window has expired.';
