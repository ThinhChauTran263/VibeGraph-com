# User-reported issues - 2026-07-19

## Status

Recorded only. Do not fix these until the user explicitly says to start fixing.

## Issues to fix

### 1. Admin account sometimes opens the user dashboard/layout

User report:
- Sometimes logging in with an admin account opens the user dashboard instead of the admin console.
- Screenshot shows `admin@vibegraph.com` inside the user `Settings` page/sidebar.

Initial code findings:
- `vibegraph-web/src/router/index.ts` still uses `localStorage.getItem('vg_user')` in `router.beforeEach()` and `getStoredUserRole()`.
- Browser auth now uses an HttpOnly cookie; localStorage is only non-sensitive cached user JSON. A stale/missing/wrong `vg_user` can make route decisions inconsistent.
- The user route group (`/dashboard`, `/settings`, `/projects`, etc.) has `requiresAuth` but no guard that redirects ADMIN users away from the user layout.
- `LoginView.vue` resolves post-login redirect using `auth.user?.role`, but an old `redirect=/dashboard` or direct navigation to `/settings` can still place an admin inside the user layout because user routes accept any authenticated role.

Expected fix direction:
- Make route authorization role-aware using the live auth/session source, not raw localStorage as the source of truth.
- If an ADMIN user enters user-only routes, redirect to `/admin` or the admin equivalent (`/admin/settings` for settings).
- Keep HttpOnly-cookie auth; do not restore JWT/localStorage auth.
- Add regression tests for admin login redirect, stale `vg_user`, and direct admin navigation to `/dashboard`/`/settings`.

### 2. User language selector placement does not match admin

User report:
- In user dashboard, the language toggle position is not acceptable.
- User wants it arranged like admin.

Initial code findings:
- Admin layout places `<LanguageSelector class="admin-language" />` in the sticky admin header, aligned to the far right.
- User layout places `<LanguageSelector class="sidebar-language" />` inside the sidebar between nav links and the account card.

Expected fix direction:
- Move or mirror the user language selector into a top/header position similar to admin.
- Keep compact `US` / `VN` toggle behavior.
- Check collapsed and mobile states carefully.

### 3. User dashboard is not 100% Vietnamese

User report:
- User dashboard still contains English when `VN` is active.
- Screenshot examples: `Repositories`, `API Keys`, `Subscription`, `Reports`, `Settings`, `New repository`, `Credits`, `Plan`, and mixed Vietnamese/English copy.

Initial code findings:
- `OpenCode_I18N_FOUNDATION_HANDOFF.md` and `ClaudeCli_USER_I18N_HANDOFF.md` both say full app translation is not complete.
- User shell basics and overview basics exist, but deeper user screens still need a full i18n pass.

Expected fix direction:
- Audit all user dashboard routes and shared components used by user dashboard.
- Move visible static UI copy to `vibegraph-web/src/language/locales/en-US.json` and `vi-VN.json`.
- Do not translate import prompts or user-generated/project/source-code content unless explicitly requested.
- Add or extend tests that switch locale and assert key user screens render Vietnamese.

### 4. Landing page is not 100% Vietnamese

User report:
- Landing/home page still has English after switching to Vietnamese.

Initial code findings:
- `LandingView.vue` has many hardcoded English strings beyond the first viewport, including feature cards, step cards, terminal demo labels, risk labels, and guide content.
- Examples found:
  - `Code as a living graph`
  - `Blast-radius analysis`
  - `Updates as you vibe`
  - `Architecture diagrams`
  - `Folder, archive or GitHub`
  - `Import your project`
  - `Run VibeGraph`
  - `Risk Level`
  - `Project Structure`
  - `Step 2: Import your project`

Expected fix direction:
- Complete landing-page i18n using the canonical `src/language` structure.
- Keep the compact `US` / `VN` language toggle on landing.
- Keep technical code/project-name examples unchanged where they are intended as examples, but translate labels, headings, body copy, and UI controls.

### 5. Admin Audit Logs live updates show unavailable

User report:
- Admin Audit Logs shows: `Live audit updates are temporarily unavailable.`

Initial code findings:
- SSE backend exists: `GET /api/admin/audit-logs/stream`.
- Frontend opens it in `admin.startAuditStream()` with `new EventSource(`${api.baseUrl}/api/admin/audit-logs/stream`, { withCredentials: true })`.
- `AuditView.vue` shows the warning whenever `admin.auditLiveStatus !== 'connected'`.
- Existing handoff says browser network QA was not completed because the Playwright Chrome extension/profile was unavailable.

Likely areas to verify:
- Whether the EventSource request receives 200 text/event-stream, 401/403, CORS failure, or connection close in real browser network tab.
- Whether HttpOnly auth cookie is sent on EventSource in the current local/prod origin setup.
- Whether backend CORS origin list includes the actual frontend origin.
- Whether the admin account really has `ROLE_ADMIN` when opening the stream.
- Whether the stream opens only after audit page load and closes on unmount as intended.

Expected fix direction:
- Reproduce in browser network/devtools first.
- Fix SSE auth/CORS/lifecycle based on actual failing status.
- Keep SSE for Audit Logs; do not switch this to STOMP unless the user changes the decision.
- Add regression test for failed/connected stream UI and, if backend issue, integration coverage for authenticated admin SSE.

## Notes

- Request Events already use SSE and are a reference implementation.
- Graph realtime and Reports realtime use STOMP WebSocket and should remain separate.
- Current worktree is dirty from multiple agents; preserve all existing changes and reread files before editing.
- No commit, push, or merge should be done unless explicitly requested.
