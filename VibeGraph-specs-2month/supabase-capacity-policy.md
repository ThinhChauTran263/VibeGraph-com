# Supabase Capacity Policy

> **Status: LAUNCH-PHASE DEFAULTS ACCEPTED — see Section 0.** This project has no production
> traffic history yet, so the project owner accepted the shipped defaults below as an interim
> decision rather than leaving them blank. This is a real, dated decision — not an unfilled `TBD` —
> and it comes with a trigger for when it must be revisited (Section 0). Once real traffic exists,
> replace Section 0 with measured numbers in Sections 1–3.

## 0. Launch-phase decision (interim, no production traffic yet)

**Decided by:** project owner, 2026-08-09 — "dự án tôi mới nên không biết rõ mấy cái này" (new
project, these numbers aren't known yet).

**Decision:** ship with the built-in defaults rather than block launch on numbers that do not exist
yet.

| Setting | Interim value | Why it's safe to launch with |
|---|---|---|
| `SUPABASE_TELEMETRY_FRESH_BATCHES_PER_CYCLE` | **1** (default) | Drains ~125 events/second/instance — see the sizing table in Section 3. A new product has no traffic history that could exceed this; the local benchmark shows the pipeline itself can do ~12,700 events/s, so this is a configuration knob, not a hard ceiling. |
| `request_events` retention | **14 days** (default) | Raw request telemetry, not business data. Cheap to shorten later. |
| `security_events` retention | **180 days** (default) | Security telemetry; longer retention favors incident investigation over storage cost at this scale. |
| Storage budget | **Supabase free tier (500 MB)** | No paid tier has been chosen yet; free tier is enough for low early traffic. |
| Rate limiting scope | **per-instance** (default, unimplemented otherwise) | Fine while running a single backend instance. Revisit before running multiple replicas behind a load balancer. |
| `security_events` durability | **best-effort** (default, unimplemented otherwise) | No durable outbox is implemented. Acceptable for launch; revisit if security telemetry becomes an audit requirement. |
| Sampling | **none** (default) | No traffic volume to justify sampling yet. |

**Revisit this decision when any of these happens** (the "scale-up trigger"):
- Real production traffic exists and peak requests/second can be measured (see Section 1).
- `request_events.dropped.total` starts climbing in monitoring — the interim ceiling is too low for
  actual traffic.
- The Supabase project approaches its plan's storage limit.
- The backend runs more than one instance behind a load balancer (per-instance rate limiting no
  longer means what the name implies).
- `security_events` needs to satisfy a compliance or audit requirement.

When any trigger fires, fill in Sections 1–3 with real numbers and use the sizing table in
Section 3 to pick a new `SUPABASE_TELEMETRY_FRESH_BATCHES_PER_CYCLE`. This is a configuration
change only — no code changes are needed to raise the ceiling.

## 1. Traffic

| Item | Value | Source |
|------|-------|--------|
| Expected average requests/second | not yet measured — new project | production traffic data, once it exists |
| Expected peak requests/second | not yet measured — new project | production traffic data, once it exists |
| Peak duration (how long a peak is sustained) | not yet measured — new project | production traffic data, once it exists |
| Replica count at peak | 1 (interim — see Section 0) | deployment plan |

## 2. Storage

| Item | Value | Source |
|------|-------|--------|
| Storage budget for `vibegraph_realtime` | Supabase free tier, ~500 MB (interim — see Section 0) | Supabase plan / cost owner |
| Maximum raw `request_events` rows | not yet derived — no bytes/event measurement on real data | derived from budget and bytes/event once traffic exists |
| Raw `request_events` retention | 14 days (interim default — see Section 0) | policy decision |
| `security_events` retention | 180 days (interim default — see Section 0) | policy decision |
| Supabase plan assumptions (tier, included storage, IOPS) | Free tier (interim — see Section 0) | cost owner |

Measure bytes per event on **representative** data — a table full of one synthetic route is not
representative, because index and TOAST behaviour depend on real route and IP cardinality:

```sql
SELECT
    c.reltuples::bigint                                                    AS estimated_rows,
    (SELECT count(*) FROM vibegraph_realtime.request_events)               AS exact_rows,
    pg_total_relation_size('vibegraph_realtime.request_events'::regclass)  AS total_relation_bytes,
    pg_indexes_size('vibegraph_realtime.request_events'::regclass)         AS index_bytes,
    CASE
        WHEN (SELECT count(*) FROM vibegraph_realtime.request_events) = 0 THEN NULL
        ELSE pg_total_relation_size('vibegraph_realtime.request_events'::regclass)::numeric
             / (SELECT count(*) FROM vibegraph_realtime.request_events)
    END                                                                    AS bytes_per_event
FROM pg_class c
WHERE c.oid = 'vibegraph_realtime.request_events'::regclass;
```

`pg_total_relation_size` covers the table, its indexes and its TOAST relation. The `CASE` guard
avoids dividing by zero on an empty table. Record `exact_rows`, `total_relation_bytes`,
`index_bytes` and `bytes_per_event` together — a bytes/event figure without the row count it came
from cannot be compared to anything.

## 3. Pipeline capacity

| Item | Value | Source |
|------|-------|--------|
| Target queue utilization at peak | not yet set — no measured peak | policy decision, once traffic exists |
| Acceptable telemetry drop rate | not yet set — no measured peak | policy decision, once traffic exists |
| Required drain safety margin | 2x peak arrival (recommended default) | policy decision |
| `SUPABASE_TELEMETRY_FRESH_BATCHES_PER_CYCLE` for production | **1** (interim default — see Section 0) | derived from peak arrival, once measured |

The pipeline drains at most `batchSize x freshBatchesPerCycle` events per flush cycle. With the
shipped defaults (`batchSize=250`, `freshBatchesPerCycle=1`, `flushIntervalMs=2000`) that caps drain
throughput at roughly **125 events/second per instance**. Raise `freshBatchesPerCycle` once a peak
arrival rate exists to size against; it stays bounded so one cycle cannot hold the flush guard
indefinitely.

The configured ceiling is logged at startup and exposed as the gauge
`request_events.drain.ceiling_per_second`, so it never has to be inferred from a drop counter:

```
Telemetry drain ceiling is ~125 events/second per instance
(batch-size 250 x fresh-batches-per-cycle 1 every 2000ms).
```

### Sizing table — pick the row for your peak, no arithmetic needed

Assumes the shipped `batchSize=250` and `flushIntervalMs=2000`, one instance, and the recommended
2x safety margin. **Peak** means requests/second hitting this instance, not the whole cluster: with
N instances behind a load balancer, divide your cluster peak by N first.

| Peak req/s (per instance) | Required drain (2x) | `SUPABASE_TELEMETRY_FRESH_BATCHES_PER_CYCLE` | Resulting ceiling |
|---|---|---|---|
| up to 60 | 120/s | **1** (default) | 125/s |
| up to 125 | 250/s | **2** | 250/s |
| up to 250 | 500/s | **4** | 500/s |
| up to 500 | 1 000/s | **8** | 1 000/s |
| up to 1 000 | 2 000/s | **16** | 2 000/s |
| up to 2 000 | 4 000/s | **32** | 4 000/s |

Formula if your `batchSize` or `flushIntervalMs` differ:

```
freshBatchesPerCycle = ceil( 2 x peakRequestsPerSecond x (flushIntervalMs / 1000) / batchSize )
```

The local benchmark measured a drain ceiling of ~12 700 events/s, so every row above is far below
what the pipeline itself can do — the limit is configuration, not capability. Re-measure on
representative infrastructure before committing to the top rows.

### Local benchmark reference (NOT a production conclusion)

Produced by `mvn -Pbenchmark test` on a developer machine against a container-local PostgreSQL, with
no network hop and no managed-instance limits. Re-run it on representative infrastructure before
using any of it for planning.

| Measurement | Value | Context |
|-------------|-------|---------|
| Observed drain ceiling | ~12,700 events/s | Windows 11, 16 vCPU, `postgres:16-alpine`, `freshBatchesPerCycle=40`, `flushIntervalMs=200` |
| Paced arrival at half the ceiling | ~6,360 events/s | 0 dropped events, peak fresh-queue utilization 0.4% |
| Bytes per event | ~198 bytes | includes indexes and TOAST; single synthetic route, so **not** representative cardinality |
| Behaviour under overload | 40,000 of 50,000 events dropped at ~275,000 events/s arrival | queue sheds oldest and increments `request_events.dropped.total` |

Re-run:

```bash
mvn -o -Pbenchmark test
```

Results are written to `target/benchmarks/telemetry-pipeline.json` and
`target/benchmarks/rate-limit-cardinality.json`. Benchmarks never run during a normal `mvn test`.

## 4. Semantics to confirm

| Question | Decision | Notes |
|----------|----------|-------|
| Are `security_events` best-effort or durable/audit-grade? | **best-effort** (interim — see Section 0) | Durable would need an outbox or a synchronous write path; neither is implemented. Revisit if audit/compliance becomes a requirement. |
| Is rate limiting per-instance or cluster-wide? | **per-instance** (interim — see Section 0) | N replicas allow up to N times the configured rate. A shared limiter is not implemented. Revisit before running more than one instance. |
| Is telemetry sampling enabled, and at what percentage? | **no sampling** (interim — see Section 0) | Revisit once a storage budget and a real peak arrival rate exist. |
| Are minute rollups required? | **no rollups** (interim — see Section 0) | Rollup, HLL and partitioning are all out of scope and unimplemented. |
| Is a delta pass or dual-write needed for cutover? | **no** — single maintenance-window backfill (interim — see Section 0) | A single `pg_dump` does not close the write gap; only the maintenance window does. A new project has no concurrent-write cutover risk yet. |

## 5. Cutover gate

This gate covers the initial launch onto Supabase with the Section 0 interim defaults. Do not
enable `VIBEGRAPH_SUPABASE_ENABLED=true` until all of the following hold:

- [x] Section 0 launch-phase defaults reviewed and accepted by the project owner (2026-08-09).
- [x] `SUPABASE_REQUIRE_SEPARATE_CREDENTIALS=true`, with a runtime role provisioned from
      `scripts/supabase-runtime-role.sql` and verified to have no DDL rights.
      *Verified 2026-08-09: all 9 verification rows returned `True` against the live project.*
- [x] Supabase Flyway schema applied (`vibegraph_realtime` at version 1) via
      `scripts/migrate-supabase-schema.ps1`, without starting the application.
- [x] `pg_publication_tables` gate returned zero rows — `request_events` and `security_events` are
      not published to `supabase_realtime`.
- [x] **Backfill intentionally skipped** — see "Backfill decision" below.
- [ ] The container termination grace period is longer than
      `SUPABASE_TELEMETRY_SHUTDOWN_DRAIN_TIMEOUT_MS`.
- [ ] Retention values in configuration match Section 0 (14 days / 180 days, or an updated choice).
- [ ] Application smoke test passed with `VIBEGRAPH_SUPABASE_ENABLED=true`.

### Backfill decision (2026-08-09): skipped, starting clean

The six source tables held only local development data, so nothing of business value would have been
migrated:

| Table | Rows | Assessment |
|---|---|---|
| `request_events` | 139,341 | Frontend polling noise — top routes were `/api/account/session-state` (37.9k), `/api/account/profile` (37.3k), `/api/account/usage` (37.3k), `/actuator/health` (5.7k). **82% (113,724 rows) were already past the 14-day retention window** and would have been deleted on the first nightly run. |
| `security_events` | 25 | Development telemetry. |
| `feedback_reports` / `feedback_messages` | 1 / 6 | A single test report titled `"ad"`. |
| `announcements` / `user_notifications` | 0 / 0 | Empty. |

Importing ~140k rows through `pg_dump --column-inserts` in one transaction over a remote session
pooler would have cost time and roughly 28 MB of the 500 MB free tier for data that is discarded
within a day. The source tables were **not** modified and remain available: if this data is ever
needed, run the backfill against the (now non-empty) target with `-Resume` and a matching manifest.

The full backfill path is still verified — `scripts/tests/backfill-rehearsal.ps1` exercises the real
generated SQL end-to-end against a live PostgreSQL pair (14 checks). Skipping it here is a data
decision, not an untested code path.

Until then the application keeps running on the primary database with
`VIBEGRAPH_SUPABASE_ENABLED=false`, which is the shipped default.

**Separately**, before scaling beyond a single instance or beyond early traffic, revisit this
document: replace Section 0 with measured numbers in Sections 1–3, re-run the benchmark
(`mvn -o -Pbenchmark test`) against representative infrastructure, and confirm the drain safety
margin still holds at the real peak arrival rate.
