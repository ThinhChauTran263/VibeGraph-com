# CladueCli FE-1 Re-Verification After FE-2

## Result

**PASS after one confirmed FE-1 contract correction.**

FE-1 API/types/stores remain compatible with the Phase 7 backend after ClaudeChat FE-2 user UI integration. The required type-check, focused FE-1/FE-2 tests, and production build all pass. No backend source was edited, and no commit or push was performed.

## Regression assessment

### Confirmed contract regression found and fixed

The FE-1 API/type/store layer still represented account API-key creation as optionally repository-bound:

- `ApiKey` and `ApiKeyCreated` exposed `projectId` / `projectName`.
- `accountApi.createApiKey(name, projectId?)` could POST `{ name, projectId }`.
- `account.createApiKey(name, projectId?)` constructed repository metadata locally.

The Phase 7 backend contract is name-only:

- `ApiKeyCreateRequest`: `name`
- `ApiKeyCreateResponse`: `id`, `keyPrefix`, `name`, `secretKey`, `createdAt`, `expiresAt`
- `ApiKeyResponse`: `id`, `keyPrefix`, `name`, `createdAt`, `lastUsedAt`, `expiresAt`, `disabledAt`

This was a real FE-1 contract mismatch, not a backend or FE-2 implementation failure. It was corrected narrowly:

- Removed `projectId` / `projectName` from account API-key DTO types.
- Changed `accountApi.createApiKey` to name-only and always POST `{ name }`.
- Changed `account.createApiKey` to name-only and stopped synthesizing repository binding.
- Updated the disabled FE-2 call site to the corrected store signature. The UI remains visibly disabled and still sends no create request.
- Added a contract regression test asserting the exact name-only request body.

Owner: **CladueCli / FE-1**, resolved in this re-verification.

### No other FE-1 regressions found

- FE-2 correctly aligned `UserUsage` with Phase 7 MB/credit fields: `usedMb`, `limitMb`, `remainingMb`, `quotaOverrideMb`, `creditsUsed`, `creditsLimit`, and `creditsRemaining`.
- `AdminUserResponse` now models the Phase 7 MB fields and retains optional byte compatibility fields for older running images.
- Account/admin store public actions used by FE-2 still type-check.
- Notifications, admin security/IP blocks, and admin audit API functions remain typed and route-aligned.
- Typed `ApiError` metadata remains intact.
- No app-code mocks or `Math.random` fake business data exist in FE-1 API/store files.

## Authentication verification

**PASS**

- Fetch API methods continue to use `credentials: 'include'`.
- Browser API client continues to send `X-VibeGraph-Client: web`.
- `_authHeaders()` remains empty.
- No application code writes `vg_token` or a JWT to `localStorage`.
- The only `localStorage.setItem('vg_token', ...)` occurrence is the auth unit test that verifies stale-token cleanup.
- Required auth tests pass.

## Notifications / security / audit verification

**PASS**

Verified retained frontend contracts:

### Account notifications

- `GET /api/account/notifications`
- `GET /api/account/announcements`
- `GET /api/account/notifications/{id}`
- `PATCH /api/account/notifications/{id}/read`
- `PATCH /api/account/notifications/{id}/dismiss`

### Admin security

- `GET /api/admin/security/events`
- `GET /api/admin/security/request-events`
- `GET /api/admin/security/top-users`
- `GET /api/admin/security/top-ips`
- `GET /api/admin/security/ip-blocks`
- `POST /api/admin/security/ip-blocks`
- `PATCH /api/admin/security/ip-blocks/{id}`
- `DELETE /api/admin/security/ip-blocks/{id}`

### Admin audit

- `GET /api/admin/audit-logs`
- `GET /api/admin/audit-logs/{id}`
- `GET /api/admin/audit-logs/retention`
- `PUT /api/admin/audit-logs/retention`

The account/admin store notification, security refresh, IP-block mutation refresh, audit pagination, and audit retention refresh contracts remain available.

## Exact verification results

### Type-check

Command:

```text
cd vibegraph-web && npm run type-check
```

Equivalent command executed from repository root:

```text
npm --prefix vibegraph-web run type-check
```

Result: **PASS** — `vue-tsc --build` exited successfully with no diagnostics.

### Required FE-1 + FE-2 focused tests

Command:

```text
cd vibegraph-web && npm run test:unit -- --run src/stores/__tests__/account.spec.ts src/stores/__tests__/admin.spec.ts src/stores/__tests__/auth.spec.ts src/views/__tests__/LoginView.spec.ts src/views/user/__tests__/ProfileView.spec.ts src/views/user/__tests__/ApiKeysView.spec.ts src/views/user/__tests__/ProjectsView.spec.ts
```

Result before the narrow contract correction: **PASS — 7 files, 51 tests**.

Post-fix command also included the FE-1 API contract spec:

```text
npm --prefix vibegraph-web run test:unit -- --run src/lib/__tests__/accountAdminApi.spec.ts src/stores/__tests__/account.spec.ts src/stores/__tests__/admin.spec.ts src/stores/__tests__/auth.spec.ts src/views/__tests__/LoginView.spec.ts src/views/user/__tests__/ProfileView.spec.ts src/views/user/__tests__/ApiKeysView.spec.ts src/views/user/__tests__/ProjectsView.spec.ts
```

Post-fix result: **PASS — 8 files, 64 tests**.

### Production build

Command:

```text
cd vibegraph-web && npm run build
```

Equivalent command executed:

```text
npm --prefix vibegraph-web run build
```

Result before correction: **PASS**.

Post-fix result: **PASS** — type-check plus Vite production build completed; 2,978 modules transformed. The only output warning is the existing chunk-size warning for assets over 500 kB.

### Diff validation

```text
git diff --check
```

Result: **PASS — no output**.

## Failure ownership

No required verification failure remains.

| Area | Result | Owner if follow-up is needed |
| --- | --- | --- |
| FE-1 API/types/stores | PASS after correction | CladueCli — resolved |
| FE-2 user UI tests | PASS | ClaudeChat — no action |
| Auth HttpOnly cookie flow | PASS | No action |
| Notifications/security/audit | PASS | No action |
| Backend capability gaps already documented | Unchanged | Backend supervisor/product decision |
| Vite large-chunk warning | Non-blocking | Integration/performance follow-up |

## GitNexus

- Pre-fix impact for account-store `createApiKey`: **LOW**, one direct consumer (`ApiKeysView.vue`).
- Pre-fix impact for API `createApiKey`: **LOW** from the stale index.
- Pre-fix impact for `ApiKeyCreated`: **MEDIUM**, 13 direct import dependents reported; direct source usage was checked and the frontend type-check/test/build gates passed after correction.
- Final `gitnexus_detect_changes(scope=all)` remains **CRITICAL aggregate**: 234 changed symbols, 75 affected symbols, and 44 changed files. This reflects all concurrent Phase 8 frontend work, not this re-verification alone.

## Remaining backend contract blockers

Unchanged from FE-1/FE-2 handoffs:

1. No user-facing plan catalog endpoint.
2. Account API keys do not support repository binding; FE-2 therefore keeps creation disabled with a truthful reason.
3. `/api/account/session-state` has no feature-capability map; `/api/session-state` with `features` is not a Phase 7 backend route.

No frontend mocks or invented contracts were added to bypass these blockers.

## Scope confirmation

- Backend edited: **No**
- Commit: **No**
- Push: **No**
- Merge: **No**
