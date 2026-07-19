# ClaudeChat Handoff — Phase 7 BE-2 Feature Flags / System Controls

## Status

Feature-flag enforcement was implemented on the current `poc` worktree. No commit, push, or merge was performed. Frontend files were not changed.

## Canonical feature keys

| Operation | Canonical key | Enforcement point |
| --- | --- | --- |
| Registration | `registration` | `AuthService.register` before lookup/hash/save |
| API-key creation (user/admin) | `api_keys.create.global` | `ApiKeyService.createForCurrentUser` and `createForUser` before account/plan/hash/save |
| CLI push/local patch | `cli.push` | `LocalPatchServiceImpl.applyPatch` before root resolution, validation, quota, pricing, debit, or filesystem mutation; disabled dry-run is rejected too |
| Local import | `import.local` | `LocalImportServiceImpl.importLocal` before current-user lookup, path validation, directory measurement, quota, persistence, executor, parser, or watcher |
| Archive import | `import.archive` | `ArchiveImportServiceImpl.importArchive` and `importArchiveAsync` before validation/lease/workspace; shared preparation no longer duplicates the gate |
| GitHub import | `import.github` | `TarballImportServiceImpl.importFromGithub` before URL parsing, preflight, network, workspace, extraction, or persistence |
| Project analyze | `project.analyze` | `ProjectController.analyze` immediately after ownership and before account/credit/project/parser/Neo4j work |
| MCP global | `mcp.enabled` | `FeatureGateService.assertMcpToolEnabled`, first lookup |
| MCP child tools | `mcp.tool.<normalizedToolName>` | Checked only after `mcp.enabled`; blocks before ownership, pricing, credit, and delegate |
| Use-case generation | `usecase.generate` | `DiagramController.getUseCaseDiagram` after ownership and before project status/graph generation |

Missing flag rows remain enabled. Explicit disabled rows throw `FeatureDisabledException`.

## Legacy migration

`src/main/resources/db/migration/V11__canonical_feature_flag_keys.sql` maps:

- `global.registration` -> `registration`
- `global.api_keys` -> `api_keys.create.global`
- `global.cli_push` -> `cli.push`
- `global.import_archive` -> `import.archive`
- `global.import_github` -> `import.github`
- `global.mcp` -> `mcp.enabled`

When both legacy and canonical rows exist, the canonical row is disabled if either row is disabled, then the legacy duplicate is deleted. Legacy-only rows are renamed. Missing canonical rows are not seeded, preserving missing-as-enabled behavior.

## Admin CRUD and authorization

Existing `AdminFeatureFlagController` CRUD remains at `/api/admin/feature-flags`. Existing `SecurityConfig` route rule protects `/api/admin/**` with `ROLE_ADMIN`. `AdminFeatureFlagService` now validates canonical global keys with `GLOBAL` scope and normalized `mcp.tool.*` keys with `MCP_TOOL` scope, rejecting legacy/unknown/scope-mismatched keys before persistence or audit. Delete remains supported and restores missing-as-enabled behavior.

## Disabled response contract

Existing global handling remains:

- HTTP `403 Forbidden`
- `success: false`
- `error.code: FEATURE_DISABLED`
- `error.message: Feature is currently disabled`
- no feature-key, DB, path, or secret details

The exception retains the canonical key for server-side context, while the client response is generic and deterministic.

## Ordering / behavior matrix

Every disabled operation is rejected before its expensive or metered phase:

- registration: before duplicate lookup, password hashing, user/settings writes, JWT issue;
- API keys: before account/plan lookup, secret generation/hash, repository save;
- local import: before filesystem validation/walk and project/ownership/usage writes;
- archive import: before upload/workspace/extraction/project/analysis;
- GitHub import: before URL/preflight/download/extract/project;
- project analyze: after ownership only, before credit pricing/balance access, project lookup, parser, graph writes, stats, and debit;
- CLI push: before project-root resolution, content decode/read, quota, credit pricing/debit, and atomic patch; dry-run is disabled consistently;
- MCP: account-access check remains first, then global MCP, child flag, ownership, credit pricing/debit, delegate;
- use-case generation: after ownership only, before project lookup and graph/inference/render work.

Enabled behavior, ownership, credit pricing, and debit formulas were preserved.

## Files changed in this scope

- `src/main/java/com/vibegraph/auth/service/FeatureGateService.java`
- `src/main/java/com/vibegraph/auth/service/AdminFeatureFlagService.java`
- `src/main/java/com/vibegraph/auth/service/AuthService.java`
- `src/main/java/com/vibegraph/auth/service/ApiKeyService.java`
- `src/main/java/com/vibegraph/graph/service/impl/LocalImportServiceImpl.java`
- `src/main/java/com/vibegraph/graph/service/impl/ArchiveImportServiceImpl.java`
- `src/main/java/com/vibegraph/graph/service/impl/TarballImportServiceImpl.java`
- `src/main/java/com/vibegraph/graph/controller/ProjectController.java`
- `src/main/java/com/vibegraph/diagram/controller/DiagramController.java`
- `src/main/java/com/vibegraph/patch/service/impl/LocalPatchServiceImpl.java`
- `src/main/resources/db/migration/V11__canonical_feature_flag_keys.sql`
- focused unit/controller/security tests under `src/test/java/...`

## Tests run

Passing required command:

```bash
./mvnw "-Dtest=*FeatureFlag*,*Import*,*Analyze*,*ApiKey*,*Mcp*" test
```

Result: `Tests run: 158, Failures: 0, Errors: 0, Skipped: 8`; `BUILD SUCCESS`.

After hardening the client message, the required patterns plus `ExceptionsTest` were rerun:

```bash
./mvnw "-Dtest=ExceptionsTest,*FeatureFlag*,*Import*,*Analyze*,*ApiKey*,*Mcp*" test
```

Result: `Tests run: 169, Failures: 0, Errors: 0, Skipped: 8`; `BUILD SUCCESS`.

Passing supplementary gate suite:

```bash
./mvnw "-Dtest=LocalPatchQuotaTest,LocalPatchServiceImplTest,ProjectControllerTest,DiagramControllerTest,FeatureGateServiceTest,AdminFeatureFlagServiceTest" test
```

Result: `Tests run: 63, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS`.

Attempted full verification / AdminSecurityIT. It is currently blocked by unrelated auth-worker changes in the shared worktree: `JwtAuthFilterTest` reports 12 failures and 2 errors, and `AdminSecurityIT` context startup reports a pre-existing/parallel `SecurityConfig` ↔ `ApiKeyAuthFilter` circular dependency. These failures are outside BE-2 and were not reverted.

`git diff --check` reports only existing LF/CRLF normalization warnings; no whitespace errors.

## Frontend contract notes

No frontend files changed. Clients should treat disabled operations as HTTP 403, inspect `error.code === "FEATURE_DISABLED"`, and display the safe `error.message`. The canonical key is stable for admin/runtime diagnostics; no stack traces or infrastructure details are returned.

## GitNexus

Index was refreshed with `npx gitnexus analyze` before impact analysis. FeatureGateService impact was HIGH (15 direct dependents); LocalPatchServiceImpl impact was HIGH (28 direct dependents); FeatureDisabledException impact was CRITICAL (91 dependents), so the exception class/handler was intentionally not structurally changed. `gitnexus_detect_changes({scope: "all"})` was run, but the shared worktree contains broad concurrent Phase 7 changes (71 modified files / 38 untracked files; risk critical), so its aggregate result cannot isolate this worker’s diff. No commit was made.
