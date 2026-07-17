# Phase 8 Frontend Final Review (FE-6)

**Verdict: REQUEST CHANGES**

Reviewed on 2026-07-17 against the current `poc` working-tree snapshot. No product code was changed by this reviewer. No commit, push, or merge was performed.

## Gate Results

| Gate | Result |
| --- | --- |
| `npm run type-check` | PASS |
| `npm run test:unit -- --run` | PASS - 53 files, 428 tests |
| `npm run build` | PASS - existing chunk-size warning only |
| `git diff --check` | PASS |
| Diagnostics on 11 high-risk files | PASS - no diagnostics |
| Chrome DevTools QA | BLOCKED - shared Chrome profile was already locked by another browser process |

The Chrome MCP could not attach or create a page because `C:\Users\User\.cache\chrome-devtools-mcp\chrome-profile` was in use. Consequently, live login, all required user/admin route walkthroughs, responsive visual validation, and console verification must be repeated after the existing browser session is released.

## Important Findings

1. **HIGH - Admin-disabled import capabilities can still be enabled in user UI.**
   - `vibegraph-web/src/lib/featureAvailability.ts:20` treats local/archive/GitHub import as enabled when `/api/account/session-state` has no `features` field.
   - The current session-state DTO has no capability map, so `vibegraph-web/src/views/user/ProjectsView.vue` can expose active import controls even when an admin disabled them.
   - Required resolution: expose a safe authenticated feature-capability DTO and consume it, or fail closed for every controlled import action. Do not present an admin-controlled feature as available without a contract.

2. **HIGH - Repository-bound API-key creation is not deliverable with the current backend contract.**
   - `vibegraph-web/src/views/user/ApiKeysView.vue:20` intentionally makes creation permanently disabled.
   - `vibegraph-web/src/lib/api.ts` posts only `{ name }`; no project binding is present in API-key DTOs.
   - This accurately avoids a false integration, but does not meet the Phase 8 requirement that a user select a repository/project and create a project-identity key. Backend contract work or an explicit scope change is required before approval.

3. **HIGH - Report realtime subscriptions are lost after a reconnect.**
   - `vibegraph-web/src/composables/useWebSocket.ts:80` clears subscriptions after the first `CONNECTED` frame.
   - The STOMP client has a reconnect delay, but `onWebSocketClose` does not preserve/replay active subscriptions; `useReportRealtime` only resubscribes when the selected report changes.
   - A transient transport failure can return the UI to `Live` without receiving future report messages or close events. Persist desired subscriptions and replay them on each connection; add a reconnect regression test.

4. **MEDIUM - User usage/profile/subscription mounts can reject without an error UI.**
   - `vibegraph-web/src/views/user/UsageView.vue:38`, `vibegraph-web/src/views/user/SubscriptionView.vue:30`, and `vibegraph-web/src/views/user/ProfileView.vue:18` await API calls in lifecycle hooks without local error handling.
   - A failed account request can create an unhandled rejection and leave an indefinite loading state. Add screen-level error/retry states and rejection tests.

5. **MEDIUM - Announcement loading failure is not rendered.**
   - `vibegraph-web/src/components/notifications/AnnouncementBanner.vue:14` sets `errorMsg`, but its template is gated by `v-if="notification"`.
   - If the initial notification request fails, no notice or recovery UI is visible. Render a retry-capable error outside the notification gate or remove the dead state.

6. **MEDIUM - Security IP-block mutations can report failure after the write succeeds.**
   - `vibegraph-web/src/stores/admin.ts:408` writes an IP block, then calls all-or-nothing `refreshSecurity()`.
   - If an unrelated telemetry endpoint is unavailable, refresh throws and `SecurityView` displays a save failure even though the IP-block mutation succeeded. Refresh panels independently or refresh only the affected policy collection after the write.

7. **MEDIUM - Nested `main` landmarks in notifications view.**
   - `vibegraph-web/src/components/layouts/UserLayout.vue:215` owns the application `main`; `vibegraph-web/src/views/user/NotificationsView.vue:83` adds another nested `main`.
   - Replace the child root with a `section` or `div` to preserve a single main landmark.

## Contract / Backend Blockers

- `GET /api/account/session-state` does not expose safe per-session feature capabilities, preventing truthful propagation of admin feature flags to user controls.
- Account and admin API-key creation are name-only; they lack project binding and a project list suitable for user/admin key assignment.
- Admin plan responses omit fields needed for lossless edit persistence (`active`, `sortOrder`) according to the FE-4 handoff.
- Backend uniqueness enforcement for normalized announcement titles is absent; the UI-only duplicate check cannot handle concurrent admins.

## Worker Scope and Overlap

- **FE-1 CladueCli:** API types/client and account/admin Pinia stores. Scope mostly aligned; overlaps FE-2/FE-4/FE-5 in `api.ts`, `types/api.ts`, `account.ts`, and `admin.ts`.
- **FE-2 ClaudeChat:** user layout, login/user pages, quota UI. Scope aligned except its requirement for project-bound key creation remains blocked by backend contract.
- **FE-3 Droid:** dashboard and ECharts behavior. Scope aligned; uses existing `echarts`/`vue-echarts` with no competing chart package.
- **FE-4 Kiro:** admin shell, users/plans/flags/security/audit/settings. Scope aligned; shared API/store overlap requires the FE-1 changes to merge first.
- **FE-5 CodexCli:** reports, notifications, announcements, and restricted UX. Scope aligned; overlaps UserLayout/API/stores and contains the reconnect blocker above.

Current worktree scope is 44 tracked frontend files changed plus untracked Phase 8 test/view/utility files. Suggested integration order remains FE-1 -> FE-2 -> FE-3 -> FE-4 -> FE-5, with shared files reconciled rather than overwritten.

## Security / Integrity Review

- No new JWT storage was found. Browser fetches use `credentials: 'include'`; API auth headers remain empty, and stale `vg_token` cleanup is removal-only.
- No hardcoded password, token, secret, or API-key literal was found in changed app source.
- No new app-code mock business data or `Math.random` behavior was found in Phase 8 surfaces.
- No browser `alert`, `confirm`, or `prompt` calls were found; destructive flows use the app confirmation dialog.
- Disabled API-key controls are visibly non-interactive; import controls are the exception described in Finding 1.

## Final Frontend Contract Summary

The frontend is correctly cookie-authenticated and has typed APIs for account notifications, admin security/IP blocks, audit, reports, and announcements. User/admin UI consumes real APIs rather than app-code mocks. Approval is blocked by the missing feature-capability and project-bound-key backend contracts, realtime reconnect data loss, and the listed error/interaction defects.

## Safe To Commit / Push

**No.** Do not commit, push, or merge this Phase 8 frontend snapshot until the HIGH findings are resolved or the relevant product requirements are explicitly re-scoped, all MEDIUM findings are addressed or accepted by the supervisor, and the complete Chrome QA matrix passes after the browser-profile conflict is cleared.
