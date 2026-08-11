# Supabase Realtime Storage

## Goal

Move high-growth and realtime-oriented data away from the primary PostgreSQL control plane while
keeping users, project ownership, plans, credits, audit data, and Neo4j unchanged.

## Scope

- Move `feedback_reports` and `feedback_messages`.
- Move `request_events` and `security_events`.
- Move `announcements` and `user_notifications`.
- Add `project_runtime_status` for the latest analysis progress per project.
- Keep the existing REST, JWT, and STOMP contracts.

## Architecture

Spring Boot keeps the primary JPA datasource. A separate JDBC pool and Flyway instance manage the
private Supabase schema `vibegraph_realtime`. When Supabase is disabled, the moved repositories use
the primary datasource so local development and existing integration tests remain self-contained.

Supabase rows store user and project identifiers as soft references. Authorization continues to use
the primary database. The backend is the only writer; no database or service-role credential is
exposed to the frontend.

## Growth Controls

- Request telemetry is buffered and batch-inserted.
- Raw request events and security events have scheduled retention.
- Closed feedback threads keep the existing seven-day cleanup.
- Notifications store only per-user read/dismiss state instead of materializing every announcement.
- Project runtime status uses one upserted row per project.

## Telemetry Durability

Request and security events are best-effort telemetry, not an audit trail:

- Events are buffered in memory. A crash loses whatever has not been written.
- A full queue sheds the oldest event and counts it in `request_events.dropped.total`.
- A batch that exhausts its retry budget is abandoned and counted in
  `request_events.batch.abandoned`; a single event that keeps failing is counted in
  `request_events.poison.total`.
- Retries are idempotent: a batch keeps its identity and its event ids, and both raw inserts use
  `ON CONFLICT (id) DO NOTHING`, so replaying a partially applied batch neither duplicates rows nor
  fails on a duplicate key.

Do not describe this pipeline as zero-loss, exactly-once or audit-grade. Promoting
`security_events` to durable storage is a separate decision recorded in the capacity policy.

Rate limiting is per instance. Every replica keeps its own bounded window cache, so N replicas
allow up to N times the configured rate, and under cache capacity pressure enforcement degrades to
best-effort for evicted keys (`rate_limit.capacity.pressure`).

## Shutdown

`RequestEventService` stops accepting events and then drains both queues until they are empty or
`SUPABASE_TELEMETRY_SHUTDOWN_DRAIN_TIMEOUT_MS` elapses. The container termination grace period must
be **longer** than that budget, otherwise the process is killed mid-drain. `docker-compose.yml` sets
`stop_grace_period: 30s` against a 10s default drain budget; keep that relationship when changing
either value, including in Kubernetes `terminationGracePeriodSeconds`.

## Cutover

`scripts/backfill-supabase-realtime.ps1` only moves data. It never stops or starts the application:
pausing writers belongs to deployment orchestration, and the script fails closed unless the operator
passes `-WritersArePaused`.

The maintenance window must stay open for the whole sequence, not just the dump:

```
pause every writer
  -> dump
  -> import
  -> semantic verification
  -> Realtime publication verification
  -> enable Supabase
  -> application smoke test
  -> resume writers
```

1. Provision Supabase and run the dedicated Flyway migration with the migration credential.
2. Pause HTTP writers, scheduled jobs and every backend instance.
3. Run the backfill. Supply credentials through `-SourceCredential` / `-TargetCredential`, or
   through `VIBEGRAPH_BACKFILL_SOURCE_PASSWORD` / `VIBEGRAPH_BACKFILL_TARGET_PASSWORD` from your
   secret store. A password is never accepted inside a connection URL and never passed as a process
   argument; the script writes a temporary `PGPASSFILE` with an owner-only ACL and deletes it in a
   `finally` block on success and on failure.
4. Read the manifest. Verification must be `PASS` for counts and canonical SHA-256 checksums on all
   six tables, and the Realtime publication gate must be `PASS`.
5. Enable Supabase in one backend instance and run the application smoke test.
6. Roll out the remaining instances, then resume writers.
7. Keep the original tables; remove them only in a later forward migration. This implementation
   does not dual-write, so a rollback after new Supabase writes requires a reverse backfill first.

### Snapshot semantics

A single `pg_dump` invocation exports all six tables, which gives one internally consistent snapshot
**across** the tables. It does **not** close the write gap: anything written to the source after the
dump begins is simply absent. Only the maintenance window — or a delta pass, or dual-write — prevents
lost rows. The manifest records `dumpStartedAt` and `dumpCompletedAt`, not an exported transaction
snapshot; the script does not use `pg_export_snapshot`.

### Preflight and resume

The target must be empty. A non-empty target is refused unless `-Resume` is supplied together with a
`-ManifestPath` whose manifest version, transformation version, source identity, target identity and
table set all match. Conflict-tolerant import is never applied to a target whose state is unknown.
The preflight also refuses to run if the target's `public` schema already holds a table named like
one of the six, because the import stages rows there.

### Import

The import runs as one `psql --single-transaction` with `ON_ERROR_STOP=1`. Dumped rows land in
unlogged staging tables in the target's `public` schema and are then inserted into
`vibegraph_realtime` in dependency order with `ON CONFLICT (id) DO NOTHING`; the staging tables are
dropped in the same transaction. Dumped row text is never rewritten, so a feedback body that happens
to contain SQL cannot corrupt the import. Any failure rolls the whole thing back. The source tables
are only ever read.

### Verification

`feedback_reports`, `feedback_messages`, `announcements`, `request_events` and `security_events` must
match on row count and on a canonical SHA-256 checksum. The checksum is computed from a
`COPY (SELECT ... ORDER BY id) TO STDOUT WITH (FORMAT csv)` stream that psql writes straight to disk,
so a large `request_events` table is never aggregated in memory. Timestamps are normalized to UTC
with fixed microsecond precision, and nulls use one documented sentinel, identically on both sides.

`user_notifications` is transformed: unread rows (`read_at IS NULL AND dismissed_at IS NULL`) are not
migrated, because the new model treats a missing row as unread. Verification applies the **same**
exclusion to the source, so counts and checksums remain directly comparable. Foreign-key coverage is
checked too: every `feedback_messages.report_id` must resolve to a `feedback_reports` row, and every
`user_notifications.announcement_id` to an `announcements` row. Any mismatch fails the cutover.

### Realtime publication gate

The script queries `pg_publication_tables` on the live target and fails the cutover if
`request_events` or `security_events` are published to `supabase_realtime`. Migration SQL is not
accepted as evidence: a publication can be changed from the Supabase dashboard afterwards.

### Rehearsal

Two layers, both runnable locally:

- `scripts/tests/backfill-supabase-realtime.Tests.ps1` (17 checks, no database) covers the
  maintenance gate, argument construction, credential handling, canonical SQL generation, manifest
  compatibility and temporary-file cleanup.
- `scripts/tests/backfill-rehearsal.ps1` (14 checks, Docker only) executes the SQL the script
  generates against a real PostgreSQL instance with a **source database and a separate target
  database**, mirroring a real cutover: one `pg_dump` for six tables, the import transaction and its
  staging tables, canonical checksums on both sides, the unread-notification transformation,
  foreign-key coverage, an idempotent second import, a rolled-back failing import, and the live
  `pg_publication_tables` gate in both its passing and failing state.

Neither layer touches a Supabase project. What remains for a staging rehearsal with the real script
is the host-side leg: `PGPASSFILE` creation and ACLs, process argument construction, network latency
and managed-instance limits.

The application stays on the primary database while `VIBEGRAPH_SUPABASE_ENABLED=false`. After
backfill verification, fill the runtime credentials and enable the flag. The original tables are
intentionally neither dropped nor rewritten.
