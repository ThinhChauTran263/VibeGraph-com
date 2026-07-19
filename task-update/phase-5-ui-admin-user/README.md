# Phase 5 - User/Admin Product UI + Admin Ops

Branch: `DanhTest-intergration`

Supervisor intent: implement the finalized VibeGraph user/admin experience without throwing away the backend/frontend integration work already in progress.

## Guardrails

- Do not commit, push, merge, or delete unrelated files.
- Do not revert existing changes from other agents. Work with the dirty tree.
- Keep runtime flows wired to real APIs. Test mocks are allowed only in tests.
- No hardcoded secrets, JWTs, raw API keys, or production credentials.
- All UI must be responsive and keyboard accessible.
- Use VibeGraph branding only. Remove Grapuco copy/assets if introduced.
- Do not add feature entitlement by plan in this phase.

## Product Decisions

### User App

- Login page must show VibeGraph logo/name at top-left.
- User app uses a left sidebar with expand/collapse and localStorage persistence.
- User sidebar items:
  - Overview
  - Repositories
  - API Keys
  - Usage
  - Subscription
  - Reports
  - Tutorial
  - Settings
  - Sign Out
- Remove user sidebar items that do not fit VibeGraph:
  - Workspaces
  - Spec Designer
  - Community
  - Referral
- Sidebar account card shows current account/email, plan, and remaining credits.
- Repositories is both the imported-project list and new import entry point.
- Usage shows plan, source storage quota, credit balance, and recent credit ledger/logs.
- Subscription shows current plan, quota/credits, and upgrade options.
- Settings supports password change with old password, new password, confirm new password. No OTP.
- Reports remains a user/admin thread flow.
- Notifications should be a top/right bell-style surface, not a sidebar item.

### Admin App

- Admin app uses the same collapsible sidebar pattern, but admin-specific menu.
- Admin sidebar items:
  - Overview
  - Users
  - Feedback / Reports
  - Plans & Credits
  - Security
  - Feature Flags
  - Announcements
  - Settings
  - Sign Out
- Admin overview includes:
  - total users
  - online users with polling/live chart
  - user growth by month/quarter/year
  - credit consumption by month/quarter/year
  - total imported repositories
  - system storage donut
  - plan distribution
  - top storage users/projects
  - security/abuse alerts
- Plans & Credits supports CRUD for plans and credit pricing rules.
- Security includes abuse monitoring: rate-limit events, failed login, suspicious API/API key/MCP/CLI usage.
- Feature Flags can disable global features and individual MCP tools.
- Announcements lets admin create in-app notices for maintenance, plan changes, disk warnings, CLI updates, security notices, and general notices.

### Storage

- User quota is read from DB/project usage.
- Imported files live on VPS/local filesystem/volume.
- Admin system storage reads filesystem/mount/config and must not hardcode disk size.
- Local dev may show the local machine/Docker mount size.
- Admin overview should show a system storage donut and clear mount/source labels.

## Work Cards

| Card | Owner | Scope | State |
| --- | --- | --- | --- |
| P5-U1 | Agent User UI (`019f60c2-bf72-7e31-a3e9-2bef828fbc61`) | Login, user layout/sidebar, user pages | review |
| P5-A1 | Agent Admin UI (`019f60c3-24af-7733-a34a-ceb3cfa3b6e3`) | Admin layout/sidebar, overview, admin pages | review |
| P5-B1 | Agent Backend Ops APIs (`019f60c3-93f9-7d50-8b02-18324bd246f2`) | Admin storage/security/feature flags/announcements/plans support | review |
| P5-R1 | Supervisor/Reviewer | Integration review and final gates | review-ready |

## Merge Gate

Frontend:

```bash
cd vibegraph-web
npm run type-check
npm run test:unit -- --run
npm run build
```

Backend:

```bash
./mvnw clean test
./mvnw clean verify
```

Repo:

```bash
git diff --check poc..DanhTest-intergration
git status --short --branch
```
