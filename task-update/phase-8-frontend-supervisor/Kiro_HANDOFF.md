# Kiro FE-4 Handoff - Admin Ops / Security / System

## Status

FE-4 frontend implementation is complete in the shared `poc` worktree. No backend files were edited. No commit, push, or merge was performed.

## Files Changed

- `vibegraph-web/src/components/layouts/AdminLayout.vue`
- `vibegraph-web/src/components/ui/AppIcon.vue`
- `vibegraph-web/src/lib/api.ts`
- `vibegraph-web/src/lib/featureAvailability.ts`
- `vibegraph-web/src/router/index.ts`
- `vibegraph-web/src/stores/admin.ts`
- `vibegraph-web/src/stores/__tests__/admin.spec.ts`
- `vibegraph-web/src/types/api.ts`
- `vibegraph-web/src/views/admin/UsersTableView.vue`
- `vibegraph-web/src/views/admin/UserDetailDrawer.vue`
- `vibegraph-web/src/views/admin/PlansCreditsView.vue`
- `vibegraph-web/src/views/admin/FeatureFlagsView.vue`
- `vibegraph-web/src/views/admin/SecurityView.vue`
- `vibegraph-web/src/views/admin/AuditView.vue` (new)
- `vibegraph-web/src/views/admin/SettingsView.vue`
- `vibegraph-web/src/views/admin/__tests__/AdminOpsViews.spec.ts` (new)

`UserDetailDrawer.vue` retains its historical filename for compatibility, but now renders as an inline/full-width panel inside the Users surface rather than a cramped right drawer.

## Admin Flows Completed

- Admin shell uses the shared collapsible navigation pattern, admin-only items, a dedicated Audit item/icon, mobile overlay navigation, and non-overlapping collapsed labels/icons.
- Users supports live search/status/plan filters, pagination, catalog-backed supported plan options, inline detail, immediate visible refresh, safe block/deactivate reasons, unblock, plan update, storage and credit quota overrides, real credit overview/adjustment, API-key creation control, specific key disable, and one-time secret copy handling.
- Plans & Credits uses real CRUD endpoints. Plan storage is MB end-to-end (`storageLimitMb`), pricing supports decimals, both editors have reset actions, and contact-sales/active toggles use designed controls.
- System exposes canonical controls for registration, local/archive/GitHub imports, CLI push, API-key creation, project analyze, use-case generation, the global MCP switch, and every actual `@Tool(name=...)` child callback. Groups collapse to title-only state and malformed persisted collapse state is rejected safely.
- Security connects request events, top users, top IPs, security events, and IP-block create/update/delete APIs. Missing capabilities load independently so one unavailable endpoint does not erase the other panels.
- Audit adds route/sidebar/view for paged filters, row detail, and retention get/update.
- Admin Settings uses the shared real profile/password endpoints. The stale disabled audit-retention placeholder was removed because retention now belongs to Audit.
- No admin view uses browser `alert()` or `confirm()`; destructive operations use designed dialogs.

## Chrome DevTools QA

Tested against `http://localhost:5173` with the documented local admin account and a 1440px desktop viewport plus a 375x812 mobile viewport.

- Users: `GET /api/admin/users` 200, `GET /api/admin/plans` 200; responsive cards/table and pagination verified.
- Users detail: inline panel verified at 375px; `GET /api/admin/api-keys?userId=...` 200 and `GET /api/admin/credits/users/{id}` 200. Quota, credit metrics, adjustment form, key controls, and safe account actions fit without a narrow drawer.
- Plans & Credits: plans and pricing endpoints 200; real values displayed as 100/500/1,024/2,048 MB; balanced forms, reset buttons, and Enterprise contact-sales state verified at 1440px.
- System: feature-flag endpoint 200; canonical global keys and MCP global/child controls verified. Expanded and collapsed groups were checked; collapsed groups expose title only. No console warnings/errors.
- Settings: profile endpoint 200; admin profile and current/new/confirm password forms verified. No console warnings/errors.
- Security: UI, responsive layout, request table, ranking panels, and IP policy editor/list verified. The currently running backend image returns 404 for request-events/top-users/top-ips/ip-blocks; source controllers exist, so the image must be rebuilt.
- Audit: route, filters, detail surface, pagination, and retention form verified. The currently running backend image returns 404 for audit list/retention; source controller exists, so the image must be rebuilt.
- Screenshots were captured through Chrome DevTools for Users detail (mobile), Plans & Credits (desktop), and System expanded/collapsed. The DevTools tool attached captures but denied writing image files outside its configured roots.

## Verification

- `npm run test:unit -- --run src/views/admin src/stores/__tests__/admin.spec.ts --reporter=verbose`
  - PASS: 6 files, 33 tests, 0 failures.
- IDE diagnostics on all FE-4 files
  - PASS: no diagnostics.
- `npm run build-only`
  - PASS: 2,978 modules transformed; production bundle generated.
  - Existing warning: several shared graph/chart chunks exceed 500 kB; unrelated to FE-4 route chunks.
- `npm run type-check`
  - BLOCKED by unrelated concurrent user/auth tests:
    - `src/views/__tests__/LoginView.spec.ts`: one invalid `wrapper.get(...).exists()` assertion.
    - `src/views/user/__tests__/ProfileView.spec.ts`: three invalid `wrapper.get(...).exists()` assertions.
  - FE-4 files have no diagnostics and the required focused tests pass.
- `npm run build`
  - Bundle generation passes, but the script exits non-zero because it runs the same global type-check in parallel.
- `git diff --check`
  - PASS: no whitespace errors.
- Security scan
  - No hardcoded credentials/tokens in FE-4 source.
  - No `v-html`/`innerHTML` usage in admin views.
  - No `console.log`/`debugger` in admin views.
- GitNexus detect changes
  - Reported CRITICAL aggregate risk across 25 changed files / 75 affected symbols because the shared worktree contains concurrent FE-1/FE-2/FE-3/FE-5 edits. FE-4-specific pre-edit impacts for `adminApi`, `useAdminStore`, router, icon map, and plan formatting were LOW; Vue SFC names were not indexed.

## Open Blockers

1. Rebuild/restart the backend container from current Phase 7 source. The running image exposes Users/Plans/Flags/Credits but returns 404 for Security abuse/IP-block and Audit endpoints that exist in source.
2. Feature propagation to user UI is not fully possible with the current backend contract. `GET /api/account/session-state` returns account status only and no feature capability map. `featureAvailability.ts` cannot truthfully consume admin flags until the backend exposes a safe per-session capability DTO. No backend change was made due scope constraints.
3. Plan edit cannot preserve backend `active` and `sortOrder` because `AdminPlanResponse` omits both while `AdminPlanUpsertRequest` requires them. The existing UI sends active plus current display order; backend should return these fields to make edits lossless.
4. Admin API-key creation backend accepts only `{ userId, name }`; it has no `projectId` field and no admin project-list-by-user endpoint. The frontend can create and disable specific keys according to the current contract, but cannot force project selection without backend work.
5. Global type-check/build gate needs the unrelated `LoginView.spec.ts` and `ProfileView.spec.ts` assertion fixes noted above.

## Review Notes

An independent FE-4 review found no critical issue. High findings for Admin User MB DTO mismatch, MCP tool-key mismatch, security partial loading, and malformed collapse storage were fixed. Remaining backend-contract findings are documented as blockers above.
