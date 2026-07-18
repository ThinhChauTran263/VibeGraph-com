# CodexCli Request Changes Handoff

Date: 2026-07-18

Scope: reports, notifications, announcement banner, STOMP/WebSocket composables, and focused frontend tests. No backend files were changed. No commit, push, or merge was performed.

## Outcome

- Persisted desired STOMP subscriptions for the lifetime of each subscription handle.
- Replayed desired subscriptions after every successful STOMP reconnect.
- Added reactive subscription `active` state and cleared it on transport close, unsubscribe, and disconnect.
- Updated user and admin report threads to show `Live` only when the report topic subscription is active; a connected transport with an inactive subscription shows `Syncing`.
- Rendered announcement loading errors even when there is no notification and added an inline retry action.
- Replaced the nested notifications `<main>` with a labelled `<section>` while preserving the layout-owned main landmark.
- Verified blocked/deactivated accounts retain access to Reports/support while disabled navigation items expose `aria-disabled` and a safe reason.
- Confirmed there are no browser `alert`, `confirm`, or `prompt` calls in the changed scope.

## Files Changed

- `vibegraph-web/src/composables/useWebSocket.ts`
- `vibegraph-web/src/composables/useReportRealtime.ts`
- `vibegraph-web/src/composables/__tests__/useWebSocket.spec.ts`
- `vibegraph-web/src/composables/__tests__/useArchiveImport.spec.ts`
- `vibegraph-web/src/composables/__tests__/useGitHubImport.spec.ts`
- `vibegraph-web/src/components/notifications/AnnouncementBanner.vue`
- `vibegraph-web/src/components/notifications/__tests__/AnnouncementBanner.spec.ts`
- `vibegraph-web/src/views/user/NotificationsView.vue`
- `vibegraph-web/src/views/user/ReportsView.vue`
- `vibegraph-web/src/views/user/__tests__/ReportsView.spec.ts`
- `vibegraph-web/src/views/admin/AdminReportsView.vue`
- `vibegraph-web/src/views/admin/__tests__/AdminReportsView.spec.ts`
- `vibegraph-web/src/components/layouts/__tests__/UserLayout.spec.ts`

## TDD Evidence

RED command:

```powershell
npm --prefix vibegraph-web run test:unit -- --run src/views/user/__tests__/ReportsView.spec.ts src/views/admin/__tests__/AdminReportsView.spec.ts src/components/notifications src/composables
```

RED result: 4 intended failures, 84 passing tests.

- Reconnect did not restore `/topic/reports/report-1`; the second event was not delivered.
- Announcement request failure rendered no `[role="alert"]` because the template was gated by `notification`.
- User report thread incorrectly displayed `Live` while its subscription was inactive.
- Admin report thread incorrectly displayed `Live` while its subscription was inactive.

GREEN result for the same command: 11 test files passed, 88 tests passed.

The prompt prohibits commits, so no RED/GREEN checkpoint commits were created. This handoff preserves the evidence instead.

## Verification

| Command | Result |
| --- | --- |
| `npm --prefix vibegraph-web run type-check` | PASS |
| Focused reports/notifications/composables unit command | PASS - 11 files, 88 tests |
| `npm --prefix vibegraph-web run test:unit -- --run src/components/layouts/__tests__/UserLayout.spec.ts` | PASS - 1 file, 5 tests |
| `npm --prefix vibegraph-web run build` | PASS - existing chunk-size warning only |
| `git diff --check` | PASS |
| GitNexus `detect-changes --scope unstaged` | PASS - LOW risk, 0 affected processes |

## GitNexus Impact

- `useWebSocket`: MEDIUM risk, 5 direct consumers and 12 impacted symbols. Direct consumers are archive import, GitHub import, local import, graph realtime, and report realtime.
- `useReportRealtime`: LOW risk, 2 direct consumers (user and admin reports).
- Announcement loading and report realtime label changes: LOW risk.
- The archive and GitHub import test doubles were updated for the additive `TopicSubscription.active` contract; production import behavior was not changed.

## Overlap Handled

Concurrent changes appeared during this task in:

- `vibegraph-web/src/stores/admin.ts`
- `vibegraph-web/src/stores/__tests__/admin.spec.ts`
- `vibegraph-web/src/views/admin/FeatureFlagsView.vue`
- `vibegraph-web/src/views/admin/SecurityView.vue`
- `vibegraph-web/src/views/admin/__tests__/AdminOpsViews.spec.ts`

Those changes were preserved and not edited by CodexCli. The shared build and type-check passed with both change sets present. CodexCli only changed the report-specific sections of `AdminReportsView.vue` and `AdminReportsView.spec.ts`.

## Chrome QA

Not run in this task. The requested CLI verification gates passed; browser walkthroughs remain for the final frontend re-review.

## Remaining Blockers / Safety

- No blocker remains in the CodexCli reports/notifications scope based on unit, type, and build verification.
- The full Phase 8 worktree still contains concurrent agent changes and requires the scheduled final supervisor re-review and Chrome QA.
- Safe to commit/push: **No for the combined worktree** until changes are separated/reconciled and the final re-review passes. No commit or remote action was performed.
