# Current Evidence Manifest

This manifest records the evidence boundary used by the artifacts in this folder. It is
intentionally concise: volatile rolling hashes and worktree drift belong in `live/`, while
page-level old-to-current explanations belong in `changes/`.

## Repository identity

- Repository: `D:\Users\User\IdeaProjects\VibeGraph`
- HEAD at the verified audit: `d5154c4c368d7ca89fabb8da91a79858bea7af7b`
- Branch at the verified audit: `backup-full-fixed-20260728`
- GitNexus status: current at commit `d5154c4`; 1,173 files, 17,907 symbols, 41,198
  relationships/edges, and 300 execution flows.
- Worktree boundary: dirty and shared with concurrent sessions. No stage, commit, push, reset,
  revert, or deployment action is implied by this documentation baseline.

The current `git status` is deliberately not copied into this file because it changes while
other sessions work. `live/CURRENT-STATE.json` is the machine-readable rolling observation after
the watcher has completed a cycle.

## Evidence classes

- **SOURCE**: a checked source/configuration path supports the claim.
- **MIGRATION**: checked-in SQL or Cypher defines the schema claim.
- **RUNTIME**: a timestamped query against the local container supports the observation.
- **TEST**: a named build/test command completed successfully.
- **UNKNOWN**: evidence was not established, so the claim is excluded.

Runtime observations do not prove an external production environment. Source/configuration
support does not prove that an optional provider or integration is enabled.

## Required artifact inventory

The ten requested deliverables live directly under `Diagram/diagram update/`:

1. `1.Usecase Diagram`
2. `2.Activity Diagram`
3. `3.ERD Diagram`
4. `3.VibeGraph_ProjectReportDocument(Updated).docx`
5. `4.1.Component_Deployment Diagram`
6. `4.2.Class Diagram`
7. `plantuml_usecase.md`
8. `plantuml_activity.md`
9. `plantuml_erd_component_class.md`
10. `VibeGraph_All_PlantUML_Diagrams.md`

The diagrams.net page inventory is `10 / 6 / 2 / 1 / 2`. Those files preserve the old
page inventory for visual comparison, but they are not claimed to map one-to-one to the
canonical PlantUML section inventory. The combined PlantUML file is generated as an exact
copy of the three canonical Markdown sources between canonical-copy markers.

## Durable source and migration evidence

- Authentication separates registration, existing-user login, OAuth success handling, rotating
  refresh sessions, and cookie ownership between `AuthController`,
  `OAuth2LoginSuccessHandler`, `AuthService`, `RefreshSessionService`, and `AuthCookieService`.
- Archive import is synchronous with HTTP `200` by default. Archive `async=true`, GitHub import,
  and local import submit executor work before their controllers return HTTP `202`.
- Watcher replacement order is `getFileSlice(before) -> deleteFile -> optional parse/node/edge
  upsert -> getFileSlice(after) -> delta -> incremental broadcast`. Separate repository calls are
  not described as an all-or-nothing transaction.
- Local patch is documented as a CLI/JWT or project-bound API-key flow. A committed content
  change schedules coalesced full asynchronous reanalysis through `PatchAnalysisScheduler`.
- Graph consumers use `GraphRepository`; raw Neo4j Driver access is isolated to
  `Neo4jGraphRepository` and migration/configuration boundaries.

Representative paths are recorded in the four files under `changes/`; the canonical diagrams
also carry source/migration locators next to the relevant views.

## PostgreSQL runtime snapshot

Observed from the healthy local `vibegraph-postgres` container running
`postgres:16.11-alpine` at `2026-08-14T10:12:42+07:00` and revalidated by the watcher at
`2026-08-14T11:09:59+07:00` with the same schema counts:

- 21 domain tables; 22 public tables when `flyway_schema_history` is included.
- 23 physical foreign keys.
- 66 indexes on the 21 domain tables; 68 public-schema indexes including two Flyway metadata
  indexes.
- 19 successful Flyway migrations. Version 16 is intentionally absent and V20 removes the
  orphan `system_control_settings` table.
- Important current unique indexes include `uq_users_email_lower`,
  `uq_identity_provider_uid`, `uq_credit_balance_user_period`,
  `uq_user_notifications_user_announcement`, and partial
  `uq_api_keys_live_user_project`.

Volatile table row counts are intentionally excluded from the canonical ERD and this manifest.

## Neo4j runtime snapshot

Observed from the healthy local `vibegraph-neo4j` container running
`neo4j:5.26-community` at `2026-08-14T10:12:42+07:00` and revalidated by the watcher at
`2026-08-14T11:09:59+07:00` with the same runtime counts:

- 56,724 nodes and 116,987 relationships.
- `APIEndpoint=620` is emitted by current parser code; `Route=0` is a migration-defined,
  schema-only label in the observed runtime. Route constraints/indexes do not cover
  `APIEndpoint`.
- Current non-zero relationships represented in the ERD include `INSTANTIATES=3,154`,
  `CATCHES=90`, and `STEP_IN_FLOW=284`.
- `ANNOTATED_BY=1,712` is retained as persisted legacy runtime data and is not presented as a
  current parser emission.
- Current `DEFINES` emission is File to Class/Interface/Enum/Record/DBModel. Older endpoints in
  persisted data are documented as runtime drift, not promoted to current behavior.

Exact V1/V2 constraint and index names are listed in `plantuml_erd_component_class.md` and are
traceable to `src/main/resources/db/migration/V1__init_schema.cypher` and
`src/main/resources/db/migration/V2__symbol_label.cypher`.

## Recorded verification commands

Final local verification on `2026-08-14` completed after the reconciliation:

- Backend compile: `.\mvnw.cmd -q -DskipTests compile` passed.
- Targeted backend audit suite rerun at approximately `11:11`: 78 tests, 0 failures, 0 errors,
  0 skipped across nine named Surefire classes.
- Frontend type-check: `npm run type-check` passed.
- Frontend unit suite rerun at approximately `11:10-11:11`: `npm run test:unit -- --run` passed
  with 67 files and 570 tests. The
  run emitted non-failing Vue warnings; this manifest does not claim a warning-free run.
- DOCX package: 19 ZIP entries, clean CRC, seven-page Word COM/PDF render at `11:03`, and visual
  review of every page after the final metadata patch.
- diagrams.net XML: expected pages `10 / 6 / 2 / 1 / 2`, no duplicate cell IDs, invalid edge
  endpoints, orphan parents, or vertices outside their page roots.

These are timestamped audit results, not a substitute for CI. Re-run them after concurrent code
changes and before accepting a new watcher baseline.

## Reproduction and rolling evidence

```powershell
# Regenerate diagrams.net companions from the reviewed generator.
pwsh -NoProfile -File scripts/generate-updated-diagrams.ps1

# Rebuild the exact combined PlantUML mirror.
pwsh -NoProfile -File scripts/sync-diagram-plantuml.ps1

# One-shot evidence capture with backend compile and frontend type-check.
pwsh -NoProfile -File scripts/update-diagram-evidence.ps1 -RunFastChecks

# Accept only after semantic review and every gate passes.
pwsh -NoProfile -File scripts/update-diagram-evidence.ps1 -AcceptCurrentBaseline
```

`scripts/update-diagram-evidence.ps1` observes and records evidence; it does not rewrite or
semantically approve the canonical diagrams. After a successful watch cycle,
`live/WATCHER-STATUS.json`, `live/CURRENT-STATE.json`, and `live/LATEST-EVIDENCE.md` must identify
the same committed cycle. A cycle mismatch or `refresh-failed` status is not a valid current
snapshot.

## Explicit limits

No artifact claims a current GitLab import route, password-reset/email-verification workflow,
backend class/sequence diagram endpoint, 3D graph renderer, broad backend SVG export, or a
six-tool MCP surface. Optional PostgreSQL-compatible realtime/high-volume storage is disabled by
default and is not asserted to have been active during the local runtime snapshot.
