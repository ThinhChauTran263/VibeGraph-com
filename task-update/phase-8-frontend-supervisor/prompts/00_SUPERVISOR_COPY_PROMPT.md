# VibeGraph Phase 8 Frontend - Supervisor Prompt

You are the supervisor for VibeGraph Phase 8 frontend completion.

Repo: `D:\Users\User\IdeaProjects\VibeGraph`
Base branch: `poc`
Backend source of truth: Phase 7 commit `8f1b38f`

Read first:
- `AGENTS.md`
- `task-update/phase-8-frontend-supervisor/README.md`
- `task-update/phase-8-frontend-supervisor/AGENT_ASSIGNMENTS.md`
- `task-update/phase-7-backend-supervisor/BE-6_FINAL_REVIEW.md`

Start the frontend phase now.

Assign:
- `CladueCli`: FE-1 API contract, types, stores, feature availability
- `ClaudeChat`: FE-2 User shell and user product pages
- `Droid`: FE-3 Admin overview and charts
- `Kiro`: FE-4 Admin users, plans, system flags, security/IP block, audit
- `CodexCli`: FE-5 Reports, notifications, announcements, realtime, blocked UX
- `gemini`: FE-6 Integration review and Chrome DevTools QA

Execution rules:
- Use one branch/worktree per worker from latest `poc`.
- Frontend only. No backend edits unless supervisor approves a confirmed backend blocker.
- Workers must not commit, push, or merge.
- Workers must write handoff files under `task-update/phase-8-frontend-supervisor/`.
- Keep real API integration. No app-code mocks.
- Keep HttpOnly-cookie auth. Do not reintroduce JWT localStorage.
- Use `echarts` / `vue-echarts` for charts. Do not add another chart library.
- Use Chrome DevTools for visual QA.

Merge order:
1. `CladueCli` API/types/stores
2. `ClaudeChat` user UI
3. `Droid` admin overview/charts
4. `Kiro` admin ops/security/system
5. `CodexCli` reports/notifications/realtime
6. `gemini` final FE review

Final gate:
- `cd vibegraph-web && npm run type-check`
- `cd vibegraph-web && npm run test:unit -- --run`
- `cd vibegraph-web && npm run build`
- `git diff --check`
- `git status --short --branch`

Final output:
- PASS/REQUEST CHANGES verdict
- files changed by each worker
- tests run and exact result
- Chrome DevTools QA notes
- unresolved findings by severity
- no commit/push/merge without explicit supervisor approval
