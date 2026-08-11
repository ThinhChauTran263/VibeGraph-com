-- =====================================================================
-- VibeGraph - Supabase least-privilege runtime role
-- =====================================================================
-- Run ONCE as an operator, with the migration (DDL) credential, BEFORE
-- the application starts for the first time. The application never runs
-- this: creating roles is an operator task.
--
-- Run it with scripts/provision-supabase-runtime-role.ps1, or directly. For an IPv4-only
-- network, use the Supabase Session pooler host on port 5432 and the qualified user
-- (postgres.<project-ref>):
--
--   VIBEGRAPH_SUPABASE_RUNTIME_PASSWORD="$RUNTIME_PASSWORD" psql \
--        "host=aws-0-REGION.pooler.supabase.com port=5432 dbname=postgres user=postgres.<project-ref>" \
--        --set=ON_ERROR_STOP=1 \
--        -v runtime_role=vibegraph_runtime \
--        -v db_name=postgres \
--        -v target_schema=vibegraph_realtime \
--        -f scripts/supabase-runtime-role.sql
--
-- runtime_password comes from your secret store. This file must never be
-- committed with a real password in it.
-- =====================================================================

\set ON_ERROR_STOP on
\getenv runtime_password VIBEGRAPH_SUPABASE_RUNTIME_PASSWORD

\if :{?runtime_password}
\else
    \quit 3
\endif

\if :{?require_tables}
\else
    \set require_tables false
\endif

BEGIN;

-- 1. The DDL credential owns the schema. The runtime role never will.
CREATE SCHEMA IF NOT EXISTS :"target_schema";

-- 2. Create or update the runtime login role. Keep the password out of query
--    result sets: the previous SELECT/\gexec approach echoed generated SQL.
SELECT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = :'runtime_role'
) AS runtime_role_exists \gset

\if :runtime_role_exists
    -- Supabase's managed session-pooler role may change an existing role's
    -- password, but it cannot alter SUPERUSER attributes. Verification below
    -- will fail safely if an operator-created role is not least-privileged.
    ALTER ROLE :"runtime_role" LOGIN PASSWORD :'runtime_password';
\else
    -- State all restrictive attributes at creation time. False attributes do
    -- not require superuser privilege and avoid a later NOSUPERUSER ALTER.
    CREATE ROLE :"runtime_role"
        LOGIN
        NOSUPERUSER
        NOCREATEDB
        NOCREATEROLE
        NOREPLICATION
        PASSWORD :'runtime_password';
\endif

-- 3. Connect and schema usage only. No CREATE, so the runtime role cannot add
--    or replace objects anywhere.
GRANT CONNECT ON DATABASE :"db_name" TO :"runtime_role";
GRANT USAGE   ON SCHEMA   :"target_schema" TO :"runtime_role";
REVOKE CREATE ON SCHEMA   :"target_schema" FROM :"runtime_role";
REVOKE CREATE ON SCHEMA   public FROM :"runtime_role";

-- 4. CRUD on the tables that already exist.
GRANT SELECT, INSERT, UPDATE, DELETE
    ON ALL TABLES IN SCHEMA :"target_schema" TO :"runtime_role";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA :"target_schema" TO :"runtime_role";

-- 5. The same rights for tables a future Flyway migration creates, so grants
--    never have to be re-applied by hand after a deployment.
ALTER DEFAULT PRIVILEGES IN SCHEMA :"target_schema"
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"runtime_role";
ALTER DEFAULT PRIVILEGES IN SCHEMA :"target_schema"
    GRANT USAGE, SELECT ON SEQUENCES TO :"runtime_role";

COMMIT;

-- 6. Verification. Every row must report the expected value, otherwise the
--    runtime role is not least-privilege and the cutover must stop.
\echo ''
\echo '=== runtime role verification ==='
SELECT 'role is not a superuser'      AS check, NOT rolsuper   AS expected_true FROM pg_roles WHERE rolname = :'runtime_role'
UNION ALL
SELECT 'role cannot create roles',         NOT rolcreaterole   FROM pg_roles WHERE rolname = :'runtime_role'
UNION ALL
SELECT 'role cannot create databases',     NOT rolcreatedb     FROM pg_roles WHERE rolname = :'runtime_role'
UNION ALL
SELECT 'role cannot CREATE in the schema',
       NOT has_schema_privilege(:'runtime_role', :'target_schema', 'CREATE')
UNION ALL
SELECT 'role cannot CREATE in public',
       NOT has_schema_privilege(:'runtime_role', 'public', 'CREATE')
UNION ALL
SELECT 'role can USAGE the schema',
       has_schema_privilege(:'runtime_role', :'target_schema', 'USAGE');

SELECT to_regclass(format('%I.request_events', :'target_schema')) IS NOT NULL
    AS request_events_present \gset

\if :request_events_present
    SELECT 'role can SELECT request_events' AS check,
           has_table_privilege(:'runtime_role', format('%I.request_events', :'target_schema'), 'SELECT')
    UNION ALL
    SELECT 'role can INSERT request_events',
           has_table_privilege(:'runtime_role', format('%I.request_events', :'target_schema'), 'INSERT')
    UNION ALL
    SELECT 'role can DELETE request_events',
           has_table_privilege(:'runtime_role', format('%I.request_events', :'target_schema'), 'DELETE');
\else
    \echo '=== table privilege verification: PENDING (request_events does not exist yet) ==='
    \if :require_tables
        \quit 3
    \endif
\endif

-- 7. Raw telemetry must stay out of Realtime. This must return zero rows.
\echo ''
\echo '=== realtime publication gate (must be empty) ==='
SELECT schemaname, tablename
FROM pg_publication_tables
WHERE pubname = 'supabase_realtime'
  AND tablename IN ('request_events', 'security_events');
