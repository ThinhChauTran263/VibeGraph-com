# CladueCli FE-1 Handoff

## Scope

Completed the Phase 8 FE-1 frontend API contract, shared TypeScript DTOs, and Pinia account/admin store support against the Phase 7 backend baseline. No backend files were edited. No commit, push, or merge was performed.

The working tree already contained concurrent Phase 8 frontend changes. This handoff describes only the FE-1 files/hunks below; aggregate git/GitNexus output includes other workers' changes.

## Files changed

- `vibegraph-web/src/types/api.ts`
- `vibegraph-web/src/lib/api.ts`
- `vibegraph-web/src/stores/account.ts`
- `vibegraph-web/src/stores/admin.ts`
- `vibegraph-web/src/lib/__tests__/accountAdminApi.spec.ts` (new)
- `vibegraph-web/src/lib/__tests__/importApi.spec.ts`
- `vibegraph-web/src/stores/__tests__/account.spec.ts`
- `vibegraph-web/src/stores/__tests__/admin.spec.ts`
- `vibegraph-web/src/stores/__tests__/auth.spec.ts`

## APIs and types added or aligned

### Account

- `GET /api/account/session-state`
- `GET /api/account/notifications`
- `GET /api/account/announcements`
- `GET /api/account/notifications/{id}`
- `PATCH /api/account/notifications/{id}/read`
- `PATCH /api/account/notifications/{id}/dismiss`
- Added/aligned `AccountSessionState` and `UserNotification`.

### Admin security and abuse

- `GET /api/admin/security/events`
- `GET /api/admin/security/request-events`
- `GET /api/admin/security/top-users`
- `GET /api/admin/security/top-ips`
- `GET /api/admin/security/ip-blocks`
- `POST /api/admin/security/ip-blocks`
- `PATCH /api/admin/security/ip-blocks/{id}`
- `DELETE /api/admin/security/ip-blocks/{id}`
- Added/aligned `AdminSecurityEvent`, `AdminRequestEvent`, `AdminRequestAggregate`, `AdminIpBlockRequest`, and `AdminIpBlock`, including nullable backend fields.

### Admin audit

- `GET /api/admin/audit-logs`
- `GET /api/admin/audit-logs/{id}`
- `GET /api/admin/audit-logs/retention`
- `PUT /api/admin/audit-logs/retention`
- Added/aligned `AdminAuditLogQuery`, `AdminAuditLog`, and `AdminAuditRetention`.

### Typed errors

`ApiError` now preserves backend `code`, safe `message`, and optional `details` while retaining the previous constructor argument order. Known Phase 7 codes are typed:

- `ACCOUNT_BLOCKED`
- `ACCOUNT_DEACTIVATED`
- `FEATURE_DISABLED`
- `QUOTA_EXCEEDED`
- `CREDIT_EXHAUSTED`
- `CONCURRENT_IMPORT_LIMIT`
- `TOO_MANY_REQUESTS`
- `IP_BLOCKED`

Unknown backend codes remain supported instead of being incorrectly rejected by the type layer.

## Store public API

### Account store

New state/getters:

- `sessionState`
- `notifications`
- `notificationDetail`
- `unreadNotifications`
- `loading`
- `error`
- `accountRestricted`
- `restrictionReason`

New actions:

- `fetchSessionState`
- `fetchNotifications`
- `fetchAnnouncements`
- `fetchNotification`
- `markNotificationRead`
- `dismissNotification`
- `refreshNotifications`

Read/dismiss mutations refresh the canonical notification list with the last requested limit. Existing profile, usage, project, API-key, and report normalization remains available. Touched local state updates use immutable replacements.

### Admin store

New state:

- `requestEvents`
- `topUsers`
- `topIps`
- `ipBlocks`
- `auditLogs`
- `auditLogDetail`
- `auditRetention`
- `auditPagination`
- `loading`
- `error`

New/finalized actions:

- `fetchRequestEvents`
- `fetchTopUsers`
- `fetchTopIps`
- `fetchIpBlocks`
- `fetchSecurityData`
- `refreshSecurity`
- `createIpBlock`
- `updateIpBlock`
- `deleteIpBlock`
- `fetchAuditLogs`
- `refreshAudit`
- `fetchAuditLogDetail`
- `fetchAuditRetention`
- `updateAuditRetention`

IP-block mutations refresh all canonical security panels. Audit-retention mutation refreshes retention plus the currently filtered audit page. `fetchSecurityData` preserves the existing UI compatibility return value listing unavailable sub-contracts while successful panels still load.

## Authentication invariants

- Browser requests retain `credentials: 'include'`.
- Browser requests retain `X-VibeGraph-Client: web`.
- `_authHeaders()` remains empty; browser JWT bearer headers are not synthesized.
- No JWT is written to `localStorage`.
- Auth regression test verifies an accidentally returned readable token is ignored.
- Existing stale `vg_token` removal remains cleanup-only.

## Verification results

### Required focused store tests

```text
npm --prefix vibegraph-web run test:unit -- --run src/stores/__tests__/account.spec.ts src/stores/__tests__/admin.spec.ts src/stores/__tests__/auth.spec.ts
```

Result: **PASS** — 3 files, 34 tests.

### FE-1 API + store regression suite

```text
npm --prefix vibegraph-web run test:unit -- --run src/lib/__tests__/accountAdminApi.spec.ts src/lib/__tests__/importApi.spec.ts src/stores/__tests__/account.spec.ts src/stores/__tests__/admin.spec.ts src/stores/__tests__/auth.spec.ts
```

Result: **PASS** — 5 files, 53 tests.

### Production bundle

```text
npm --prefix vibegraph-web run build-only
```

Result: **PASS**. Vite emitted only the existing large-chunk warning.

### Formatting / lint / diff

- Focused Prettier check: **PASS**.
- Focused ESLint check: **PASS for file rules**; command also prints a non-fatal `eslint-plugin-oxlint` message because it looks for `.oxlintrc.json` from the repository root.
- `git diff --check`: **PASS**.

### Type check

```text
npm --prefix vibegraph-web run type-check
```

Result: **BLOCKED by concurrent/pre-existing test typing outside FE-1 scope**:

- `src/views/__tests__/LoginView.spec.ts`: `DOMWrapper.exists` type error.
- `src/views/user/__tests__/ProfileView.spec.ts`: three `DOMWrapper.exists` type errors.

Earlier type-check attempts also surfaced concurrent dashboard/security test changes; FE-1-specific errors were corrected. The final focused FE-1 tests and Vite production build pass.

### Full unit suite

```text
npm --prefix vibegraph-web run test:unit -- --run
```

Result: **408 passed / 412 total; 4 failures outside FE-1-owned files**:

- 2 failures in `src/views/user/__tests__/ApiKeysView.spec.ts` (missing test selectors).
- 2 failures in `src/views/user/__tests__/ProjectsView.spec.ts` (missing router injection/query).

These were not modified because the assignment limits work to API/types/stores and focused tests.

## GitNexus / impact

- Pre-edit `ApiError` impact: **CRITICAL**, 59 impacted symbols, 30 direct dependencies, 23 processes, 6 modules. The implementation therefore kept the constructor parameter order and only appended optional typed metadata.
- `accountApi`, `adminApi`, `useAccountStore`, and `useAdminStore` returned LOW/zero from the stale index; real filesystem consumers were reviewed instead.
- Final `gitnexus_detect_changes(scope=all)` reported **CRITICAL**, 190 changed symbols, 75 affected symbols, and 28 changed files. This aggregate is contaminated by all concurrent Phase 8 frontend workers and must not be attributed solely to FE-1.
- No HIGH/CRITICAL impact warning was ignored; shared-error compatibility was explicitly preserved and tested.

## Backend contract mismatch

Phase 7 exposes:

```text
GET /api/account/session-state
```

This response contains account identity/status/safe reason only. It does **not** contain a feature-capability map.

The concurrent frontend helper `src/lib/featureAvailability.ts` expects:

```text
GET /api/session-state
```

with a `features` map. That backend route/shape does not exist in Phase 7. FE-1 did not invent a frontend map, cast account status into feature flags, or add app-code mocks. Admin feature flags remain available through `admin.featureFlags`, and user flows can react to typed `FEATURE_DISABLED` API errors. Authoritative proactive user gating requires a dedicated authenticated backend capability endpoint.

## Remaining integration notes

- User notification views can migrate from direct `accountApi` calls to the account-store notification actions to share loading/error/refresh state.
- Admin Security and Audit views can consume the new store `loading` and `error` state incrementally; their current local state remains compatible.
- Existing direct UI consumers of `fetchSecurityData`, audit pagination, announcements, and feature flags were preserved.
- The supervisor/integration worker should resolve the unrelated type-check and four full-suite failures before the global Phase 8 gate.

## Scope confirmation

- Backend edited: **No**
- App-code mocks/fake random business data added: **No**
- JWT localStorage introduced: **No**
- Commit: **No**
- Push: **No**
- Merge: **No**
