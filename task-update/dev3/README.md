# Dev3 Task Brief: Frontend User Workspace + Admin Console

## Owner
Dev3 owns `vibegraph-web/**` frontend implementation for Phase 4.

## Branch And Git Rules
- Work on `poc`.
- Pull latest `origin/poc` before starting.
- Do not create a new branch unless Supervisor asks.
- Do not commit, push, merge, or run `git add .` without Supervisor approval.
- Stage only files in your scope.
- Do not edit backend Java files.

## Dependency Boundary
Implement against existing backend contracts first:
- account profile
- account usage
- account projects
- account API keys

For APIs not landed yet, create typed client methods only when contract is stable in Dev1/Dev2 handoff. Otherwise use feature placeholders that do not break build.

## Main Goal
Build the logged-in user workspace and admin console UI.

## Slice A: User Workspace UI

### Pages/Routes
- Account profile
- API keys
- My projects
- Usage/plan/quota
- Reports/feedback shell if backend contract exists

### Required Behavior
- Profile page shows email, display name, role.
- Profile page can update display name.
- Usage shows plan and source-storage quota meter.
- Quota text format:
  - `100MB / 500MB used`
  - `400MB remaining`
- Projects page lists imported projects with status and last analyzed date.
- API keys page:
  - list keys without secret
  - create key and show one-time secret clearly
  - disable key with confirmation
  - handle disabled states:
    - `API_KEYS_DISABLED`: “API key creation is disabled for your account.”
    - `API_KEY_PLAN_LIMIT_REACHED`: “API key limit reached for your current plan.”
- Blocked account copy:
  - “Your account is blocked. Project analysis, imports, patches, and API keys are paused. Reason: {safeReason}. You can still open a report if this looks incorrect.”
- Quota exceeded copy:
  - “Source storage quota exceeded. Free up storage or ask an admin for a quota override.”

### Tests Required
- Component/store tests for profile load/update.
- API key create/list/disable tests.
- Error state rendering tests for blocked/quota/API-key-disabled/plan-limit.
- Router guard/admin route tests if routes are added.

## Slice B: Admin Console UI

### Pages/Routes
- Admin overview dashboard
- Admin users table
- Admin user detail/edit drawer
- Admin feedback/reports

### Dashboard Behavior
- Until realtime API exists, fetch `GET /api/admin/overview` on load and poll every 15-30 seconds while visible.
- Maintain a rolling in-memory series of online user counts for the chart.
- Show a small “Polling” or “Simulated live” indicator only in admin chrome/status text.
- Keep chart props stable so later WebSocket/SSE can replace polling.

### Admin Users Behavior
- Dense table on desktop with sticky headers and filters above.
- Tablet: horizontal scroll, actions collapse into menu buttons.
- Mobile: stacked rows with key metrics first.
- Actions:
  - create user with temp password
  - block/unblock with reason
  - deactivate user using soft wording
  - update plan
  - quota override with inline validation
  - disable API key creation
  - disable individual API keys if endpoint exists
- Soft delete copy:
  - “Deactivate user”
  - “This disables sign-in and API access without immediately removing account data.”
- Quota override UI must prevent override below current source-storage usage.

### Feedback UI
- User/admin report thread.
- Reply box sticky at bottom on mobile.
- Closed reports show `Deletes after <date>`.
- Treat cleanup as eligibility after timestamp, not guaranteed immediate deletion.

### Tests Required
- Admin overview polling test with fake timers.
- User table action state tests.
- Quota override validation test.
- Feedback thread reply/close state tests once API exists.

## Responsive And Design Requirements
- Do not create a marketing landing page.
- Use dense operational UI, not oversized hero/cards.
- Tables: sticky headers, filters above.
- Mobile: stacked rows, destructive actions behind confirm dialogs.
- Quota meter, status chips, and action buttons need fixed/min widths.
- Do not use visible instructional text explaining how UI works.
- Keep text within containers on mobile and desktop.
- Avoid one-note purple/blue gradient themes.

## Files Likely Touched
- `vibegraph-web/src/router/**`
- `vibegraph-web/src/lib/**`
- `vibegraph-web/src/stores/**`
- `vibegraph-web/src/views/**`
- `vibegraph-web/src/components/**`
- `vibegraph-web/src/types/**`
- frontend tests under `vibegraph-web/src/**/__tests__/**`

Avoid touching:
- `src/main/java/**`
- `src/test/java/**`
- `vibegraph-cli/**`
- Docker/env files

## Verification Commands
Run from `vibegraph-web/`:
- `npm test -- --run`
- `npm run typecheck` or existing `vue-tsc --build --noEmit` command
- `npm run build`

If the app is visually changed, run the dev server on port 5173 and perform a browser smoke:
- login/register still works
- user account pages render
- admin routes do not show for normal user
- responsive layouts do not overlap

## Handoff Format
Report:
- Changed files.
- Routes/pages added.
- API contracts consumed.
- Screens/states implemented.
- Test commands and pass/fail counts.
- Screenshots or browser-smoke notes if available.
- Confirm no commit/push unless explicitly approved.
