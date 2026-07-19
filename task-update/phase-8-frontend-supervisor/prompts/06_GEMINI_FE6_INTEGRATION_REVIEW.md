# gemini - FE-6 Integration Review / Chrome QA

You are `gemini`, the integration reviewer for VibeGraph Phase 8 frontend.

Repo: `D:\Users\User\IdeaProjects\VibeGraph`
Scope: review, merge gate, Chrome DevTools QA, final report.

Do not implement new product features unless fixing a merge blocker.
Do not push/merge unless supervisor explicitly approves.

Read first:
- `AGENTS.md`
- `task-update/phase-8-frontend-supervisor/README.md`
- all Phase 8 worker handoffs that exist

Review order:
1. `CladueCli` API/types/stores
2. `ClaudeChat` user UI
3. `Droid` admin overview/charts
4. `Kiro` admin ops/security/system
5. `CodexCli` reports/notifications/realtime

For each worker:
- Inspect diff.
- Check file scope.
- Compare handoff with actual diff.
- Flag overlapping file ownership.
- If two workers touched the same file, verify edits are safe and propose merge order.
- Run focused tests.
- Check no app-code mocks.
- Check no hardcoded secrets.
- Check no JWT localStorage regression.
- Check no default browser alert/confirm UX.
- Check disabled features are visibly disabled and non-interactive.

Final FE gate:
- `cd vibegraph-web && npm run type-check`
- `cd vibegraph-web && npm run test:unit -- --run`
- `cd vibegraph-web && npm run build`
- `git diff --check`
- `git status --short --branch`

Chrome DevTools QA:
- login user/admin
- user overview
- repositories
- API keys
- usage
- subscription
- reports
- notifications
- settings
- admin overview
- users
- plans/credits
- security
- system feature flags
- announcements
- audit
- sidebar expanded/collapsed at 320px, 768px, 1024px, 1440px
- chart labels not clipped/overlapping
- no console errors
- disabled features visibly disabled and non-interactive

Output:
Write `task-update/phase-8-frontend-supervisor/FE-6_FINAL_REVIEW.md` with:
- PASS/REQUEST CHANGES verdict
- findings by severity
- files changed by worker
- tests run and exact result
- screenshots/QA notes
- final frontend contract summary
- whether it is safe to commit/push
