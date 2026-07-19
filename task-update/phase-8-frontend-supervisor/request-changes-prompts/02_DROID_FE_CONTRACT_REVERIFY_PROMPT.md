# Droid - FE Contract Reverify After Backend Contract Fix

You are `Droid`, re-verifying FE API/types/stores after Phase 8 backend contract fixes.

Repo: `D:\Users\User\IdeaProjects\VibeGraph`

Read first:
- `AGENTS.md`
- `task-update/phase-8-frontend-supervisor/FE-6_FINAL_REVIEW.md`
- `task-update/phase-8-frontend-supervisor/BACKEND_CONTRACT_FIX_HANDOFF.md` if it exists
- `task-update/phase-8-frontend-supervisor/request-changes-prompts/00_AGENT_AUTONOMY_AND_OVERLAP_PROTOCOL.md`

Autonomy:
- Do not stop to ask `yes` for local FE edits, tests, or overlap with other agents.
- If another agent changed `api.ts`, `types/api.ts`, `account.ts`, or `admin.ts`, read and merge carefully.
- Do not commit, push, merge, delete files, or revert another agent's work.

Scope:
- `vibegraph-web/src/types/api.ts`
- `vibegraph-web/src/lib/api.ts`
- `vibegraph-web/src/stores/account.ts`
- `vibegraph-web/src/stores/admin.ts`
- focused tests

Dependency:
- Start only after `CladueCli` has written `task-update/phase-8-frontend-supervisor/BACKEND_CONTRACT_FIX_HANDOFF.md`, unless you are only doing read-only preparation.
- If the backend contract handoff is missing, do not fake the contract. Write a short blocked note and stop before implementation.

Work:
- Consume the new backend feature capabilities contract.
- Make feature availability fail closed when capability data is absent or loading fails.
- Add typed project-bound API key request/response support.
- Preserve HttpOnly cookie auth.
- Do not put JWT in localStorage.
- Keep no app-code mocks.
- Ensure user import/API-key UI workers can read capability and project-bound key state.

Run:
- `cd vibegraph-web && npm run type-check`
- `cd vibegraph-web && npm run test:unit -- --run src/lib src/stores`
- `cd vibegraph-web && npm run build`
- `git diff --check`

Handoff:
Write `task-update/phase-8-frontend-supervisor/Droid_REQUEST_CHANGES_HANDOFF.md` with:
- files changed
- API/types/store changes
- tests exact result
- overlap handled
- remaining blockers
