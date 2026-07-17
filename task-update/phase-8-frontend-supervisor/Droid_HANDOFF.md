# Droid FE-3 Handoff: Admin Overview / Charts

## Files Changed

- `src/main/java/com/vibegraph/auth/dto/AdminOverviewResponse.java`
- `src/main/java/com/vibegraph/auth/service/AdminService.java`
- `src/main/java/com/vibegraph/auth/service/OnlineUserHistoryService.java`
- `src/test/java/com/vibegraph/auth/service/AdminServiceTest.java`
- `src/test/java/com/vibegraph/auth/service/AdminOverviewAggregateTest.java`
- `src/test/java/com/vibegraph/auth/service/OnlineUserHistoryServiceTest.java`
- `src/test/java/com/vibegraph/auth/web/AdminOverviewControllerTest.java`
- `vibegraph-web/src/types/api.ts`
- `vibegraph-web/src/views/admin/DashboardView.vue`
- `vibegraph-web/src/views/admin/dashboard-chart-utils.ts`
- `vibegraph-web/src/views/admin/__tests__/DashboardView.spec.ts`
- `task-update/phase-8-frontend-supervisor/Droid_HANDOFF.md`

The backend synchronization work was added after explicit user approval. No chart dependency was added.

## Chart Behavior Implemented

- Uses the existing `echarts` and `vue-echarts` dependencies with real data from `adminStore.fetchOverview()` (`GET /api/admin/overview`).
- Groups Total Users, Online Users, Credit Consumption, and System Storage into one responsive analytics section.
- Keeps Imported Repositories and Security Alerts in the summary metrics.
- Renders Top Storage Projects and Plan Distribution as ECharts horizontal bar charts.
- Keeps Plan Distribution and Security / Abuse Alerts as full-width sections.
- Month mode contains all 12 month buckets. The mobile axis shows labels `1` through `12` to keep every month readable.
- Year mode contains the latest 5 calendar years.
- Online Users:
  - receives a shared `onlineUserHistory` series from `GET /api/admin/overview`;
  - samples the server-wide active-user count every 30 seconds;
  - shows only the latest 10 one-minute buckets;
  - formats labels as 24-hour `HH:mm`;
  - keeps one server sample per minute and replaces the current minute;
  - no longer reads or writes browser `sessionStorage`;
  - returns the same history to every admin connected to the same backend instance;
  - does not backfill time before the first observed sample;
  - uses a dynamic readable y-axis with a zero baseline.
- Polls every 30 seconds only while `document.visibilityState === 'visible'`; polling stops when hidden and refreshes immediately when visible again.
- Donut labels are kept inside the chart surface through a centered donut and bottom scrolling legend.

## Chrome DevTools QA

Verified the live app at `http://localhost:5173/admin` through the Chrome DevTools Protocol against the real local backend:

- `POST /api/auth/login`: successful
- `GET /api/admin/overview`: HTTP 200
- Console errors: 0
- Horizontal page/content overflow: none at 1440, 1024, 768, or 320
- Six ECharts canvases rendered at every viewport
- Month labels, chart controls, titles, donut, wide plan chart, and alert rows had no observed clipping or overlap
- Two isolated Chrome admin sessions received byte-equivalent `onlineUserHistory` arrays with zero console errors

Screenshots:

- `qa-artifacts/fe3-admin-overview/admin-overview-1440-top.png`
- `qa-artifacts/fe3-admin-overview/admin-overview-1440-charts.png`
- `qa-artifacts/fe3-admin-overview/admin-overview-1440-support.png`
- `qa-artifacts/fe3-admin-overview/admin-overview-1024-top.png`
- `qa-artifacts/fe3-admin-overview/admin-overview-1024-charts.png`
- `qa-artifacts/fe3-admin-overview/admin-overview-1024-support.png`
- `qa-artifacts/fe3-admin-overview/admin-overview-768-top.png`
- `qa-artifacts/fe3-admin-overview/admin-overview-768-charts.png`
- `qa-artifacts/fe3-admin-overview/admin-overview-768-support.png`
- `qa-artifacts/fe3-admin-overview/admin-overview-320-top.png`
- `qa-artifacts/fe3-admin-overview/admin-overview-320-charts.png`
- `qa-artifacts/fe3-admin-overview/admin-overview-320-support.png`
- `qa-artifacts/fe3-admin-overview/sync-admin-a.png`
- `qa-artifacts/fe3-admin-overview/sync-admin-b.png`
- Final QA metrics: `qa-artifacts/fe3-admin-overview/chrome-qa-final.json`
- Two-admin response comparison: `qa-artifacts/fe3-admin-overview/two-admin-sync.json`

## Tests and Validation

- `cd vibegraph-web && npm run type-check`
  - PASS, exit code 0
- `cd vibegraph-web && npm run test:unit -- --run src/views/admin/__tests__/DashboardView.spec.ts`
  - PASS, 1 file and 5 tests
- `cd vibegraph-web && npm run test:unit -- --run`
  - PASS, 53 files and 428 tests
- Changed-file ESLint check
  - PASS, exit code 0
- `cd vibegraph-web && npm run build`
  - PASS, type-check and production bundle built successfully
  - Existing chunk-size warning remains; no build error
- `mvnw.cmd -q -Dtest=OnlineUserHistoryServiceTest,AdminServiceTest,AdminOverviewAggregateTest,AdminOverviewControllerTest test`
  - PASS
- `mvnw.cmd verify`
  - PASS, backend unit/integration tests and JaCoCo coverage gate
- IDE diagnostics for synchronization-specific new/changed files
  - 0 errors and 0 warnings

## Review and Open Blockers

- GitNexus impact analysis reported LOW risk for `getActiveUsersCount` and removing local `captureOnlineSample`.
- The GitNexus index refresh failed with its local VECTOR/ANY binding error; the existing index still resolved the changed symbols.
- `CladueCli_HANDOFF.md` was not present when work started.
- Online history is shared for all admins on the current single backend instance and resets on backend restart. A future multi-instance deployment would require a shared Redis/database sampler because active-user tracking is also currently JVM-local.
- No product blocker remains for FE-3.
- No commit or push was performed.
