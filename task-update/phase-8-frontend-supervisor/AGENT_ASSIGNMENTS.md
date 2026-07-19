# Phase 8 Frontend - Agent Assignments

Base branch: `poc`
Backend contract: Phase 7 commit `8f1b38f`

## CladueCli - FE-1 API Contract / Stores

```text
You are CladueCli, working on VibeGraph Phase 8 frontend.

Repo: D:\Users\User\IdeaProjects\VibeGraph
Scope: FE API contract, TypeScript types, Pinia stores, feature availability only.
Do not commit, push, or merge.
Do not edit backend.

Read first:
- AGENTS.md
- task-update/phase-8-frontend-supervisor/README.md
- task-update/phase-7-backend-supervisor/BE-6_FINAL_REVIEW.md

Goal:
Update VibeGraph frontend API/types/stores to match the Phase 7 backend contract.

Work items:
- Update `vibegraph-web/src/types/api.ts`.
- Update `vibegraph-web/src/lib/api.ts`.
- Update `vibegraph-web/src/stores/account.ts`.
- Update `vibegraph-web/src/stores/admin.ts`.
- Keep `auth.ts` HttpOnly-cookie based. Do not put JWT in localStorage.
- Add account notifications APIs:
  - GET `/api/account/notifications`
  - GET `/api/account/announcements`
  - GET `/api/account/notifications/{id}`
  - PATCH `/api/account/notifications/{id}/read`
  - PATCH `/api/account/notifications/{id}/dismiss`
- Add admin security/abuse APIs:
  - GET `/api/admin/security/events`
  - GET `/api/admin/security/request-events`
  - GET `/api/admin/security/top-users`
  - GET `/api/admin/security/top-ips`
  - GET/POST/PATCH/DELETE `/api/admin/security/ip-blocks`
- Add admin audit APIs:
  - GET `/api/admin/audit-logs`
  - GET `/api/admin/audit-logs/{id}`
  - GET/PUT `/api/admin/audit-logs/retention`
- Verify existing admin overview/plans/pricing/announcements/users/reports APIs match backend DTOs.
- Add typed error handling for:
  - ACCOUNT_BLOCKED
  - ACCOUNT_DEACTIVATED
  - FEATURE_DISABLED
  - QUOTA_EXCEEDED
  - CREDIT_EXHAUSTED
  - CONCURRENT_IMPORT_LIMIT
  - TOO_MANY_REQUESTS
  - IP_BLOCKED

Acceptance:
- All new backend endpoints have typed frontend API functions.
- Stores expose loading/error/refresh actions needed by UI workers.
- No app-code mocks or Math.random fake data.
- Existing tests are updated or added for API/store behavior.

Required verification:
- `cd vibegraph-web && npm run type-check`
- `cd vibegraph-web && npm run test:unit -- --run src/stores/__tests__/account.spec.ts src/stores/__tests__/admin.spec.ts src/stores/__tests__/auth.spec.ts`

Handoff:
Write `task-update/phase-8-frontend-supervisor/CladueCli_HANDOFF.md`.
```

## ClaudeChat - FE-2 User Product UI

```text
You are ClaudeChat, working on VibeGraph Phase 8 frontend.

Repo: D:\Users\User\IdeaProjects\VibeGraph
Scope: User shell and user product pages only.
Do not commit, push, or merge.
Do not edit backend.

Read first:
- AGENTS.md
- task-update/phase-8-frontend-supervisor/README.md
- CladueCli handoff if available

Goal:
Implement the user-facing VibeGraph app experience against real APIs.

Work items:
- Login page: VibeGraph logo/name top-left.
- User layout/sidebar:
  - collapsible independently
  - hamburger icon expands when collapsed
  - no text overlap in collapsed mode
  - icons for every menu item
  - account mini-card shows email/display name, plan, remaining credits
  - remove Workspaces, Spec Designer, Community, Referral, Tutorial for now
- User overview:
  - repo count
  - remaining credits
  - current plan
  - quick actions to Repositories, API Keys, Reports
- Repositories:
  - list imported projects first
  - `New Repository` reveals existing import form
  - keep existing Local/Archive/GitHub import UX/copy/logic
  - successful import opens project/graph/analyze view
  - disabled import features visibly disable relevant controls
- API Keys:
  - create API key requires project/repository selection
  - show project binding in list
  - one-time secret display only after create
  - disabled state when API keys disabled/account blocked/feature off
- Usage:
  - plan, quota in MB, remaining storage, credits, ledger
- Subscription:
  - real plan catalog from backend, not hardcoded stale values
  - Enterprise shows contact sales state
- Settings:
  - profile update
  - password change old/new/confirm, no OTP

Acceptance:
- User pages use real store/API data.
- Disabled states are clear and non-interactive, not silent no-ops.
- Sidebar responsive and no overlap at 320/768/1024/1440.
- No console errors in Chrome DevTools.

Required verification:
- `cd vibegraph-web && npm run type-check`
- `cd vibegraph-web && npm run test:unit -- --run src/views/user`
- Chrome DevTools screenshots/notes for login, overview, repositories, API keys, sidebar collapsed/expanded.

Handoff:
Write `task-update/phase-8-frontend-supervisor/ClaudeChat_HANDOFF.md`.
```

## Droid - FE-3 Admin Overview / Charts

```text
You are Droid, working on VibeGraph Phase 8 frontend.

Repo: D:\Users\User\IdeaProjects\VibeGraph
Scope: Admin overview dashboard and charts only.
Do not commit, push, or merge.
Do not edit backend.

Read first:
- AGENTS.md
- task-update/phase-8-frontend-supervisor/README.md
- CladueCli handoff if available

Goal:
Rebuild Admin Overview as a professional operations dashboard using real backend data and ECharts/vue-echarts.

Work items:
- Use `echarts` / `vue-echarts`; do not add another chart library.
- Overview sections:
  - total users
  - online users
  - credit consumption
  - system storage
  - imported repositories/projects
  - top storage projects
  - plan distribution
  - security/abuse alerts
- Chart controls:
  - day/month/quarter/year where data supports it
  - month shows all 12 months
  - year shows last 5 years
  - online users chart shows last 10 minutes
  - online chart labels use 24h time
  - y-axis ranges should be readable, not huge empty spacing
- Layout:
  - total user, online user, credit, and system storage chart area is one coherent section
  - top storage projects replaces the old square plan distribution area
  - plan distribution and security alerts become horizontal/wide sections to reduce wasted empty space
  - sticky admin operations/header area where appropriate
- Poll `/api/admin/overview` every 15-30 seconds while visible.
- Do not label it as fake. Use small admin status text like `Polling`.

Acceptance:
- Charts are visually clear, aligned, and responsive.
- No overlapping labels or clipped donut text.
- Reload does not create duplicate online chart points.
- Online chart starts from the x-axis baseline visually.
- No console errors.

Required verification:
- `cd vibegraph-web && npm run type-check`
- `cd vibegraph-web && npm run test:unit -- --run src/views/admin/__tests__/DashboardView.spec.ts`
- Chrome DevTools screenshots at 1440, 1024, 768, 320.

Handoff:
Write `task-update/phase-8-frontend-supervisor/Droid_HANDOFF.md`.
```

## Kiro - FE-4 Admin Ops / Security / System

```text
You are Kiro, working on VibeGraph Phase 8 frontend.

Repo: D:\Users\User\IdeaProjects\VibeGraph
Scope: Admin users, plans/credits, system feature flags, security/IP block, audit, admin settings.
Do not commit, push, or merge.
Do not edit backend.

Read first:
- AGENTS.md
- task-update/phase-8-frontend-supervisor/README.md
- CladueCli handoff if available

Goal:
Make admin operations complete, balanced, and connected to Phase 7 backend APIs.

Work items:
- Admin layout/sidebar:
  - same collapsible pattern as user
  - admin-only menu
  - no duplicate Admin Console titles
  - no overlap in collapsed mode
- Users:
  - users table with filters/search
  - inline/full detail panel, not cramped right drawer
  - detail header shows email, blocked/deactivated status, plan
  - block/unblock/deactivate with safe reason
  - update plan/quota/credit refreshes visible data immediately
  - API key creation toggle design polished and aligned
  - disable specific API keys
- Plans & Credits:
  - CRUD connected
  - all storage values displayed in MB
  - pricing rule form balanced
  - reset button
  - Enterprise contact sales toggle styled properly
- System / Feature Flags:
  - global controls for registration, imports, CLI push, API key creation, project analyze, gen use case
  - MCP global and child tool controls
  - collapsible groups with only title shown when collapsed
  - disabled feature state should propagate to user UI via store contract
- Security:
  - request events
  - top users/IPs
  - IP block create/update/delete
  - rate/abuse status
- Audit:
  - audit log list/detail
  - retention setting
- Admin Settings:
  - admin profile and password change like user settings

Acceptance:
- Admin pages use real API data.
- Forms/buttons align and remain balanced at desktop/tablet/mobile.
- Updates refresh without manual reload.
- No default browser alert/confirm for app-level actions; use designed dialogs/toasts.
- No console errors.

Required verification:
- `cd vibegraph-web && npm run type-check`
- `cd vibegraph-web && npm run test:unit -- --run src/views/admin src/stores/__tests__/admin.spec.ts`
- Chrome DevTools screenshots for users detail, plans/credits, system, security, audit.

Handoff:
Write `task-update/phase-8-frontend-supervisor/Kiro_HANDOFF.md`.
```

## CodexCli - FE-5 Reports / Notifications / Realtime / Blocked UX

```text
You are CodexCli, working on VibeGraph Phase 8 frontend.

Repo: D:\Users\User\IdeaProjects\VibeGraph
Scope: Reports, notifications, announcements user delivery, realtime, blocked/deactivated UX polish.
Do not commit, push, or merge.
Do not edit backend.

Read first:
- AGENTS.md
- task-update/phase-8-frontend-supervisor/README.md
- CladueCli handoff if available

Goal:
Finish user/admin communications and immediate account/system state feedback.

Work items:
- Notifications:
  - after login/app load, active announcement appears as a highlighted notice
  - notice has `Read` and `Close`
  - `Read` opens Notifications detail/list page
  - notifications list newest first
  - detail shows creator, createdAt, title, body
  - mark read/dismiss uses backend, not only localStorage
- Announcements admin UI:
  - create/update/disable/delete
  - no duplicate titles or redundant info
  - balanced form layout
  - severity/type states
- Reports:
  - user report list/detail/thread
  - admin report list/detail/thread
  - realtime message/close updates
  - sticky reply box on mobile
  - close report shows deleteAfter date
- Blocked/deactivated UX:
  - if account becomes blocked/deactivated, product controls become unavailable quickly
  - show safe reason and support/report path
  - do not require manual refresh for obvious state updates where polling/revalidation exists
- Feature disabled UX:
  - controls visibly disabled and explain why

Acceptance:
- Notifications are backend-backed.
- Reports realtime works for user/admin without leaking other users' reports.
- Blocked/deactivated copy is safe and clear.
- No browser default popup UX.
- No console errors.

Required verification:
- `cd vibegraph-web && npm run type-check`
- `cd vibegraph-web && npm run test:unit -- --run src/views/user/__tests__/ReportsView.spec.ts src/views/admin/__tests__/AdminReportsView.spec.ts`
- Chrome DevTools walkthrough for notification after login, report thread realtime, blocked state UI.

Handoff:
Write `task-update/phase-8-frontend-supervisor/CodexCli_HANDOFF.md`.
```

## gemini - FE-6 Integration Review / Chrome QA

```text
You are gemini, the integration reviewer for VibeGraph Phase 8 frontend.

Repo: D:\Users\User\IdeaProjects\VibeGraph
Scope: review, merge gate, Chrome DevTools QA, final report.
Do not implement new product features unless fixing a merge blocker.
Do not push/merge unless supervisor explicitly approves.

Read first:
- AGENTS.md
- task-update/phase-8-frontend-supervisor/README.md
- all Phase 8 worker handoffs that exist

Review order:
1. CladueCli API/types/stores
2. ClaudeChat user UI
3. Droid admin overview/charts
4. Kiro admin ops/security/system
5. CodexCli reports/notifications/realtime

For each worker:
- Inspect diff.
- Check file scope.
- Compare handoff with actual diff.
- Flag overlapping file ownership.
- If two workers touched the same file, verify edits are safe and propose merge order.
- Run focused tests.
- Check no app-code mocks, no hardcoded secrets, no JWT localStorage regression.

Final FE gate:
- `cd vibegraph-web && npm run type-check`
- `cd vibegraph-web && npm run test:unit -- --run`
- `cd vibegraph-web && npm run build`
- `git diff --check`
- `git status --short --branch`

Chrome DevTools QA:
- login user/admin
- user overview, repositories, API keys, usage, subscription, reports, notifications, settings
- admin overview, users, plans/credits, security, system, announcements, audit
- sidebar expanded/collapsed at 320px, 768px, 1024px, 1440px
- chart labels not clipped/overlapping
- no console errors
- disabled features visibly disabled and non-interactive

Output:
Write `task-update/phase-8-frontend-supervisor/FE-6_FINAL_REVIEW.md` with PASS/REQUEST CHANGES, findings by severity, tests run, screenshots/QA notes, and final frontend contract summary.
```
