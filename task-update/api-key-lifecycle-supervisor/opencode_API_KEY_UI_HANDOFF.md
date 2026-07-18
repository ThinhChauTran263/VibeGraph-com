# OpenCode API Key UI Handoff

## Scope

Frontend-only API key lifecycle UI. No backend files were changed. No commit, push, or merge was performed.

## Files Changed

- `vibegraph-web/src/types/api.ts`
- `vibegraph-web/src/lib/api.ts`
- `vibegraph-web/src/stores/account.ts`
- `vibegraph-web/src/stores/admin.ts`
- `vibegraph-web/src/views/user/ApiKeysView.vue`
- `vibegraph-web/src/views/admin/UserDetailDrawer.vue`
- `vibegraph-web/src/lib/__tests__/accountAdminApi.spec.ts`
- `vibegraph-web/src/stores/__tests__/account.spec.ts`
- `vibegraph-web/src/stores/__tests__/admin.spec.ts`
- `vibegraph-web/src/views/user/__tests__/ApiKeysView.spec.ts`
- `vibegraph-web/src/views/admin/__tests__/UserDetailDrawer.spec.ts`
- `task-update/api-key-lifecycle-supervisor/opencode_API_KEY_UI_HANDOFF.md`

## Implemented

- User key creation requires a repository/project selection.
- Projects with an existing non-deleted key are blocked from replacement and show a clear delete-first reason.
- Admin-locked keys show a visible badge and cannot be deleted or replaced by the user.
- User delete uses `AdminConfirmDialog`; no browser `alert` or `confirm` is used.
- One-time secrets are only rendered from a successful create response.
- Admin user detail has no API key creation form and only lists key metadata with project, status, disabled actor, and lock metadata when supplied.
- Admin can disable or lock a specific key.
- No `createApiKeyForUser` surface remains and admin key operations never call `POST /api/admin/api-keys`.

## Expected Backend Contract

- `POST /api/account/api-keys` body: `{ name, projectId }`.
- `DELETE /api/account/api-keys/{id}` deletes a user-owned key when it is not admin locked.
- `PATCH /api/admin/api-keys/{id}/disable` disables a specific key.
- `PATCH /api/admin/api-keys/{id}/lock` locks a specific key.
- API key metadata may include `disabledBy`, `locked`, `lockedAt`, `lockedBy`, and `deletedAt`.

## Backend Blockers

The backend handoff file was not present when implementation started. The backend source visible at that time supported project-bound create and disable, but did not yet expose user delete, admin lock, or lock/actor metadata. Until those backend changes land, delete and lock actions will return an API error; the UI surfaces that error instead of failing silently.

## Verification

- `npm --prefix vibegraph-web run type-check`: PASS
- `npm --prefix vibegraph-web run test:unit -- --run`: PASS, 56 files and 457 tests
- `npm --prefix vibegraph-web run build`: PASS
- `git diff --check`: PASS; only line-ending warnings from unrelated dirty files

## GitNexus

Pre-edit symbol impact was LOW for the affected frontend store/API actions: one direct user-view caller for create and disable, and the admin disable path through `UserDetailDrawer` to `UsersTableView`. Final whole-worktree detection reported CRITICAL because the shared dirty worktree contains 65 changed files and 287 symbols from multiple agents, including backend/auth changes. No unrelated changes were reverted or modified.

## Droid Request-Changes Resolution (2026-07-18)

- **M1 admin lock resolution UI: FIXED.** Locked keys now expose a custom-confirmed Unlock action backed by the admin unlock endpoint.
- **M2 DTO drift: FIXED.** Frontend metadata aligns with backend `disabledBy`, `disabledReason`, `lockedAt`, `lockedBy`, `locked`, `deletedAt`, and `canDelete`; user deletion uses authoritative `canDelete` with a locked compatibility fallback.
- **M5 first-100 repository limit: FIXED.** `fetchProjects` loads all reported pages in parallel after page zero, so every owned repository is available for API-key binding.
- **NOT FIXED:** none for opencode-owned Droid findings.

Verification: type-check PASS; 56 test files / 459 tests PASS; production build PASS (existing chunk-size warning only). No commit/push/merge.