# Droid - FE-3 Admin Overview / Charts

You are `Droid`, working on VibeGraph Phase 8 frontend.

Repo: `D:\Users\User\IdeaProjects\VibeGraph`
Scope: Admin overview dashboard and charts only.

Do not commit, push, or merge.
Do not edit backend.

Read first:
- `AGENTS.md`
- `task-update/phase-8-frontend-supervisor/README.md`
- `task-update/phase-8-frontend-supervisor/CladueCli_HANDOFF.md` if available

Goal:
Rebuild Admin Overview as a professional operations dashboard using real backend data and `echarts` / `vue-echarts`.

Allowed primary files:
- `vibegraph-web/src/views/admin/DashboardView.vue`
- admin dashboard chart components/tests only
- shared chart utility files only when necessary

Do not modify admin users/plans/security/system pages.

Work items:
- Use `echarts` / `vue-echarts`; do not add another chart library.
- Overview sections:
  - total users
  - online users
  - credit consumption
  - system storage
  - imported repositories/projects
  - top storage projects
  - plan distribution
  - security/abuse alerts
- Chart controls:
  - day/month/quarter/year where data supports it
  - month shows all 12 months
  - year shows last 5 years
  - online users chart shows only last 10 minutes
  - online chart labels use 24h time
  - y-axis ranges should be readable, not huge empty spacing
- Layout:
  - total user, online user, credit, and system storage charts belong to one coherent top section
  - top storage projects replaces the old square plan distribution area
  - plan distribution becomes a horizontal/wide section
  - security/abuse alerts become a horizontal/wide section
  - operations/header area stays aligned and visually stable
- Poll `/api/admin/overview` every 15-30 seconds while visible.
- Do not call it fake. Use small admin status text such as `Polling`.
- Prevent duplicate online chart points after reload or polling.

Acceptance:
- Charts are visually clear, aligned, and responsive.
- No overlapping labels or clipped donut text.
- Online chart starts visually from the x-axis baseline.
- No console errors.

Required verification:
- `cd vibegraph-web && npm run type-check`
- `cd vibegraph-web && npm run test:unit -- --run src/views/admin/__tests__/DashboardView.spec.ts`
- Chrome DevTools screenshots at 1440, 1024, 768, 320.

Handoff:
Write `task-update/phase-8-frontend-supervisor/Droid_HANDOFF.md` with:
- files changed
- chart behavior implemented
- screenshots/Chrome QA notes
- tests run and exact result
- open blockers
