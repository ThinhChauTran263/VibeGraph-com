-- =====================================================================
-- VibeGraph - Refresh session retention support
-- =====================================================================
-- Rotation only ever INSERTs: an active user produces roughly one row per
-- access-token lifetime, so refresh_sessions grows for the life of the
-- deployment unless something deletes the dead rows.
--
-- RefreshSessionService.purgeExpiredSessions() runs
--   DELETE FROM refresh_sessions WHERE expires_at < :cutoff
-- daily. Without an index on expires_at that is a full table scan against
-- exactly the table this migration exists to keep large.
-- =====================================================================

CREATE INDEX IF NOT EXISTS idx_refresh_sessions_expires_at
    ON refresh_sessions (expires_at);

COMMENT ON INDEX idx_refresh_sessions_expires_at IS
    'Supports the retention sweep that deletes expired refresh sessions.';
