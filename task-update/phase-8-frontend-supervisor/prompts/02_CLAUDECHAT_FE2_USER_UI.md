# ClaudeChat - FE-2 User Product UI

You are `ClaudeChat`, working on VibeGraph Phase 8 frontend.

Repo: `D:\Users\User\IdeaProjects\VibeGraph`
Scope: User shell and user product pages only.

Do not commit, push, or merge.
Do not edit backend.

Read first:
- `AGENTS.md`
- `task-update/phase-8-frontend-supervisor/README.md`
- `task-update/phase-8-frontend-supervisor/CladueCli_HANDOFF.md` if available

Goal:
Implement the user-facing VibeGraph app experience against real APIs.

Allowed primary files:
- `vibegraph-web/src/components/layouts/UserLayout.vue`
- `vibegraph-web/src/views/LoginView.vue`
- `vibegraph-web/src/views/user/**`
- focused user-view tests
- shared presentational components only when needed

Do not modify admin pages except shared component compatibility fixes.

Work items:
- Login page:
  - VibeGraph logo/name in top-left
  - professional layout, no overlap at mobile widths
- User layout/sidebar:
  - independently collapsible/expandable
  - collapsed mode uses icons only
  - hamburger icon expands when collapsed
  - no text/icon overlap in collapsed mode
  - icons for every menu item
  - account mini-card shows email/display name, plan, remaining credits
  - remove Workspaces, Spec Designer, Community, Referral, Tutorial for now
- User overview:
  - imported repo/project count
  - remaining credits
  - current plan
  - quick actions to Repositories, API Keys, Reports
- Repositories:
  - list imported projects first
  - `New Repository` reveals existing import form
  - keep existing Local/Archive/GitHub import UX/copy/logic
  - successful import opens project/graph/analyze view
  - disabled import features visibly disable relevant controls
- API Keys:
  - create API key requires selecting a repository/project
  - API key acts as project identity for CLI/MCP
  - show project binding in list
  - one-time secret display only after create
  - disabled state when API keys disabled/account blocked/feature off
- Usage:
  - plan
  - quota in MB
  - remaining storage
  - credits
  - recent credit ledger
- Subscription:
  - real plan catalog from backend, not hardcoded stale values
  - Enterprise shows contact sales state
- Settings:
  - profile update
  - password change with old password/new password/confirm password
  - no OTP

Acceptance:
- User pages use real store/API data.
- Disabled states are clear and non-interactive, not silent no-ops.
- Sidebar responsive and no overlap at 320/768/1024/1440.
- No default browser alerts/confirms for app actions.
- No console errors in Chrome DevTools.

Required verification:
- `cd vibegraph-web && npm run type-check`
- `cd vibegraph-web && npm run test:unit -- --run src/views/user`
- Chrome DevTools screenshots/notes for:
  - login
  - overview
  - repositories
  - API keys
  - sidebar collapsed/expanded at 320/768/1024/1440

Handoff:
Write `task-update/phase-8-frontend-supervisor/ClaudeChat_HANDOFF.md` with:
- files changed
- user flows completed
- Chrome QA notes
- tests run and exact result
- open blockers
