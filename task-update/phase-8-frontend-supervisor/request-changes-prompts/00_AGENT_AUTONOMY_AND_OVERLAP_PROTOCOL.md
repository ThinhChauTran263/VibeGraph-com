# Phase 8 Agent Autonomy And Overlap Protocol

Use this protocol in every Phase 8 follow-up prompt.

## Do Not Stop For These

Do not ask the user for confirmation for normal local development work:

- reading files
- inspecting `git diff` or `git status`
- editing files inside your assigned scope
- updating tests for your assigned scope
- running `npm`, `mvn`, `git diff`, `git status`, or browser QA commands
- fixing type-check, lint, build, or test failures caused by your changes
- reconciling non-conflicting edits from another Phase 8 agent
- adding handoff notes

If another agent has changed a file you need:

1. Read the current file and relevant diff.
2. Preserve the other agent's behavior.
3. Apply the smallest compatible edit for your task.
4. Run focused tests.
5. Record the overlap in your handoff.

Do not stop just because the worktree is dirty. Phase 8 is intentionally multi-agent and dirty.

## Ask The User Only For These

Ask before:

- commit
- push
- merge
- deleting files
- reverting another agent's work
- changing backend when your prompt is frontend-only
- changing product scope or accepting an unresolved product requirement
- changing credentials, secrets, environment variables, or production config
- using a destructive command such as `git reset --hard`, `git checkout --`, or recursive delete

## Conflict Handling

If a conflict is logical, not textual, choose the safer product behavior:

- real API over mock
- fail closed over falsely enabled
- visible disabled state over silent no-op
- preserve HttpOnly cookie auth
- no JWT localStorage
- no browser default alert/confirm
- no hardcoded secrets

If you truly cannot continue without a decision, write a short blocker note with:

- file
- exact conflict
- options
- your recommended option

Continue with other independent work instead of stopping the whole task.

## Handoff Requirement

Every handoff must include:

- files changed
- tests run and exact result
- overlaps with other agents
- blockers, if any
- whether commit/push is safe
