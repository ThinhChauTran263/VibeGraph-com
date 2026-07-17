# Kiro - FE-4 Admin Ops / Security / System

You are `Kiro`, working on VibeGraph Phase 8 frontend.

Repo: `D:\Users\User\IdeaProjects\VibeGraph`
Scope: Admin users, plans/credits, system feature flags, security/IP block, audit, admin settings.

Do not commit, push, or merge.
Do not edit backend.

Read first:
- `AGENTS.md`
- `task-update/phase-8-frontend-supervisor/README.md`
- `task-update/phase-8-frontend-supervisor/CladueCli_HANDOFF.md` if available

Goal:
Make admin operations complete, balanced, and connected to Phase 7 backend APIs.

Allowed primary files:
- `vibegraph-web/src/components/layouts/AdminLayout.vue`
- `vibegraph-web/src/views/admin/UsersView.vue`
- `vibegraph-web/src/views/admin/PlansCreditsView.vue`
- `vibegraph-web/src/views/admin/SystemView.vue`
- `vibegraph-web/src/views/admin/FeatureFlagsView.vue`
- `vibegraph-web/src/views/admin/SecurityView.vue`
- `vibegraph-web/src/views/admin/AuditView.vue`
- `vibegraph-web/src/views/admin/SettingsView.vue`
- focused admin-view tests

Do not modify user pages except shared component compatibility fixes.
Do not modify admin dashboard charts unless required for layout compatibility.

Work items:
- Admin layout/sidebar:
  - same collapsible pattern as user
  - admin-only menu
  - no duplicate Admin Console titles
  - no overlap in collapsed mode
- Users:
  - users table with filters/search
  - inline/full detail panel, not cramped right drawer
  - detail header shows email, blocked/deactivated status, plan
  - block/unblock/deactivate with safe reason
  - update plan/quota/credit refreshes visible data immediately
  - API key creation toggle design polished and aligned
  - disable specific API keys
- Plans & Credits:
  - CRUD connected
  - all storage values displayed and edited in MB
  - pricing rule form balanced
  - reset button
  - Enterprise contact sales toggle styled properly
- System / Feature Flags:
  - global controls for registration, imports, CLI push, API key creation, project analyze, gen use case
  - MCP global and child tool controls
  - collapsible groups with only title shown when collapsed
  - disabled feature state should propagate to user UI via store contract
  - user-facing controls must become visibly disabled and non-interactive when a flag is off
- Security:
  - request events
  - top users/IPs
  - IP block create/update/delete
  - rate/abuse status
- Audit:
  - audit log list/detail
  - retention setting
- Admin Settings:
  - admin profile and password change like user settings

Acceptance:
- Admin pages use real API data.
- Forms/buttons align and remain balanced at desktop/tablet/mobile.
- Updates refresh without manual reload.
- No default browser alert/confirm for app-level actions; use designed dialogs/toasts.
- No console errors.

Required verification:
- `cd vibegraph-web && npm run type-check`
- `cd vibegraph-web && npm run test:unit -- --run src/views/admin src/stores/__tests__/admin.spec.ts`
- Chrome DevTools screenshots for:
  - users detail
  - plans/credits
  - system feature flags collapsed/expanded
  - security/IP blocks
  - audit

Handoff:
Write `task-update/phase-8-frontend-supervisor/Kiro_HANDOFF.md` with:
- files changed
- admin flows completed
- Chrome QA notes
- tests run and exact result
- open blockers
