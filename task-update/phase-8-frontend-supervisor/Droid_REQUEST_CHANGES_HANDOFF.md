# Droid Frontend Contract Wiring Handoff

Status: assigned frontend API/types/store wiring is complete in the shared working tree. No commit, push, merge, delete, or revert was performed.

## Files Changed

- `vibegraph-web/src/types/api.ts`
- `vibegraph-web/src/lib/api.ts`
- `vibegraph-web/src/stores/account.ts`
- `vibegraph-web/src/stores/admin.ts`
- `vibegraph-web/src/lib/__tests__/accountAdminApi.spec.ts`
- `vibegraph-web/src/stores/__tests__/account.spec.ts`
- `vibegraph-web/src/stores/__tests__/admin.spec.ts`
- `task-update/phase-8-frontend-supervisor/Droid_REQUEST_CHANGES_HANDOFF.md`

## API, Types, And Store Changes

- `AccountSessionState.features` now mirrors the backend capability map as `Record<string, FeatureCapability>`.
- The account store exposes `getFeatureCapability`, which returns disabled for an absent capability.
- Session capability refreshes fail closed on errors and use a request sequence so stale concurrent responses cannot restore or clear newer capability state.
- Added typed `ApiKeyCreateRequest` and `AdminApiKeyCreateRequest` contracts with required `projectId`.
- Account and admin API-key clients send the backend's canonical `{ name, projectId }` and `{ userId, name, projectId }` payloads.
- API-key list metadata includes a nullable safe project binding. Create responses include the one-time `secretKey` and required safe project binding.
- The account store preserves project metadata in its secret-free key list entry.
- The admin store rejects an unbound creation before any network request.
- Browser requests continue to use `credentials: 'include'` with no `Authorization` header. No JWT persistence or app-code mock behavior was added.

## Tests

- `npm --prefix vibegraph-web run type-check`
  - PASS.
- `npm --prefix vibegraph-web run test:unit -- --run src/lib src/stores`
  - PASS: 19 test files, 236 tests.
- `npm --prefix vibegraph-web run build`
  - PASS: Vite production build completed. Existing chunk-size warning only.
- `git diff --check`
  - PASS: no whitespace errors. Existing line-ending warnings only.
- Focused review:
  - TypeScript review of capability race handling: PASS.
  - Security review: PASS. Cookie auth, one-time secret handling, and fail-closed binding were preserved.

## GitNexus Impact

- The index was refreshed before implementation.
- `fetchSessionState`, account `createApiKey`, and admin `createApiKeyForUser` were LOW risk.
- Direct callers are `UserLayout`, `ApiKeysView`, and `UserDetailDrawer`; no indexed execution flow was affected by those symbol-level checks.
- `gitnexus detect-changes` reports aggregate CRITICAL risk for the intentionally dirty multi-agent working tree: 63 files, 242 changed symbols, and 27 affected processes. This includes unrelated backend and Phase 8 frontend work and was not reverted.

## Overlap Handled

- Preserved existing notification, report, security/IP-block, audit, and other Phase 8 behavior already present in the shared files.
- Tests were extended without removing or weakening other agents' coverage.
- Existing fail-closed behavior in `vibegraph-web/src/lib/featureAvailability.ts` was retained.

## Remaining Blockers

- `vibegraph-web/src/views/admin/UserDetailDrawer.vue` still has no project selector and calls the admin store without `projectId`. The store now fails closed instead of sending an invalid unbound request.
- The backend handoff exposes project-bound admin key creation but no endpoint for an admin to list projects owned by the selected target user. A truthful admin selector requires that backend read contract, or an explicit product scope change.
- Backend compilation, focused Maven tests, full tests, and backend reviews remain unverified according to `BACKEND_CONTRACT_FIX_HANDOFF.md`.

## Commit / Push Safety

Not safe to commit or push the aggregate Phase 8 working tree yet. Resolve the admin target-user project-list contract/UI blocker, complete backend verification, and perform the final integrated review first.
