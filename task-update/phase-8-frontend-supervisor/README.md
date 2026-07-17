# Phase 8 - Frontend User/Admin Completion

Status: READY FOR PARALLEL FRONTEND AGENTS
Base branch: `poc`
Backend source of truth: Phase 7 commit `8f1b38f`

## Goal

Implement the VibeGraph user/admin frontend against the completed Phase 7 backend contract.

This phase is frontend-only unless a worker finds a confirmed backend blocker and documents it for supervisor review.

## Product Decisions

- Use VibeGraph branding only.
- Login shows VibeGraph logo/name in the top-left.
- User sidebar is collapsible/expandable. Collapsed mode uses icons and a hamburger button to expand.
- Remove Workspaces, Spec Designer, Community, Referral.
- User sidebar:
  - Overview
  - Repositories
  - API Keys
  - Usage
  - Subscription
  - Reports
  - Settings
  - Sign Out
- Tutorial is not needed for now.
- User overview shows imported repo count, remaining credits, plan, and quick actions to Repositories, API Keys, Reports.
- Repositories shows imported projects first. `New Repository` reveals the existing import form. Do not replace the VibeGraph import prompt/form.
- Successful import should take the user to view/analyze the imported project, not require export.
- API key creation requires selecting a repository/project. API key identifies the project for CLI/MCP.
- Usage shows plan, quota MB, credits, and recent credit ledger.
- Subscription shows plan/credits/quota and upgrade options.
- Settings supports profile and password change with old password/new password/confirm password. No OTP.
- Notifications are user-facing and backed by backend announcement/notification APIs.
- Reports remain threaded and realtime.
- Blocked/deactivated users see safe reason and cannot use product flows.

Admin:
- Admin sidebar uses the same collapsible pattern but admin-specific items.
- Admin sidebar:
  - Overview
  - Users
  - Feedback / Reports
  - Plans & Credits
  - Security
  - System / Feature Flags
  - Announcements
  - Settings
  - Sign Out
- Admin Overview uses ECharts/vue-echarts. Do not add another chart library.
- Overview supports day/month/quarter/year where backend data allows.
- Month charts show 12 months. Year charts show last 5 years.
- Online user chart shows last 10 minutes and uses 24h time labels.
- Show total users, online users, credit usage, system storage, top storage projects, plan distribution, security/abuse alerts.
- Users detail is inline/full in the users surface, not a cramped right drawer. Updates should refresh visible data immediately.
- Admin can block/unblock/deactivate with reason, update plan/quota/credit, toggle API key creation, and disable keys.
- Security shows request events, top users/IPs, IP blocks, and abuse/rate-limit state.
- System/Feature Flags controls global features and MCP child tools. Disabled features must look disabled and be non-interactive in user UI.
- Announcements CRUD creates user notifications.
- Audit logs are admin-facing.

## Agent Roster

| Agent | Role |
| --- | --- |
| `CladueCli` | FE-1 API contract, types, stores, feature availability |
| `ClaudeChat` | FE-2 User shell, user overview/repositories/API keys/usage/subscription/settings |
| `Droid` | FE-3 Admin overview and charts |
| `Kiro` | FE-4 Admin users, plans, system/feature flags, security/IP block/audit |
| `CodexCli` | FE-5 Reports, notifications, announcements, realtime, blocked UX polish |
| `gemini` | FE-6 Integration review, Chrome DevTools QA, merge gate |

## Copy-Paste Prompts

Use these files to command each agent directly:

- `prompts/00_SUPERVISOR_COPY_PROMPT.md`
- `prompts/01_CLADUECLI_FE1_API_TYPES_STORES.md`
- `prompts/02_CLAUDECHAT_FE2_USER_UI.md`
- `prompts/03_DROID_FE3_ADMIN_OVERVIEW_CHARTS.md`
- `prompts/04_KIRO_FE4_ADMIN_OPS_SECURITY_SYSTEM.md`
- `prompts/05_CODEXCLI_FE5_REPORTS_NOTIFICATIONS_REALTIME.md`
- `prompts/06_GEMINI_FE6_INTEGRATION_REVIEW.md`

## Shared Rules

- No backend edits unless supervisor approves a confirmed backend blocker.
- Do not add mock business behavior in app code. Tests may mock.
- Real API integration only.
- No hardcoded secrets, JWTs, raw API keys, passwords, or production URLs.
- Keep HttpOnly cookie auth flow. Do not reintroduce JWT localStorage.
- Use `echarts` / `vue-echarts` for charts.
- Use existing VibeGraph UI components and tokens where practical.
- Use Chrome DevTools for visual verification on desktop/tablet/mobile.
- Every worker writes a handoff file in this folder.

## Global FE Gate

```bash
cd vibegraph-web
npm run type-check
npm run test:unit -- --run
npm run build
```

Browser QA:

- User login and admin login.
- User overview, repositories import flow, API key project selection, usage, subscription, reports, notifications, settings.
- Admin overview charts, users detail, plans/credits, security/IP block, system feature flags, announcements, audit.
- Sidebar expanded/collapsed at 320px, 768px, 1024px, 1440px.
- Disabled feature states are visibly disabled and non-interactive.
- No console errors.
