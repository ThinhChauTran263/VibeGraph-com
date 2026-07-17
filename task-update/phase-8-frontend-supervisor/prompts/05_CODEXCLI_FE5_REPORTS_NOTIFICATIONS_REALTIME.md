# CodexCli - FE-5 Reports / Notifications / Realtime / Blocked UX

You are `CodexCli`, working on VibeGraph Phase 8 frontend.

Repo: `D:\Users\User\IdeaProjects\VibeGraph`
Scope: Reports, notifications, announcements user delivery, realtime, blocked/deactivated UX polish.

Do not commit, push, or merge.
Do not edit backend.

Read first:
- `AGENTS.md`
- `task-update/phase-8-frontend-supervisor/README.md`
- `task-update/phase-8-frontend-supervisor/CladueCli_HANDOFF.md` if available

Goal:
Finish user/admin communications and immediate account/system state feedback.

Allowed primary files:
- `vibegraph-web/src/views/user/ReportsView.vue`
- `vibegraph-web/src/views/user/NotificationsView.vue`
- `vibegraph-web/src/views/admin/AdminReportsView.vue`
- `vibegraph-web/src/views/admin/AnnouncementsView.vue`
- realtime/STOMP client files
- blocked state UI components
- focused report/notification/realtime tests

Do not modify admin users/plans/security/system pages except shared state compatibility.

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
- Realtime:
  - send JWT in STOMP native connect header as `Authorization: Bearer <JWT>` only if the current frontend auth flow exposes a safe token source
  - if HttpOnly cookie means JWT is not readable, document backend/frontend transport mismatch instead of reintroducing localStorage JWT
  - do not leak other users' report events
- Blocked/deactivated UX:
  - if account becomes blocked/deactivated, product controls become unavailable quickly
  - show safe reason and support/report path
  - do not require manual refresh where polling/revalidation exists
- Feature disabled UX:
  - controls visibly disabled and explain why

Acceptance:
- Notifications are backend-backed.
- Reports realtime works for user/admin or a precise backend auth mismatch is documented.
- Blocked/deactivated copy is safe and clear.
- No browser default popup UX.
- No console errors.

Required verification:
- `cd vibegraph-web && npm run type-check`
- `cd vibegraph-web && npm run test:unit -- --run src/views/user/__tests__/ReportsView.spec.ts src/views/admin/__tests__/AdminReportsView.spec.ts`
- Chrome DevTools walkthrough for:
  - notification after login
  - report thread realtime
  - blocked state UI

Handoff:
Write `task-update/phase-8-frontend-supervisor/CodexCli_HANDOFF.md` with:
- files changed
- notifications/reports/realtime behavior
- Chrome QA notes
- tests run and exact result
- confirmed blockers, if any
