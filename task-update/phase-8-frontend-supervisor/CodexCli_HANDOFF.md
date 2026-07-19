# CodexCli FE-5 Handoff

Date: 2026-07-17
Scope: Reports, notifications, announcements delivery, realtime, blocked/deactivated UX

## Files Changed

- `vibegraph-web/src/components/layouts/UserLayout.vue`
- `vibegraph-web/src/components/notifications/AnnouncementBanner.vue`
- `vibegraph-web/src/lib/api.ts`
- `vibegraph-web/src/stores/account.ts`
- `vibegraph-web/src/stores/admin.ts`
- `vibegraph-web/src/types/api.ts`
- `vibegraph-web/src/views/user/NotificationsView.vue`
- `vibegraph-web/src/views/user/ReportsView.vue`
- `vibegraph-web/src/views/admin/AdminReportsView.vue`
- `vibegraph-web/src/views/admin/AnnouncementsView.vue`
- `vibegraph-web/src/views/user/__tests__/ReportsView.spec.ts`
- `vibegraph-web/src/views/admin/__tests__/AdminReportsView.spec.ts`

Some shared files above also contain concurrent Phase 8 work from other frontend agents. No unrelated changes were reverted.

## Notifications And Announcements

- User layout loads the newest unread active notification after login/app load.
- Highlighted banner has backend-backed `Read` and `Close` actions.
- `Read` calls `PATCH /api/account/notifications/{id}/read`, opens Notifications, and hides the banner.
- `Close` calls `PATCH /api/account/notifications/{id}/dismiss`; no announcement state uses localStorage.
- Notifications are sorted newest first. Detail shows creator, date, type, severity, title, and body.
- Notifications detail marks unread items as read and supports backend dismissal.
- Admin announcements now support create, edit, disable, and delete with confirm dialogs.
- Duplicate normalized titles are rejected in the admin composer before submission.
- Admin form supports type, severity, target, schedule, dismissible, and active state.

## Reports And Realtime

- User and admin report list/detail/thread flows use real backend APIs.
- User can reply and close a report; admin can reply and close a report.
- Closed report UI displays the backend retention/delete-after date.
- Missing/null event timestamps display `Just now` instead of the Unix epoch.
- User and admin mobile reply boxes use `position: sticky; bottom: 0` below 700 px.
- Realtime handlers reject events whose `reportId` is not the currently selected report.
- Backend STOMP subscription authorization independently checks report ownership/admin access.
- No JWT was added to localStorage. The current HttpOnly cookie handshake successfully authenticates SockJS/STOMP, so no realtime auth blocker remains.

## Blocked And Disabled UX

- User layout polls `/api/account/session-state` every 10 seconds and revalidates on window focus.
- Blocked/deactivated state shows only the backend safe reason.
- Product navigation becomes disabled quickly; direct product routes render a non-interactive restricted state.
- Reports remain available as the support path, matching backend restricted-route policy.
- Feature-disabled controls remain visibly disabled with an explanatory reason.

## Chrome DevTools QA

- Rebuilt the local backend container from current source because the old image returned 404 for the notification endpoint.
- Admin created a USER announcement; user login displayed the highlighted banner.
- `Read` opened notification detail and produced notification read PATCH 200.
- Detail showed creator, created date, title, and body; `Dismiss` removed it through the backend.
- Opened the same report in isolated user/admin browser contexts. Both showed `Live`.
- Admin reply appeared in the user thread without reload.
- Mobile viewport `390x844` reported reply box computed style `position: sticky`, `bottom: 0px`.
- Admin blocked the active user with a safe reason. User polling disabled product links and preserved Reports without refresh.
- Direct `/projects` navigation while blocked showed the restricted product state and support link.
- User was unblocked after QA; the test announcement was deleted; the QA report was closed.
- API key creation was visibly disabled and non-interactive with the capability-contract reason.
- Chrome console check: no errors or warnings.

## Verification

```text
cd vibegraph-web && npm run type-check
PASS - vue-tsc --build, exit 0

cd vibegraph-web && npm run test:unit -- --run src/views/user/__tests__/ReportsView.spec.ts src/views/admin/__tests__/AdminReportsView.spec.ts
PASS - 2 files, 6 tests, exit 0

npx eslint <FE-5 changed frontend files>
PASS - exit 0

git diff --check <FE-5 changed frontend files>
PASS - exit 0
```

## Confirmed Blockers And Risks

- Feature availability contract: frontend currently requests `/api/session-state`, but the backend exposes `/api/account/session-state` without a `features` map. User feature controls therefore use the existing explicit unavailable/compatibility states. A backend capabilities payload is required for live user-facing flag propagation.
- GitNexus global `detect-changes` reported CRITICAL because the shared worktree contains 41 files / 219 symbols / 75 flows from all concurrent Phase 8 agents. Pre-edit FE-5 symbol impact checks were LOW; the global result cannot isolate this agent's shared-file hunks.
- The Chrome QA report remains as a closed report until backend retention cleanup. No backend files were edited.

## Constraints Honored

- No commit, push, or merge.
- No backend source edits.
- No JWT or auth secret stored in localStorage.
