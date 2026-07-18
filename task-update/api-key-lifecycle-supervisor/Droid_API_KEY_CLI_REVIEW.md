# Droid API Key Lifecycle + CLI Integration Review

## Verdict

**REQUEST CHANGES**

The current dirty worktree is not merge-ready. The lifecycle implementation covers most of the requested contract, but two backend security/lifecycle gaps remain and the required CLI test gate is red. This review was performed against the live source and diff, not only the handoff documents. No implementation code was changed, committed, pushed, merged, or reverted.

## Critical

None found.

## High

### H1. JWT authentication can bypass the API-key project identity boundary

- **Location:** `src/main/java/com/vibegraph/auth/web/ApiKeyAuthFilter.java:62`, `src/main/java/com/vibegraph/auth/config/SecurityConfig.java:112`, `src/main/java/com/vibegraph/auth/web/ApiKeyRequestContextAccessor.java:24`
- **Impact:** `JwtAuthFilter` runs first, and `ApiKeyAuthFilter` skips validation whenever the security context is already authenticated. A JWT-only MCP/explicit patch request has no API-key project context, while a request containing a valid JWT plus an invalid, disabled, expired, deleted, locked, or mismatched API key ignores the API key. This conflicts with API key as the CLI/MCP project identity and makes ambiguous dual-credential requests fail open to JWT.
- **Fix:** Always validate a supplied `X-API-Key`; reject ambiguous or invalid mixed credentials. Require API-key context/authority for MCP and project-ID-free patch routes, and explicitly document any legacy JWT-only patch route that remains supported. Make project matching fail closed where API-key identity is required.
- **Owner:** Kiro (backend lifecycle/security integration)

### H2. Deleting a project physically deletes API-key history, including admin locks

- **Location:** `src/main/resources/db/migration/V12__project_bound_api_keys.sql:10`, `src/main/java/com/vibegraph/common/ownership/ProjectDeletionOrchestrator.java:60`
- **Impact:** The project foreign key uses `ON DELETE CASCADE`. Deleting the project ownership row hard-deletes associated API keys, bypassing the rule that users cannot delete admin-locked keys and destroying lifecycle/audit history.
- **Fix:** Replace the cascade with `RESTRICT` or an explicit lifecycle transaction. Preserve key history and reject project deletion while an admin lock exists, or define and audit an admin-only resolution transition before project deletion.
- **Owner:** Kiro (backend lifecycle)

### H3. Required CLI test gate fails

- **Location:** `vibegraph-cli/test/shell.test.js:100`
- **Impact:** `npm --prefix vibegraph-cli test` reports 44 passed, 1 failed, 1 skipped. The assertion expects unstyled contiguous text, but inherited color output inserts ANSI sequences, so the package is not currently merge-ready.
- **Fix:** Small test-only unblock: run this assertion with color disabled or strip ANSI before matching. Do not weaken production masking/auth assertions.
- **Owner:** CodexCli
## Medium

### M1. Admin disable and lock are duplicate operations, with no resolution path

- **Location:** `src/main/java/com/vibegraph/auth/web/AdminApiKeyController.java:32`, `src/main/java/com/vibegraph/auth/web/AdminApiKeyController.java:38`, `src/main/java/com/vibegraph/auth/service/ApiKeyService.java:95`, `vibegraph-web/src/views/user/ApiKeysView.vue:32`
- **Impact:** Both admin endpoints call the same permanent `disableForAnyUser` transition. The UI tells users an administrator must unlock the key, but no unlock/resolution endpoint or UI exists. A locked project can therefore remain permanently unable to receive a replacement key.
- **Fix:** Define one coherent contract: either treat admin disable as lock and remove the duplicate UI/endpoint/copy, or add a distinct audited lock/unlock lifecycle with backend and UI support.
- **Owner:** Kiro for backend contract; opencode for UI alignment

### M2. Frontend metadata contract drifts from `ApiKeyResponse`

- **Location:** `vibegraph-web/src/types/api.ts:184`, `vibegraph-web/src/views/admin/UserDetailDrawer.vue:130`, `src/main/java/com/vibegraph/auth/dto/ApiKeyResponse.java:19`
- **Impact:** Frontend expects `lockedAt` and `lockedBy`, which backend does not return, while backend returns `disabledReason` and authoritative `canDelete`, which frontend does not model. “Locked by” can never render and delete eligibility is re-derived client-side.
- **Fix:** Align TypeScript with the Java DTO and use `canDelete` as the authoritative gate. Add lock actor/timestamp to backend only if product intentionally exposes that safe metadata; otherwise remove those frontend dependencies.
- **Owner:** opencode

### M3. Authentication candidate BCrypt work has no hard bound

- **Location:** `src/main/java/com/vibegraph/auth/repository/ApiKeyRepository.java:18`, `src/main/java/com/vibegraph/auth/web/ApiKeyAuthFilter.java:80`
- **Impact:** Prefix lookup avoids `findAll()`, which is good, but returns an unrestricted list and BCrypt-checks every collision. Natural collisions are unlikely, yet corrupted or adversarial rows can amplify authentication cost.
- **Fix:** Enforce unique lookup material or a strict candidate limit and fail closed on ambiguity.
- **Owner:** Kiro

### M4. Security-critical rejection paths lack direct regression tests

- **Location:** `src/test/java/com/vibegraph/auth/web/ApiKeyAuthFilterTest.java:34`, `src/test/java/com/vibegraph/auth/service/ApiKeyLifecycleServiceTest.java:127`
- **Impact:** Filter tests do not directly prove rejection of disabled, deleted, expired, admin-locked, wrong-secret, deactivated, or mixed JWT/API-key requests. `create_afterDelete_succeeds` does not actually execute a delete/state transition. These rules can regress while the focused gate remains green.
- **Fix:** Add parameterized auth rejection tests, mixed-credential security-chain tests, and a persistence-backed delete-then-recreate lifecycle test.
- **Owner:** Kiro

### M5. Repository selector only loads the first 100 projects

- **Location:** `vibegraph-web/src/stores/account.ts:206`
- **Impact:** Users with more than 100 repositories cannot bind a key to projects outside the first page.
- **Fix:** Load all pages or implement a paginated/searchable repository selector.
- **Owner:** opencode

## Low

### L1. Snapshot files do not request restrictive local permissions

- **Location:** `vibegraph-cli/lib/snapshot.js:49`
- **Impact:** Snapshots contain project identifiers, relative paths, hashes, and timestamps and are written with platform defaults. They do not contain the raw API key; on Windows this is acceptable MVP risk because POSIX mode bits are not reliably enforced, but POSIX hosts may expose metadata to other local users.
- **Fix:** Create snapshot directories owner-only where supported and write/chmod snapshot files to `0600`; add POSIX-gated tests.
- **Owner:** CodexCli

## Verified Requirements

- Forbidden admin-create surfaces are absent from production source: no `AdminApiKeyCreateRequest`, `ApiKeyService.createForUser`, frontend `createApiKeyForUser`, or admin POST endpoint.
- User create requires an owned project; duplicate non-deleted keys are blocked, including user-disabled and admin-disabled keys.
- Database partial unique index protects one non-deleted key per `(user_id, project_id)` and the PostgreSQL concurrency test proves one concurrent insert survives.
- User delete endpoint exists and uses owner plus admin-lock guards.
- Raw secret is generated only for create response, BCrypt-hashed before persistence, absent from list DTO, and create response uses `Cache-Control: no-store`.
- Auth lookup uses indexed prefix candidates rather than `findAll()` and excludes disabled/deleted rows; expiry and project ownership are checked before authentication.
- `/api/projects/current/patch` resolves the project from API-key request context; explicit API-key patch and MCP project calls compare project binding when context exists.
- User UI requires repository selection, clearly blocks duplicate projects, disables delete for locked keys, and uses custom confirmation dialogs rather than browser `alert`/`confirm`.
- Admin UI has no create-key flow and calls existing list/disable/lock endpoints.
- JWT is not stored in localStorage; only non-sensitive user/UI metadata is persisted there, while legacy token keys are removed.
- CLI implements `auth set-key`, `auth clear`, `auth status`, `VIBEGRAPH_API_KEY`, masked `config show`/status/doctor output, and updated shell suggestions.
- Push/watch use `X-API-Key` and omit Bearer when an API key is available; root-only mode uses `/api/projects/current/patch`.
- Snapshot identity is a SHA-256 fingerprint, not the raw key. No production path that prints the full key was found.
- CLI docs describe API-key-first push/watch and mark login/register as legacy compatibility.

## Gate Results

- `npm --prefix vibegraph-cli test`: **FAIL** — 44 passed, 1 failed, 1 skipped; ANSI-sensitive assertion at `vibegraph-cli/test/shell.test.js:100`.
- `npm --prefix vibegraph-web run type-check`: **PASS**.
- `npm --prefix vibegraph-web run test:unit -- --run`: **PASS** — 56 files, 457 tests.
- `npm --prefix vibegraph-web run build`: **PASS** — build completed; chunk-size warning only.
- `.\mvnw.cmd "-Dtest=*ApiKey*,*LocalPatch*,*Mcp*,*Feature*,*Session*,*Account*" test`: **PASS** — exit code 0.
- `git diff --check`: **PASS** — line-ending conversion warnings only.
- GitNexus `detect_changes(scope=all)`: **COMPLETED** — aggregate dirty worktree risk `CRITICAL`, 346 changed symbols, 42 affected symbols/flows, 75 changed files. This includes unrelated parallel-agent work and is not an API-key-only risk rating.

## Handoff Notes

- No code fix was applied. The only file created by Droid is this review document.
- Small unblock permitted for CodexCli: make the ANSI-sensitive shell test deterministic, then rerun the full CLI gate.
- Do not commit/push/merge until H1, H2, and the red CLI gate are resolved. Preserve all unrelated dirty-worktree changes from parallel agents.

## Re-Review Status (2026-07-18)

Original Droid findings after requested fixes:

- **H1 mixed JWT/API-key bypass: FIXED** — API-key filter now fail-closes supplied API keys and API-key authority is required for non-demo MCP.
- **H2 project deletion cascade: FIXED** — V12/V14 preserve key lifecycle rows; a DB trigger and a pre-data-plane transactional locked-row guard prevent an admin lock from being bypassed by project deletion.
- **H3 CLI ANSI gate: FIXED** — CLI test suite is green.
- **M1 duplicate lock/disable and no resolution path: FIXED** — lock remains available and audited unlock resolution is now exposed in backend and admin UI.
- **M2 FE/backend metadata drift: FIXED** — frontend consumes aligned lock/disabled metadata and authoritative `canDelete`.
- **M3 unbounded prefix candidates: FIXED** — bounded top-six lookup with fail-closed ambiguity handling.
- **M4 missing auth/lifecycle regression coverage: FIXED** — tests added for disabled/deleted/expired/admin-locked/mixed-auth/bounded lookup and unlock.
- **M5 first-100 repository selector: FIXED** — all project pages are loaded.
- **L1 snapshot permissions: NOT FIXED** — accepted Windows MVP risk; snapshots do not contain raw API keys, while config files retain restrictive permissions.

Re-review verdict: **READY WITH ACCEPTED LOW RISK**. No code was committed, pushed, merged, or reverted by Droid.