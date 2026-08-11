-- =====================================================================
-- VibeGraph - Remove the orphaned system_control_settings table
-- =====================================================================
-- The table was created by a V16 migration whose script no longer exists in
-- the repository, and nothing in src/main reads it: the values it holds
--     import.concurrent.per-user / per-ip / global
-- are served by AbuseProperties (vibegraph.abuse.*) and consumed by
-- ConcurrentImportGuard. Verified by grep for both the table name and the
-- setting keys — zero references.
--
-- It is dropped rather than left in place because a config table nobody reads
-- is a trap: the next operator changes a limit here, restarts, and nothing
-- happens. Removing it makes the real source of configuration unambiguous.
--
-- If runtime-adjustable import limits are wanted later, reintroduce the table
-- together with the code that reads it.
-- =====================================================================

DROP TABLE IF EXISTS system_control_settings;
