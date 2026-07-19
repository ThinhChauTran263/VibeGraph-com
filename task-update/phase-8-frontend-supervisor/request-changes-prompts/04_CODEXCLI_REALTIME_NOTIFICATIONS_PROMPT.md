# CodexCli - Reports Reconnect And Notifications Fixes

You are `CodexCli`, fixing FE-6 reports/notifications request changes.

Repo: `D:\Users\User\IdeaProjects\VibeGraph`

Read first:
- `AGENTS.md`
- `task-update/phase-8-frontend-supervisor/FE-6_FINAL_REVIEW.md`
- `task-update/phase-8-frontend-supervisor/request-changes-prompts/00_AGENT_AUTONOMY_AND_OVERLAP_PROTOCOL.md`

Autonomy:
- Do not stop to ask `yes` for local FE edits, tests, or overlap with other agents.
- If another agent changed websocket/report/notification files, read and merge carefully.
- Do not commit, push, merge, delete files, or revert another agent's work.

Scope:
- reports
- notifications
- announcement banner
- websocket/realtime composables
- focused tests
- Do not edit backend.

Fix:
1. STOMP report subscriptions are lost after reconnect:
   - Persist desired subscriptions.
   - Replay them on every successful reconnect.
   - Do not show `Live` if subscriptions are not active.
   - Add reconnect regression test.

2. Announcement loading failure is not rendered:
   - Render retry-capable error UI outside `v-if="notification"`.
   - Do not use browser alert/confirm.
   - Add test.

3. Notifications view has nested `<main>`:
   - Replace child `main` with `section` or `div`.
   - Preserve accessibility landmarks.

4. Blocked/deactivated and feature-disabled messaging:
   - Ensure reports/support path remains usable where allowed.
   - Disabled controls show safe reason and do not silently no-op.

Run:
- `cd vibegraph-web && npm run type-check`
- `cd vibegraph-web && npm run test:unit -- --run src/views/user/__tests__/ReportsView.spec.ts src/views/admin/__tests__/AdminReportsView.spec.ts src/components/notifications src/composables`
- `cd vibegraph-web && npm run build`
- `git diff --check`

Chrome QA:
- notification banner success/error/retry
- notifications list/detail
- user report thread
- admin report thread
- realtime reconnect behavior if practical
- mobile sticky reply box

Handoff:
Write `task-update/phase-8-frontend-supervisor/CodexCli_REQUEST_CHANGES_HANDOFF.md` with:
- files changed
- tests exact result
- Chrome QA notes
- overlap handled
- remaining blockers
