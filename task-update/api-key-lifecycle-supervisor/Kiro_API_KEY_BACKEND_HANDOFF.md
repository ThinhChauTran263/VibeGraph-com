# Kiro API Key Backend Handoff

Status: backend lifecycle implementation complete in the dirty working tree. No commit, push, merge, reset, checkout, or revert was performed. Existing agent changes outside this backend scope were preserved.

## Product decisions implemented

- Admin cannot create API keys; no `POST /api/admin/api-keys` route, `AdminApiKeyCreateRequest`, or `createForUser` reference remains in backend source.
- Users create their own key and must bind it to an owned project/repository.
- A project is the CLI/MCP identity boundary.
- At most one non-deleted key exists for each `(user_id, project_id)`, including disabled keys.
- User disable sets `disabled_by=USER`; a user must soft-delete before replacement.
- Admin disable/lock sets `disabled_by=ADMIN`; the key is locked and cannot be user-deleted or replaced.
- Admin can list metadata, disable/lock a key, and disable API-key creation for an account through the existing account-settings admin flow.

## Exact backend files changed in this scope

- `src/main/java/com/vibegraph/auth/domain/ApiKey.java`
- `src/main/java/com/vibegraph/auth/domain/ApiKeyDisabledBy.java`
- `src/main/java/com/vibegraph/auth/repository/ApiKeyRepository.java`
- `src/main/java/com/vibegraph/auth/service/ApiKeyService.java`
- `src/main/java/com/vibegraph/auth/web/AccountApiKeyController.java`
- `src/main/java/com/vibegraph/auth/web/AdminApiKeyController.java`
- `src/main/java/com/vibegraph/auth/web/ApiKeyAuthFilter.java`
- `src/main/java/com/vibegraph/auth/dto/ApiKeyResponse.java`
- `src/main/java/com/vibegraph/common/exception/ApiKeyProjectConflictException.java`
- `src/main/java/com/vibegraph/common/exception/ApiKeyAdminLockedException.java`
- `src/main/resources/db/migration/V12__project_bound_api_keys.sql`
- `src/main/resources/db/migration/V13__api_key_lifecycle.sql`
- `src/test/java/com/vibegraph/auth/service/ApiKeyServiceTest.java`
- `src/test/java/com/vibegraph/auth/service/ApiKeyLifecycleServiceTest.java`
- `src/test/java/com/vibegraph/auth/web/AccountApiKeyControllerTest.java`
- `src/test/java/com/vibegraph/auth/web/AdminApiKeyControllerTest.java`
- `src/test/java/com/vibegraph/auth/web/ApiKeyAuthFilterTest.java`
- `src/test/java/com/vibegraph/auth/integration/ApiKeyUniquenessConcurrencyTest.java`

`ApiKeyCreateRequest`, `ApiKeyCreateResponse`, and `ProjectBindingResponse` were already present in the dirty worktree from the parallel project-binding agent and remain part of the final API contract; they were not reverted.

## Database migrations

- `V12__project_bound_api_keys.sql`: adds nullable `project_id`, FK to `projects(project_id)` with `ON DELETE CASCADE`, and project index. Existing unbound legacy rows remain safe for migration but fail closed for API-key authentication.
- `V13__api_key_lifecycle.sql`: adds `deleted_at`, `disabled_by`, `disabled_reason`, validation constraint, deleted/indexed lookup fields, indexed auth candidates, and the partial unique index `uq_api_keys_live_user_project` on `(user_id, project_id)` where `deleted_at IS NULL`.
- V13 conservatively backfills pre-existing rows with `disabled_at IS NOT NULL` and no actor as `disabled_by=ADMIN`, preventing an unknown legacy disabled key from becoming user-deletable.

## API contract

### User

- `POST /api/account/api-keys`
  - Request: `{ "name": string, "projectId": string }`
  - Response: `201`, `Cache-Control: no-store`, one-time `secretKey`, `keyPrefix`, name, safe project metadata, timestamps.
  - Rejects missing/blank project, non-owned project, duplicate non-deleted key, user-disabled existing key, admin-locked existing key, disabled feature/account, and plan limit.
- `GET /api/account/api-keys`
  - Lists non-deleted metadata only. Never returns raw secret or hash.
  - Includes `project`, `disabledAt`, `disabledBy`, `disabledReason`, `deletedAt`, `locked`, and `canDelete`.
- `PATCH /api/account/api-keys/{id}/disable`
  - User disable; atomic guard prevents changing an admin-locked row.
- `DELETE /api/account/api-keys/{id}`
  - Returns `204`; soft-deletes only the owner’s non-deleted, non-admin-locked key.
  - Admin-locked attempts return `403` with `API_KEY_ADMIN_LOCKED`.

### Admin

- `GET /api/admin/api-keys?userId=...`: list safe metadata.
- `PATCH /api/admin/api-keys/{id}/disable`: admin lock/disable.
- `PATCH /api/admin/api-keys/{id}/lock`: explicit alias for admin lock used by the current admin UI.
- There is intentionally no admin create endpoint.

### Runtime authentication

- API-key authentication is restricted to `/mcp...` and CLI patch routes (`/api/projects/{projectId}/patch`, `/api/projects/current/patch`).
- Candidate lookup uses indexed `key_prefix`, excludes deleted/disabled rows, and then runs BCrypt only on bounded candidates.
- Bound projects must still exist and belong to the key owner; unbound/orphaned keys fail closed.

## Tests and verification

- RED gate: lifecycle tests initially failed to compile because the requested lifecycle types, repository methods, delete service method, and exceptions were absent.
- Focused requested gate: PASS
  - `./mvnw.cmd "-Dtest=*ApiKey*,*Feature*,*Session*,*Account*" test`
  - Includes the API-key lifecycle tests, filter tests, admin/account tests, and PostgreSQL Testcontainers uniqueness test.
- Concurrency test: PASS
  - `ApiKeyUniquenessConcurrencyTest` with PostgreSQL 16/Testcontainers; exactly one direct concurrent insert survives for the same user/project.
- Full clean gate: PASS
  - `./mvnw.cmd clean test`
  - `817` tests run, `0` failures, `0` errors, `9` skipped.
- `git diff --check`: PASS; Git emitted only normal LF/CRLF conversion warnings for the dirty Windows worktree.
- Diagnostics: no compile diagnostics in the modified production API-key files. Test diagnostics are non-blocking IDE warnings for ignored `assertThrows` return values and JUnit lifecycle methods.

## Dirty-worktree note / GitNexus

`gitnexus_detect_changes({scope:"unstaged"})` reports `critical` for the aggregate worktree because parallel agents touched 75 files / 346 symbols, including unrelated frontend, CLI, docs, feature-capability, MCP, and patch work. This is not an API-key-only risk report. No unrelated changes were reverted.

## Remaining follow-up / blocker

- The working tree is still dirty by design and must not be committed by this handoff.
- The aggregate GitNexus report still includes stale historical symbols such as `createForUser` from the indexed baseline, while current backend text search confirms no `AdminApiKeyCreateRequest`, `createForUser`, or admin `@PostMapping` remains. Re-indexing is a separate repository-wide operation and was not performed to avoid mixing with concurrent agent work.
- The security review recommended a future centralized API-key principal/authority model for broader route enforcement. This implementation fail-closes API-key authentication to MCP/CLI patch routes, which is the safe boundary for the current CLI/MCP API-key contract.

## Droid Request-Changes Resolution (2026-07-18)

- **H1 mixed JWT/API-key bypass: FIXED.** A supplied `X-API-Key` is always validated on supported routes, clears any prior JWT context, and returns `401` on invalid/mismatched credentials. Valid API-key principals receive `API_KEY`; non-demo `/mcp/**` requires that authority.
- **H2 project cascade deletes key history: FIXED.** V12 uses `ON DELETE SET NULL`; forward migration V14 preserves key rows and blocks locked-project deletion. The public deletion orchestrator checks locked rows before touching Neo4j/watchers and holds a database row lock through the control-plane transaction.
- **M1 lock resolution: FIXED.** Added audited `PATCH /api/admin/api-keys/{id}/unlock`; unlock clears administrator disable/lock metadata but does not delete the key.
- **M2 metadata contract backend portion: FIXED.** `ApiKeyResponse` now returns `disabledReason`, `lockedAt`, `lockedBy`, `locked`, `deletedAt`, and `canDelete`; `locked_by` is persisted by V14.
- **M3 unbounded BCrypt candidates: FIXED.** Prefix lookup fetches at most six rows, accepts at most five candidates, and fails closed before BCrypt if the safety bound is exceeded.
- **M4 missing lifecycle/auth regressions: FIXED.** Added disabled, deleted, expired, admin-locked, mixed JWT/API-key, bounded-candidate, unlock service, and unlock controller tests.
- **NOT FIXED:** none for Kiro-owned Droid findings.

Verification: requested focused Maven gate PASS; `git diff --check` PASS after whitespace cleanup. No commit/push/merge.