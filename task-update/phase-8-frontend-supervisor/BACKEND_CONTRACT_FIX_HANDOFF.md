# Backend Contract Fix Handoff

Status: backend contract implementation staged in the working tree; no commit, push, merge, delete, or revert performed.

## Current files changed

- `src/main/java/com/vibegraph/auth/dto/FeatureCapability.java`
- `src/main/java/com/vibegraph/auth/dto/AccountSessionStateResponse.java`
- `src/main/java/com/vibegraph/auth/service/FeatureGateService.java`
- `src/main/java/com/vibegraph/auth/service/AccountService.java`
- `src/main/java/com/vibegraph/auth/domain/ApiKey.java`
- `src/main/java/com/vibegraph/auth/dto/ProjectBindingResponse.java`
- `src/main/java/com/vibegraph/auth/dto/ApiKeyCreateRequest.java`
- `src/main/java/com/vibegraph/auth/dto/AdminApiKeyCreateRequest.java`
- `src/main/java/com/vibegraph/auth/dto/ApiKeyCreateResponse.java`
- `src/main/java/com/vibegraph/auth/dto/ApiKeyResponse.java`
- `src/main/java/com/vibegraph/auth/repository/ProjectOwnershipRepository.java`
- `src/main/java/com/vibegraph/auth/service/ApiKeyService.java`
- `src/main/java/com/vibegraph/auth/web/ApiKeyRequestContext.java`
- `src/main/java/com/vibegraph/auth/web/ApiKeyRequestContextAccessor.java`
- `src/main/java/com/vibegraph/auth/web/ApiKeyAuthFilter.java`
- `src/main/java/com/vibegraph/mcp/MeteredToolCallback.java`
- `src/main/java/com/vibegraph/common/config/McpServerConfig.java`
- `src/main/java/com/vibegraph/patch/controller/LocalPatchController.java`
- `src/main/resources/db/migration/V12__project_bound_api_keys.sql`
- focused backend tests under `src/test/java/com/vibegraph/auth/**`

## Contract draft

`GET /api/account/session-state` now carries a safe `features` map keyed by canonical feature names. Values are `{ enabled, reason }`; no flag ids, scope, descriptions, internal reasons, hashes, or secrets are exposed. The map includes canonical global flags and persisted canonical `mcp.tool.*` keys. Restricted accounts force product capabilities disabled while preserving the existing support/report route allowlist.

`POST /api/account/api-keys` accepts `{ name, projectId }`; `POST /api/admin/api-keys` accepts `{ userId, name, projectId }`. Project binding is validated against authoritative `projects.owner_id`. Create responses contain a one-time `secretKey` plus safe project metadata; list responses never contain raw secrets or hashes.

A nullable `api_keys.project_id` migration preserves old unbound rows. API-key authentication exposes only a safe key reference and nullable project context. MCP and local patch paths compare explicit project ids with bound context.

## Migration

`V12__project_bound_api_keys.sql` adds nullable `api_keys.project_id`, foreign key `ON DELETE SET NULL`, and an index. Existing keys remain unbound and require explicit project context for project-scoped runtime validation.

## Tests / blockers

Verification status:

- `git diff --check`: PASS three times (line-ending warnings only).
- `gitnexus_detect_changes({scope: "all"})`: ran; aggregate working tree is CRITICAL because this intentionally dirty multi-agent branch contains 58 changed files, including unrelated Phase 8 frontend/admin/report work. No changes were reverted.
- `./mvnw -DskipTests compile`: attempted repeatedly, but Maven execution was blocked by a transient Claude Code tool-classifier outage before the process started; no compile result is claimed.
- `./mvnw -Dtest=*ApiKey*,*Feature*,*Session*,*Account* test`: BLOCKED by the same outage before Maven could start; no test result is claimed.
- `./mvnw clean test`: not run because compile/focused test execution remained blocked by the same outage.

Known follow-up work before handoff is final: run the two mandatory Maven commands when shell classification recovers, resolve any compile/test failures, then complete the mandatory Java/code/security/database reviews. Review-agent launch attempts were also blocked by the same transient classifier outage.

## FE notes

Droid should consume `session-state.data.features`, send `projectId` on account/admin key creation, and render `data.project` from key create/list responses. The raw `secretKey` remains create-once only. The backend canonical field is `projectId`; no separate repository id exists in the current control-plane schema.

## Overlap / blockers

This backend card intentionally avoided frontend product files and preserved the existing dirty Phase 8 prompt/dispatch changes. Commit/push is not safe until compilation, focused tests, full clean tests, `git diff --check`, security/database/code reviews, and GitNexus scope validation pass.
