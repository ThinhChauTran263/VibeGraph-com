# CladueCli - Backend Contract Fix For Feature Capabilities And Project-Bound API Keys

You are `CladueCli`, the backend contract fixer for VibeGraph Phase 8 request changes.

Repo: `D:\Users\User\IdeaProjects\VibeGraph`

Read first:
- `AGENTS.md`
- `task-update/phase-8-frontend-supervisor/FE-6_FINAL_REVIEW.md`
- `task-update/phase-8-frontend-supervisor/request-changes-prompts/00_AGENT_AUTONOMY_AND_OVERLAP_PROTOCOL.md`

Autonomy:
- Do not stop to ask `yes` for local code edits, tests, or overlap with other agents.
- Do not commit, push, merge, delete files, or revert another agent's work.
- Preserve existing Phase 7 behavior.

Scope:
Fix the two backend contract blockers identified by FE-6:

1. Add a safe authenticated feature capability contract so frontend can truthfully disable user controls when admin turns features off.
2. Add project-bound API keys so API keys can identify a repository/project for CLI/MCP.

Required behavior:

Feature capabilities:
- `GET /api/account/session-state` should expose a safe capability map or add a clearly named authenticated account capability endpoint.
- Include controlled features needed by frontend:
  - registration if relevant to public/auth surfaces
  - local import
  - archive import
  - GitHub import
  - CLI push
  - API key creation
  - project analyze
  - gen use case
  - MCP global enabled
  - MCP child tool states
- Blocked/deactivated users must receive capabilities that fail closed for product flows while preserving allowed support/report access.
- Do not expose internal flag implementation details or unsafe admin-only metadata.

Project-bound API keys:
- Account API key create must accept project/repository id.
- API key list/response must include safe project binding display data.
- Admin API key create/list/disable should preserve or support binding where needed.
- Validate project ownership for user-created keys.
- Validate project existence and target user relationship for admin-created keys.
- Existing keys without binding must be handled safely in migration/backfill.
- CLI/MCP validation path must be able to resolve the bound project from the API key.
- Blocked/deactivated/account-disabled behavior must remain enforced.

Testing:
- Add or update backend tests for:
  - session capabilities reflect disabled feature flags
  - blocked account capabilities fail closed
  - account API key create requires owned project
  - account API key create rejects another user's project
  - API key list includes project binding without raw secret
  - admin key create/list binding behavior
  - CLI/MCP API-key validation resolves project binding

Run:
- `./mvnw -Dtest=*ApiKey*,*Feature*,*Session*,*Account* test`
- `./mvnw clean test`
- `git diff --check`

Handoff:
Write `task-update/phase-8-frontend-supervisor/BACKEND_CONTRACT_FIX_HANDOFF.md` with:
- files changed
- API contract added/changed
- migration details
- tests run and exact result
- frontend integration notes
- overlaps handled
- commit/push safety
