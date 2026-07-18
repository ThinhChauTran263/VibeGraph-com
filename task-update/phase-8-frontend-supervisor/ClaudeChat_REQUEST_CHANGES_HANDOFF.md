# ClaudeChat Request-Changes Handoff

## Status

**COMPLETE** for the ClaudeChat user-side Phase 8 scope.

No commit, push, or merge was performed. The worktree remains dirty because other Phase 8 agents have unrelated changes; those changes were preserved.

## Files changed in this pass

Production code reviewed/fixed:

- `vibegraph-web/src/components/admin/AdminConfirmDialog.vue`
- `vibegraph-web/src/components/layouts/UserLayout.vue`
- `vibegraph-web/src/components/projects/ImportProjectPanel.vue` (reviewed; no additional edit in this pass)
- `vibegraph-web/src/components/notifications/AnnouncementBanner.vue` (reviewed; no additional edit in this pass)
- `vibegraph-web/src/composables/useWebSocket.ts`
- `vibegraph-web/src/composables/useReportRealtime.ts` (reviewed; no additional edit in this pass)
- `vibegraph-web/src/stores/account.ts` (reviewed; no additional edit in this pass)
- `vibegraph-web/src/views/user/ApiKeysView.vue`
- `vibegraph-web/src/views/user/NotificationsView.vue`
- `vibegraph-web/src/views/user/ProfileView.vue`
- `vibegraph-web/src/views/user/ProjectsView.vue`
- `vibegraph-web/src/views/user/ReportsView.vue`
- `vibegraph-web/src/views/user/SubscriptionView.vue`
- `vibegraph-web/src/views/user/UsageView.vue`

Tests changed/added in the current user-side scope:

- `vibegraph-web/src/components/layouts/__tests__/UserLayout.spec.ts`
- `vibegraph-web/src/composables/__tests__/useWebSocket.spec.ts`
- `vibegraph-web/src/views/user/__tests__/ApiKeysView.spec.ts`
- `vibegraph-web/src/views/user/__tests__/ProfileView.spec.ts`
- `vibegraph-web/src/views/user/__tests__/ProjectsView.spec.ts`
- `vibegraph-web/src/views/user/__tests__/ReportsView.spec.ts`
- `vibegraph-web/src/views/user/__tests__/SubscriptionView.spec.ts`
- `vibegraph-web/src/views/user/__tests__/UsageView.spec.ts`

`vibegraph-web/src/views/__tests__/LoginView.spec.ts` was included in the required verification scope and remained passing; it required no new production change in this pass.

## User views fixed/reviewed

- **API Keys**: project selection is required; project-bound key creation is disabled until the capability and repository refresh are both known to be available. A failed repository refresh now fails closed even if stale projects remain in Pinia. Retry/create status and one-time secret state are reset so stale errors do not contradict a successful creation.
- **Notifications**: notification selection uses a latest-selection guard so an older mark-read response cannot replace the currently selected notification.
- **Profile / Settings**: profile retry errors no longer mask a profile populated by another successful account refresh.
- **Projects / Repositories**: delete uses the captured project id across the async boundary, and the confirmation dialog cannot be cancelled by backdrop/cancel while deletion is busy. Import controls remain fail-closed when no capability is enabled.
- **Reports**: report detail selection is guarded against out-of-order requests. Reply submission captures the report id and only updates the still-selected report after the request resolves. Realtime is labelled Live only after the topic subscription is active. The shared WebSocket composable now reuses an in-flight connection promise, preventing duplicate STOMP clients during rapid report changes.
- **Subscription**: subscription retry errors no longer mask usage data populated by a concurrent account refresh; the view continues to show only backend-backed plan/quota data.
- **Usage**: usage retry errors no longer mask valid usage data populated by a concurrent account refresh; ledger failures remain independently visible with retry.
- **User layout**: product content is withheld until the account session state is verified. This prevents restricted accounts from briefly mounting product views and firing restricted API calls. Account refresh is single-flight to avoid overlapping poll/focus requests.

## Error/retry states added or hardened

- Profile/settings load error with retry.
- Subscription usage load error with retry.
- Usage load error with retry.
- Independent credit-ledger error with retry while usage remains visible.
- Existing API-key disable failure remains recoverable; API-key creation resets stale status/secret state.
- Repository list/import capability failure and repository deletion race coverage.
- Report detail out-of-order request coverage.
- User-layout account-session verification loading/fail-closed coverage.

## Capability and fail-closed behavior

- Feature availability is read from the authenticated capability contract and controls are disabled when unavailable.
- API-key creation additionally requires a successful repository refresh and at least one repository; stale Pinia projects cannot enable the control after refresh failure.
- Repository import remains disabled when all import methods are unavailable.
- Restricted/blocked/deactivated accounts receive a safe shell and support-report path only after session state is known.
- No client-side gate is treated as a replacement for backend authorization; this is UI safety and truthful affordance behavior.

## Chrome QA notes

Chrome QA was completed before this final review at the required breakpoints. The reviewed user surfaces were checked for:

- responsive layout at required desktop/mobile widths;
- no nested `<main>` elements under `UserLayout`;
- visible loading, error, retry, restricted, and capability-disabled states;
- disabled controls not appearing actionable when their capability is unavailable;
- support reports remaining reachable for restricted accounts;
- no browser `alert()`/`confirm()` flows.

The later code changes are state/race hardening changes covered by the focused unit tests; no visual layout direction was changed.

## Security/UI contract checks

- No JWT or raw session token persistence in `localStorage`/`sessionStorage` was found in the scoped user views.
- No mock business logic or fabricated plan/catalog data was added to production views.
- No browser `alert()` or `confirm()` usage was found.
- No `v-html`/`dangerouslySetInnerHTML` usage was found in the scoped user views.
- No nested `<main>` was found in the scoped user views.
- User-visible text interpolation remains Vue-escaped.

## GitNexus impact review

Impact analysis was run before edits on the relevant changed symbols. The resolved targets were LOW risk with no high/critical blast-radius warning:

- `ApiKeysView.create`: LOW, no impacted callers/processes.
- `ProjectsView.confirmDelete`: LOW, no impacted callers/processes.
- `ReportsView.selectReport`: LOW, no impacted callers/processes.
- `ReportsView.sendReply`: LOW, no impacted callers/processes.
- `NotificationsView.selectNotification`: LOW, one local `loadNotifications` caller in the User module.
- `UserLayout.refreshAccountState`: LOW, one local caller in `UserLayout.vue`.
- `useWebSocket.connect`: LOW, no impacted callers/processes reported by the current index.

New helper functions were not present in the current GitNexus index and returned `UNKNOWN` when queried by name; no HIGH/CRITICAL result was returned. No rename or broad refactor was performed.

## Exact verification results

All required gates passed after the final fixes:

```text
npm --prefix vibegraph-web run type-check
PASS — vue-tsc --build

npm --prefix vibegraph-web run test:unit -- --run src/views/user src/views/__tests__/LoginView.spec.ts src/components/layouts/__tests__/UserLayout.spec.ts src/composables/__tests__/useWebSocket.spec.ts
PASS — 9 test files, 51 tests

npm --prefix vibegraph-web run build
PASS — vue-tsc --build and vite production build
```

The production build emitted existing Vite chunk-size warnings for large generated chunks, but completed successfully.

```text
git diff --check
PASS — no whitespace errors
```

## Remaining blockers / dependencies

The frontend now consumes the intended capability/project-bound API-key contract, but final integration still depends on the backend contract being present and deployed consistently:

- authenticated account capability data must accurately reflect admin-disabled features and restricted-account fail-closed behavior;
- account API-key creation must accept and validate an owned project/repository id;
- API-key list/create responses must expose safe project binding metadata without raw secrets;
- CLI/MCP API-key validation must resolve the bound project;
- existing unbound keys must remain safely supported.

If the backend capability/project-bound API-key contract is not final or not deployed in the target environment, the UI intentionally fails closed rather than presenting controls that cannot work.

## Safety confirmation

- **No commit.**
- **No push.**
- **No merge.**
- No unrelated agent changes were reverted or overwritten.
