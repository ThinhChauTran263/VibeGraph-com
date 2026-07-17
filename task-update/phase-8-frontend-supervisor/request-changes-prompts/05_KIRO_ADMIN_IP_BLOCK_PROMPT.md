# Kiro - Admin Security IP Block Mutation Fix

You are `Kiro`, fixing FE-6 admin security request changes.

Repo: `D:\Users\User\IdeaProjects\VibeGraph`

Read first:
- `AGENTS.md`
- `task-update/phase-8-frontend-supervisor/FE-6_FINAL_REVIEW.md`
- `task-update/phase-8-frontend-supervisor/request-changes-prompts/00_AGENT_AUTONOMY_AND_OVERLAP_PROTOCOL.md`

Autonomy:
- Do not stop to ask `yes` for local FE edits, tests, or overlap with other agents.
- If another agent changed `admin.ts` or `SecurityView.vue`, read and merge carefully.
- Do not commit, push, merge, delete files, or revert another agent's work.

Scope:
- admin security/IP block UI and store behavior
- focused tests
- Do not edit backend.

Fix:
- IP-block mutations can currently report failure after the write succeeds because they call all-or-nothing `refreshSecurity()`.
- After create/update/delete IP block, refresh only the affected IP block collection or refresh panels independently.
- A telemetry/request-events failure must not turn a successful IP-block write into a save failure.
- UI should show:
  - mutation success when policy write succeeds
  - separate warning if telemetry refresh failed
  - retry action for failed panels
- Preserve real API integration; no mock business behavior.

Run:
- `cd vibegraph-web && npm run type-check`
- `cd vibegraph-web && npm run test:unit -- --run src/views/admin src/stores/__tests__/admin.spec.ts`
- `cd vibegraph-web && npm run build`
- `git diff --check`

Chrome QA:
- admin Security page
- create/update/delete IP block
- failed telemetry panel state if practical
- responsive at 320/768/1024/1440

Handoff:
Write `task-update/phase-8-frontend-supervisor/Kiro_REQUEST_CHANGES_HANDOFF.md` with:
- files changed
- tests exact result
- Chrome QA notes
- overlap handled
- remaining blockers
