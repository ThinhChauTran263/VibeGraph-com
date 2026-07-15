# P5-U1 - User App UI

Owner: User UI worker

## Scope

Allowed:

- `vibegraph-web/src/views/LoginView.vue`
- `vibegraph-web/src/components/layouts/UserLayout.vue`
- user views under `vibegraph-web/src/views/user/`
- user layout tests under `vibegraph-web/src/components/layouts/__tests__/`
- user view tests under `vibegraph-web/src/views/user/__tests__/`
- frontend types/store calls only when needed for user UI correctness

Avoid:

- backend files
- admin views/layout except shared type compatibility if absolutely necessary
- reverting 5.6-A/5.6-B changes

## Acceptance

- Login shows VibeGraph logo/name top-left.
- User sidebar is collapsible, persists state in localStorage, and uses tooltips or accessible labels when collapsed.
- User sidebar contains only:
  - Overview
  - Repositories
  - API Keys
  - Usage
  - Subscription
  - Reports
  - Tutorial
  - Settings
  - Sign Out
- Repositories page is the project list + import entry point.
- Usage page shows plan, credit balance, storage quota, and recent credit log/ledger if API exists; otherwise use clear loading/empty state and no runtime fake data.
- Subscription page shows current plan and VibeGraph plan options: Free, Pro, Pro Plus, Max, Enterprise.
- Settings password change uses old password/new password/confirm password. No OTP/send-code UI.
- No Grapuco copy.
- Responsive desktop/tablet/mobile behavior works.

## Required Evidence

- `npm run type-check`
- focused user tests or updated snapshots/specs
- final list of files changed
