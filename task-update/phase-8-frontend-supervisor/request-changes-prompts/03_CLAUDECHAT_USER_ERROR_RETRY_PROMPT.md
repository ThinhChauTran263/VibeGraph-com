# ClaudeChat - User Error/Retry And Capability UI Fixes

You are `ClaudeChat`, fixing FE-6 user-side request changes.

Repo: `D:\Users\User\IdeaProjects\VibeGraph`

Read first:
- `AGENTS.md`
- `task-update/phase-8-frontend-supervisor/FE-6_FINAL_REVIEW.md`
- `task-update/phase-8-frontend-supervisor/request-changes-prompts/00_AGENT_AUTONOMY_AND_OVERLAP_PROTOCOL.md`
- `task-update/phase-8-frontend-supervisor/CladueCli_REQUEST_CHANGES_HANDOFF.md` if it exists

Autonomy:
- Do not stop to ask `yes` for local FE edits, tests, or overlap with other agents.
- If another agent changed a user file you need, read current code and preserve their behavior.
- Do not commit, push, merge, delete files, or revert another agent's work.

Scope:
- User views/layout only.
- Do not edit backend.
- Do not edit admin pages except shared component compatibility.

Fix:
1. User usage/profile/subscription mounts must not reject into indefinite loading:
   - `UsageView.vue`
   - `SubscriptionView.vue`
   - `ProfileView.vue`
   Add local error state, retry action, and tests.

2. Import controls must consume real feature capability state:
   - local/archive/GitHub import controls must be visibly disabled and non-interactive when disabled.
   - If capability data is unavailable, fail closed with clear copy.
   - Do not fake capabilities.

3. API key creation must support project-bound contract if backend/FE-1 added it:
   - select repository/project before create
   - show project binding in list
   - if contract still missing, keep disabled with precise blocker copy.

4. Keep existing user UI quality:
   - no nested main
   - no overlap in sidebar collapsed state
   - no browser alert/confirm
   - no JWT localStorage

Run:
- `cd vibegraph-web && npm run type-check`
- `cd vibegraph-web && npm run test:unit -- --run src/views/user src/views/__tests__/LoginView.spec.ts`
- `cd vibegraph-web && npm run build`
- `git diff --check`

Chrome QA:
- user login
- overview
- repositories import disabled/enabled states
- API keys project selection
- usage error/retry
- subscription error/retry
- settings/profile error/retry
- sidebar at 320/768/1024/1440

Handoff:
Write `task-update/phase-8-frontend-supervisor/ClaudeChat_REQUEST_CHANGES_HANDOFF.md` with:
- files changed
- tests exact result
- Chrome QA notes
- overlap handled
- remaining blockers
