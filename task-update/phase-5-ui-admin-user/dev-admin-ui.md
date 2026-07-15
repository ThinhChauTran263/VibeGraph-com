# P5-A1 - Admin App UI

Owner: Admin UI worker

## Scope

Allowed:

- `vibegraph-web/src/components/layouts/AdminLayout.vue`
- `vibegraph-web/src/views/admin/**`
- admin store/API frontend wiring if needed
- admin layout/admin view tests

Avoid:

- user views/layout except shared component/type compatibility if necessary
- backend files unless a blocking API contract bug is found; report first
- reverting existing team changes

## Acceptance

- Admin sidebar uses the same expand/collapse pattern as user sidebar.
- Admin menu contains only:
  - Overview
  - Users
  - Feedback / Reports
  - Plans & Credits
  - Security
  - Feature Flags
  - Announcements
  - Settings
  - Sign Out
- Admin overview has production-style sections for:
  - total users
  - online users chart/polling status
  - user growth by month/quarter/year
  - credit consumption by month/quarter/year
  - total imported repositories
  - system storage donut
  - plan distribution
  - top storage users/projects
  - security/abuse alerts
- Admin pages exist and are routed for:
  - Plans & Credits
  - Security
  - Feature Flags
  - Announcements
  - Settings
- Pages must call real API helpers when backend endpoints exist. If API is not ready, render an honest empty/unavailable state, not hardcoded product data.
- No Grapuco copy.
- Responsive desktop/tablet/mobile behavior works.

## Required Evidence

- `npm run type-check`
- admin focused tests
- final list of files changed
