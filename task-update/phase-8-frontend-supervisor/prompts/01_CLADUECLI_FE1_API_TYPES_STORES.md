# CladueCli - FE-1 API Contract / Stores

You are `CladueCli`, working on VibeGraph Phase 8 frontend.

Repo: `D:\Users\User\IdeaProjects\VibeGraph`
Scope: FE API contract, TypeScript types, Pinia stores, feature availability only.

Do not commit, push, or merge.
Do not edit backend.

Read first:
- `AGENTS.md`
- `task-update/phase-8-frontend-supervisor/README.md`
- `task-update/phase-7-backend-supervisor/BE-6_FINAL_REVIEW.md`

Goal:
Update VibeGraph frontend API/types/stores to match the Phase 7 backend contract.

Allowed primary files:
- `vibegraph-web/src/types/api.ts`
- `vibegraph-web/src/lib/api.ts`
- `vibegraph-web/src/stores/account.ts`
- `vibegraph-web/src/stores/admin.ts`
- focused store/API tests only

Work items:
- Keep `auth.ts` HttpOnly-cookie based. Do not put JWT in localStorage.
- Add account notification APIs:
  - `GET /api/account/notifications`
  - `GET /api/account/announcements`
  - `GET /api/account/notifications/{id}`
  - `PATCH /api/account/notifications/{id}/read`
  - `PATCH /api/account/notifications/{id}/dismiss`
- Add admin security/abuse APIs:
  - `GET /api/admin/security/events`
  - `GET /api/admin/security/request-events`
  - `GET /api/admin/security/top-users`
  - `GET /api/admin/security/top-ips`
  - `GET/POST/PATCH/DELETE /api/admin/security/ip-blocks`
- Add admin audit APIs:
  - `GET /api/admin/audit-logs`
  - `GET /api/admin/audit-logs/{id}`
  - `GET/PUT /api/admin/audit-logs/retention`
- Verify existing admin overview/plans/pricing/announcements/users/reports APIs match backend DTOs.
- Add typed error handling for:
  - `ACCOUNT_BLOCKED`
  - `ACCOUNT_DEACTIVATED`
  - `FEATURE_DISABLED`
  - `QUOTA_EXCEEDED`
  - `CREDIT_EXHAUSTED`
  - `CONCURRENT_IMPORT_LIMIT`
  - `TOO_MANY_REQUESTS`
  - `IP_BLOCKED`
- Expose store actions/state needed by UI workers:
  - feature availability
  - announcements/notifications
  - admin security
  - audit logs
  - refresh after admin mutation

Acceptance:
- All new backend endpoints have typed frontend API functions.
- Stores expose loading/error/refresh actions needed by UI workers.
- No app-code mocks or `Math.random` fake data.
- Existing tests are updated or added for API/store behavior.

Required verification:
- `cd vibegraph-web && npm run type-check`
- `cd vibegraph-web && npm run test:unit -- --run src/stores/__tests__/account.spec.ts src/stores/__tests__/admin.spec.ts src/stores/__tests__/auth.spec.ts`

Handoff:
Write `task-update/phase-8-frontend-supervisor/CladueCli_HANDOFF.md` with:
- files changed
- APIs/types added
- tests run and exact result
- any backend contract mismatch
- remaining UI integration notes
