# Supabase Setup Guide

Step-by-step configuration, from an empty Supabase project to a verified cutover. Every step is
runnable; the only inputs you supply are your own project credentials and your peak traffic figure.

The application ships with `VIBEGRAPH_SUPABASE_ENABLED=false` and keeps using the primary database
until you finish step 8.

---

## 0. Prerequisites

```bash
winget install PostgreSQL.PostgreSQL.16
```

`pg_dump` and `psql` must be on `PATH`. Verify:

```bash
psql --version
```

Docker is needed only for the local rehearsal in step 4.

---

## 1. Create the Supabase project and collect connection details

In the Supabase dashboard: **Project Settings → Database → Connection info**. Note the host, port,
database name and the database password. On an IPv6-capable network, the direct host is fine. On an
IPv4-only network, use the **Session Pooler** host on port `5432`; it provides a normal session and
is suitable for Flyway and `psql`. Do not use the **Transaction Pooler** on port `6543` for migrations
or backfill.

When using the Session Pooler, qualify the username with the project reference:
`postgres.<project-ref>` for the migration role and `vibegraph_runtime.<project-ref>` for the runtime
role.

Two credentials are used from here on:

| Credential | Used for | Role |
|---|---|---|
| **Migration** | creating the schema, Flyway DDL, backfill, verification | `postgres` (or another owner) |
| **Runtime** | the application's CRUD traffic | `vibegraph_runtime`, created in step 2 |

---

## 2. Provision the least-privilege runtime role

```bash
pwsh -NoProfile -File scripts/provision-supabase-runtime-role.ps1 -DatabaseHost aws-0-ap-southeast-1.pooler.supabase.com -Database postgres -MigrationUser postgres.YOUR-REF
```

### Supplying the passwords

Every script takes credentials in one of three ways, in this order of preference:

1. **Interactive prompt (simplest).** Run the script with no credential set and it asks, without
   echoing. The value goes straight into a `SecureString`, then a temporary `PGPASSFILE` with an
   owner-only ACL that is deleted in `finally`. It never reaches the environment, the process list
   or disk. This is the default when a console is attached.
2. **`-MigrationCredential` / `-RuntimeCredential`** with a `PSCredential`, for scripted runs.
3. **Environment variables**, for CI:
   `VIBEGRAPH_SUPABASE_MIGRATION_PASSWORD`, `VIBEGRAPH_SUPABASE_RUNTIME_PASSWORD`.

An environment variable only lives in the shell that set it, and `pwsh -File` starts a child
process — a forgotten export is the usual reason a script reports a missing password. When no
console is attached (CI, piped input) the scripts fail closed instead of blocking on a prompt.

> Re-running provisioning issues `ALTER ROLE ... PASSWORD`. Type the **same** runtime password you
> used before, or you will silently rotate the role's password and the backend will stop connecting.

The script creates `vibegraph_runtime`, grants CRUD plus default privileges, revokes `CREATE`
everywhere, then prints a verification table. Because the tables do not exist yet, the first run may
show `PENDING (request_events does not exist yet)`; that is expected. Do not use `-RequireTables` on
this first run. After step 3 creates the tables, rerun with `-RequireTables`; then every verification
row must read `True`, and the Realtime publication gate at the bottom must return zero rows.

---

## 3. Apply the dedicated Supabase schema

Run the Flyway migration explicitly before any backfill. It uses only the migration credential and
does not start the application:

```powershell
.\scripts\migrate-supabase-schema.ps1 `
  -DatabaseHost aws-0-ap-southeast-1.pooler.supabase.com `
  -Database postgres `
  -MigrationUser postgres.YOUR-REF `
  -MigrationCredential $migrationCredential
```

If you are using the direct host, replace the host and username with the dashboard values. After it
succeeds, rerun provisioning strictly:

```powershell
.\scripts\provision-supabase-runtime-role.ps1 `
  -DatabaseHost aws-0-ap-southeast-1.pooler.supabase.com `
  -Database postgres `
  -MigrationUser postgres.YOUR-REF `
  -MigrationCredential $migrationCredential `
  -RuntimeCredential $runtimeCredential `
  -RequireTables
```

The migration is idempotent. If provisioning reports an error after `COMMIT`, inspect the target
before retrying: role and grant changes made before that point may already be present.

---

## 4. Rehearse the backfill locally (no Supabase involved)

```bash
pwsh -NoProfile -File scripts/tests/backfill-rehearsal.ps1
```

This runs the real generated import transaction, canonical checksums, semantic verification and the
publication gate against a throwaway PostgreSQL container, using a separate source and target
database so it mirrors a real cutover. All 14 checks must pass. It is the cheapest way to catch a
problem before touching your project.

---

## 5. Size the telemetry pipeline

Look up your **peak requests/second per instance** in the sizing table in
[supabase-capacity-policy.md](./supabase-capacity-policy.md) and set
`SUPABASE_TELEMETRY_FRESH_BATCHES_PER_CYCLE` accordingly. The default of `1` caps drain at
**125 events/second per instance**; above that the queue fills and the oldest telemetry is dropped.

After startup, confirm the log line matches what you expect:

```
Telemetry drain ceiling is ~<N> events/second per instance
```

---

## 6. Fill in the capacity policy

Complete the `TBD` rows in [supabase-capacity-policy.md](./supabase-capacity-policy.md): storage
budget, retention, acceptable drop rate, and whether `security_events` is best-effort or
audit-grade. The cutover checklist at the bottom of that file is the gate.

---

## 7. Backfill during a maintenance window

### First decide whether you need this step at all

Count the source rows before planning a maintenance window:

```bash
docker exec -i vibegraph-postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -A -F"|"' <<'SQL'
SELECT 'feedback_reports' AS t, count(*) FROM feedback_reports
UNION ALL SELECT 'feedback_messages', count(*) FROM feedback_messages
UNION ALL SELECT 'announcements', count(*) FROM announcements
UNION ALL SELECT 'user_notifications', count(*) FROM user_notifications
UNION ALL SELECT 'request_events', count(*) FROM request_events
UNION ALL SELECT 'security_events', count(*) FROM security_events;
SQL
```

Read the shell variables **inside** the container: `$POSTGRES_USER` and `$POSTGRES_DB` come from
docker-compose and are not set in your host shell.

If the only rows are development telemetry — check the top routes, and how many rows are already
past the retention window — skip this step and start clean. `request_events` in particular is
regenerated continuously and expires after 14 days, so migrating it is usually wasted effort. The
source tables are never modified, so you can still backfill later with `-Resume` and a matching
manifest.

```bash
docker exec -i vibegraph-postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -A -F"|"' <<'SQL'
SELECT route, count(*) FROM request_events GROUP BY route ORDER BY count(*) DESC LIMIT 5;
SELECT count(*) FILTER (WHERE occurred_at > now() - interval '14 days') AS within_retention,
       count(*) AS total FROM request_events;
SQL
```

### If you do need it

Pause **every** writer first — HTTP writers, scheduled jobs and all backend instances — and keep
them paused until step 8 finishes. A single `pg_dump` gives one snapshot across the six tables, but
it does not close the write gap; only the maintenance window does.

Importing through `pg_dump --column-inserts` writes one INSERT per row inside a single transaction.
Over a remote session pooler that is slow for large tables — budget accordingly, or trim expired
telemetry first.

```bash
$env:VIBEGRAPH_BACKFILL_SOURCE_PASSWORD = '...'
```

```bash
$env:VIBEGRAPH_BACKFILL_TARGET_PASSWORD = '...'
```

```bash
pwsh -NoProfile -File scripts/backfill-supabase-realtime.ps1 -SourceHost PRIMARY-HOST -SourceDatabase vibegraph -SourceUser vibegraph -TargetHost aws-0-ap-southeast-1.pooler.supabase.com -TargetDatabase postgres -TargetUser postgres.YOUR-REF -WritersArePaused
```

The script refuses to run without `-WritersArePaused`, refuses a non-empty target unless you pass
`-Resume` with a matching manifest, and fails the cutover on any count, checksum, foreign-key or
publication mismatch. Read the manifest it writes: `verification.status` and
`realtimePublication.status` must both be `PASS`.

Unread `user_notifications` rows are intentionally not migrated — a missing row now means unread.

---

## 8. Enable Supabase

Set the backend environment (see the block below), start **one** instance, and run your smoke test:
list notifications, mark one read, open a feedback report, load the admin security page. Then roll
out the remaining instances and resume writers.

To roll back before new Supabase writes accumulate, set `VIBEGRAPH_SUPABASE_ENABLED=false` and
restart — the original tables were never modified. After Supabase has taken writes, a rollback needs
a reverse backfill first.

---

## Environment block

Copy into your deployment secret store. Passwords come from the store, never from this file.

```bash
VIBEGRAPH_SUPABASE_ENABLED=true
SUPABASE_DB_URL=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require
SUPABASE_DB_USER=vibegraph_runtime.YOUR-REF
SUPABASE_DB_PASSWORD=<from-secret-store>
SUPABASE_DB_SCHEMA=vibegraph_realtime
SUPABASE_REQUIRE_SEPARATE_CREDENTIALS=true
SUPABASE_MIGRATION_DB_URL=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require
SUPABASE_MIGRATION_DB_USER=postgres.YOUR-REF
SUPABASE_MIGRATION_DB_PASSWORD=<from-secret-store>
SUPABASE_TELEMETRY_FRESH_BATCHES_PER_CYCLE=1
```

`SUPABASE_REQUIRE_SEPARATE_CREDENTIALS=true` makes the migration credential mandatory and refuses to
start if migration and runtime are the same role on the same database. Leave it `false` only for
local development, where startup logs a warning instead.

The container termination grace period must stay **longer** than
`SUPABASE_TELEMETRY_SHUTDOWN_DRAIN_TIMEOUT_MS` (10s by default), otherwise the process is killed
mid-drain. `docker-compose.yml` sets `stop_grace_period: 30s`; mirror that in
`terminationGracePeriodSeconds` on Kubernetes.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Startup fails: `SUPABASE_MIGRATION_DB_URL ... is missing` | `require-separate-credentials=true` without a migration credential | set the three `SUPABASE_MIGRATION_DB_*` values |
| Startup fails: `same role on the same database` | migration and runtime are identical | use `vibegraph_runtime` for the runtime credential |
| Startup warns `migration and runtime share one credential` | `require-separate-credentials=false` | expected locally; set `true` in production |
| `request_events.dropped.total` climbing | arrival above the drain ceiling | raise `SUPABASE_TELEMETRY_FRESH_BATCHES_PER_CYCLE` per the sizing table |
| `relation "vibegraph_realtime.request_events" does not exist` during role verification | the Supabase schema migration has not run yet | run `scripts/migrate-supabase-schema.ps1`, then rerun provisioning with `-RequireTables` |
| Backfill refuses: `target schema ... already contains rows` | target is not empty | start from an empty target, or use `-Resume` with the matching manifest |
| Backfill fails: `raw telemetry tables are published` | `request_events`/`security_events` are in `supabase_realtime` | remove them from the publication in the dashboard |
| Notifications look empty after cutover | enabled before the backfill | disable, backfill, re-enable |
