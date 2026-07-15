# Kiro Frontend UX Handoff

## Files changed

- `vibegraph-web/src/components/layouts/UserLayout.vue`
- `vibegraph-web/src/components/layouts/AdminLayout.vue`
- `vibegraph-web/src/components/notifications/AnnouncementBanner.vue`
- `vibegraph-web/src/components/projects/ImportProjectPanel.vue`
- `vibegraph-web/src/components/ui/AppIcon.vue`
- `vibegraph-web/src/lib/api.ts`
- `vibegraph-web/src/lib/featureAvailability.ts`
- `vibegraph-web/src/router/index.ts`
- `vibegraph-web/src/stores/account.ts`
- `vibegraph-web/src/styles/tokens.css`
- `vibegraph-web/src/types/api.ts`
- `vibegraph-web/src/views/HomeView.vue`
- `vibegraph-web/src/views/user/ProjectsView.vue`
- `vibegraph-web/src/views/user/ApiKeysView.vue`
- `vibegraph-web/src/views/user/NotificationsView.vue`
- `vibegraph-web/src/views/admin/FeatureFlagsView.vue`
- `vibegraph-web/src/views/admin/SettingsView.vue`
- `vibegraph-web/src/views/admin/SecurityView.vue`

No Workspaces, Spec Designer, Community, Referral, or Grapuco import copy was added. The existing Local folder, Archive, and GitHub import components and copy remain in place.

## Screenshots and snapshots verified

Chrome DevTools verification used `http://127.0.0.1:5174` with a local-only QA session object; no real credentials were used.

- User Overview at 1440x900: expanded sidebar, summary cards, and quick actions verified by accessibility snapshot and viewport screenshot.
- User sidebar collapsed at desktop width: icon rail, expand control, account icon, and sign-out verified by snapshot.
- Repositories at 1440x900 and 375x812: default repository surface and responsive navigation verified. Existing imports remain available in compatibility mode when the session-state endpoint is absent.
- API Keys at 1440x900: project-bound create flow is visibly disabled while the backend contract is absent; explanatory state verified.
- Admin System at 1440x900: grouped controls and `usecase.generate` verified; MCP section collapse interaction verified.
- Admin Settings at 1440x900: profile/password forms and disabled retention contract seam verified.
- Admin Security at 1440x900: live security-events surface plus disabled request monitor, exact-IP, and audit-log surfaces verified.
- Browser API calls failed under the synthetic QA token/CORS setup, so snapshots intentionally verified loading, fallback, disabled, and error states rather than authenticated backend data.

## APIs consumed or waiting for backend

Consumed now:

- `GET /api/account/profile`
- `PATCH /api/account/profile`
- `POST /api/account/password`
- `GET /api/account/usage`
- `GET /api/account/projects`
- Existing `/api/projects` list/delete/import flows
- `GET /api/account/api-keys`
- `PATCH /api/account/api-keys/{id}/disable`
- `GET /api/admin/feature-flags` and existing feature-flag mutation endpoints
- `GET /api/admin/security/events`

Typed seams waiting for backend:

- `GET /api/session-state` with feature availability and safe disabled reasons. Until it lands, existing Local/Archive/GitHub imports stay enabled in compatibility mode; new contract-dependent actions fail closed.
- Project-bound API key create/list DTOs with required `projectId` and returned `projectId`/`projectName`. Creation remains disabled until capability support is confirmed to avoid creating an unbound key.
- `GET /api/account/announcements` with active, target-filtered announcements and `creatorName`/`createdAt`; persistent dismiss endpoint is also pending. Local dismissal is implemented as fallback.
- Admin audit-retention read/update endpoint.
- Admin request-monitor endpoint.
- Admin exact-IP block/watchlist endpoints.
- Admin audit-log query endpoint.

## Test commands and results

- `npm run type-check` in `vibegraph-web`: passed.
- `npm run build` in `vibegraph-web`: passed; existing Vite warning remains for chunks larger than 500 kB.
- IDE diagnostics across all changed Vue/TypeScript files: no diagnostics.
- Unit tests were not run because the task did not request adding or running tests.
- GitNexus pre-edit impact: Vue SFC symbols were unresolved with UNKNOWN/no caller data; `createApiKey` and router were LOW risk.
- GitNexus detect-changes reported CRITICAL aggregate risk across 114 pre-existing dirty files in the shared worktree; this is not scoped to the frontend slice. No commit, push, merge, reset, or revert was performed.

## Visual polish follow-up

- Refined both collapsed sidebars to a 66px rail with one consistent icon axis.
- Rebuilt the hamburger as three equal-length SVG strokes and verified its 20px glyph is centered inside a 40px control.
- Reduced new action buttons to compact 40px visual height, tighter padding/radius, and left-aligned labels while preserving safe hit areas.
- Chrome DevTools geometry check at 1440x900: collapsed rail `66px`, toggle `40x40`, first nav item `40px` high, menu glyph `20x20`; viewport screenshot and accessibility snapshot verified.
- Removed centered max-width/margin behavior from user and admin app content so surfaces anchor to the left edge after layout padding.
- Moved all three Overview actions directly below `Quick actions` in a compact left-aligned row.
- Fixed the scoped CSS leak where `UserLayout` styled every nested `<main>` and added double padding. DevTools now verifies the collapsed hamburger at `x=13px`, layout rail `66px`, page content at `x=78px` (`12px` after the rail), Quick Actions heading/actions both at `x=78px`, and buttons at `38px` high.
- Audited all Phase 6 user/admin surfaces: compacted text buttons, removed stretched unavailable controls, left-aligned empty states and import tabs, and retained centering only for icon glyphs, switches, spinners, and modal overlays.

## Compact-density audit

- Applied design-system, interface-polish, accessibility, and browser-QA guidance across the Phase 6 surfaces.
- Reduced app typography to a restrained hierarchy: Overview `32px`, page headings approximately `26-30px`, section headings `18-20px`, and operational body text `14px`.
- Compacted Overview summary cards to `100px` height with `12px` padding and aligned every icon, label, value, and caption to the same grid; numeric values use tabular figures.
- Reworked the user account card so the muted plan and highlighted credits share a baseline, with email below. DevTools verifies the expanded card at `239x78`; collapsed wallet remains a bordered `40x40` tile.
- Removed Reports `max-width` and centered margins. DevTools verifies Reports begins at the normal left gutter, uses full available width, `14px` body text, compact cards, and a `38px` submit button.
- Reduced spacing/radius/type across Repositories, API Keys, Notifications, announcement banner, import panel, Admin Settings, and Admin Security while preserving accessible form and icon hit areas.

## Unresolved UX gaps

- Notifications cannot display real announcements until the user announcement endpoint and final DTO land.
- API key creation cannot safely complete until backend project binding is enforced and returned by the API.
- Feature changes cannot update immediately/poll until session-state exists; imports use compatibility mode to preserve current functionality.
- Audit retention, request monitor, exact-IP policy, and audit logs remain honest disabled surfaces until backend contracts land; no mock business data is shown.
- The API-key modal is currently unreachable while the contract is unavailable. When backend enables it, a final keyboard focus-trap/restoration pass is still required.
- Browser data-state verification needs a real authenticated backend session with CORS configured for the selected Vite origin.
