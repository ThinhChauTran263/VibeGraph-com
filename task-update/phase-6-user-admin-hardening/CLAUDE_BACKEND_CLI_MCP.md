# Claude Prompt - Phase 6 Backend, CLI, MCP

You are Claude, assigned to VibeGraph Phase 6 backend/CLI/MCP work.

Repository: `D:\Users\User\IdeaProjects\VibeGraph`  
Branch: create/use your own branch from `DanhTest-intergration` if possible.  
Do not commit, push, or merge unless explicitly instructed.  
Write a handoff report to `task-update/phase-6-user-admin-hardening/CLAUDE_HANDOFF.md`.

## Mission

Implement backend enforcement and CLI/MCP contracts for user/admin hardening:
- blocked account behavior
- real feature flag enforcement
- API keys bound to projects
- MCP child-tool toggles
- concurrent import guard
- request monitoring/rate limiting/IP block
- audit log + retention

## Non-Negotiable Rules

- Use GitNexus impact before editing symbols per AGENTS.md/RULES.md.
- Keep auth, ownership, quota, credit, path safety, secret filtering, and binary filtering intact.
- Never log or expose JWTs, API key raw secrets, passwords, .env content, private keys, or uploaded source content.
- All admin endpoints remain `ROLE_ADMIN` only.
- All user/project operations enforce ownership.
- If a feature flag is disabled, backend must reject the operation with a clear structured error; UI-only disabling is insufficient.

## Slice 1 - Blocked Account Realtime Contract

Requirements:
- If admin blocks a user, product actions become unavailable quickly.
- If user logs in while blocked, login must surface a safe block reason.
- Current product flows must reject blocked accounts:
  - project import
  - local patch/CLI push
  - analyze
  - API key creation/use
  - MCP tools
- User may still access a safe report/support surface if that already exists or if frontend can route there.

Backend work:
- Add/verify a lightweight account/session-state endpoint, e.g. `GET /api/account/session-state`.
- Response should include account status, safe block reason, feature flag states relevant to user UI, plan/quota/credit summary if already cheap to fetch, and current announcement signal if implemented by frontend contract.
- Add tests for blocked login and blocked product operations.

Acceptance:
- Blocked user cannot use product endpoints.
- Safe reason is returned without internal-only reason leakage.
- Existing admin block/unblock tests still pass.

## Slice 2 - Feature Flags Enforced For Real

Flags already discussed include:
- `auth.registration`
- `import.local`
- `import.archive`
- `import.github`
- `cli.push`
- `api_keys.create.global`
- `mcp.enabled`
- MCP child flags, e.g. `mcp.tool.project_context`, `mcp.tool.graph_query`
- `usecase.generate`

Backend work:
- Feature flag service must be consulted before every controlled operation.
- Disabled feature returns structured error, recommended code: `FEATURE_DISABLED` or specific existing code if already present.
- Do not silently no-op.
- Add tests proving disabled flags block real endpoints/tools.

Acceptance:
- Toggling a flag affects the relevant backend operation.
- All feature flag reads are fail-safe. If flag storage cannot be read, choose the safer behavior for production-sensitive operations.

## Slice 3 - API Key Project Binding

User decision:
- API key is project identity. For MVP, creating an API key should require a selected project/repository.

Backend work:
- Add nullable-to-required migration strategy carefully. Existing keys may need migration compatibility.
- `api_keys` should include project id/default project id.
- API key create request includes `projectId`.
- API key responses include project id/name/status.
- User can only bind keys to their own project.
- Admin-created keys for a user can only bind to a project owned by that user.
- API key use is restricted to its project. Reject project override to a different project for MVP.

CLI/MCP implications:
- API key auth resolves the project context.
- CLI/MCP commands using an API key should not require project id for project-bound operations.
- If a command/tool supplies a different project id than the key binding, backend rejects.

Acceptance:
- Cannot create key without valid owned project.
- Cannot use key for another project.
- List keys never returns raw secret.
- One-time secret behavior remains intact.

## Slice 4 - MCP Child Tool Toggles

Requirements:
- `mcp.enabled` disables all MCP tools.
- Each MCP child tool has its own flag.
- Backend checks global MCP flag first, then child tool flag.
- Disabled child tool returns clear structured error.

Work:
- Inventory actual MCP tools in backend.
- Create/seed flags for each MCP tool.
- Add tests per at least two tools and global disable.

Acceptance:
- Global disable blocks all tools.
- Tool-specific disable blocks only that tool.
- No ownership bypass via MCP project id.

## Slice 5 - Concurrent Import Guard

Requirement:
- Prevent abuse where one user opens many tabs and imports/uploads many projects concurrently.
- Local/dev can disable this guard for testing; production should enable it.

Work:
- Add configurable per-user concurrent import limit. Suggested default: 1 active import per user.
- Apply to local import, archive import, GitHub/tarball import, and any storage-heavy import path.
- Ensure lock is released on success/failure.
- Return clear structured error, e.g. `CONCURRENT_IMPORT_LIMIT`.
- Add tests for concurrent attempts.

Acceptance:
- Second concurrent import for same user is rejected while first is active.
- Different users are not blocked by each other.
- Guard can be disabled in dev/test config.

## Slice 6 - Request Monitoring, Rate Limit, IP Block

Requirements:
- Admin must see if a user/IP is spamming or DoS-like.
- Track requests per minute by user/IP/API key.
- Support exact IP block with reason and optional expiration.

Work:
- Add request/security event capture with sanitized metadata:
  - user id/email if authenticated
  - API key prefix/id if API key auth
  - IP
  - user-agent
  - method/path family, not sensitive query/body
  - status/outcome
  - timestamp
- Add rate-limit policy. MVP can use global or per-plan limits if plan data is easy to use.
- Exceeding limit returns `429 TOO_MANY_REQUESTS`.
- Add admin APIs for security overview/listing events/IP blocks.
- Exact IP block first. CIDR is out of MVP unless supervisor approves.
- Trust forwarded IP headers only from known proxy configuration; do not blindly trust client-provided headers.

Acceptance:
- Admin can list request/security events.
- Exact IP block rejects requests with `IP_BLOCKED`.
- Audit/security logs do not store secrets.

## Slice 7 - Audit Log + Retention

Requirements:
- Audit logs record important actions.
- Retention policy managed in Admin Settings.
- Viewing audit logs belongs in Admin Security.

Must log:
- admin block/unblock/deactivate user
- admin plan/quota/credit updates
- admin API key creation disabled/enabled
- admin disables key
- plan/pricing CRUD
- feature flag toggles
- announcement create/disable
- admin login success/failure
- report close/reply

Work:
- Add `audit_logs` and audit setting/policy if absent.
- Add retention scheduler, default 90 days.
- Add admin APIs for retention policy and audit log list/detail.
- Sanitize metadata.

Acceptance:
- Logs exist for high-risk admin operations.
- Retention update is audited.
- Scheduler deletes/marks eligible old logs according to policy.

## CLI Notes

Update `vibegraph-cli` only as needed for API key project context:
- Add/adjust API key auth flow.
- Key-bound project commands should not require project id.
- Keep backward-compatible `projects` commands if already shipped; `repos` aliases are optional unless low risk.
- Never print full API keys after input; show prefix only.

## Required Tests

Run focused tests first, then full:
- `./mvnw -Dtest=*FeatureFlag*,*ApiKey*,*Mcp*,*Import*,*Audit*,*Security*,*Admin* test`
- `./mvnw clean test`
- CLI smoke tests if CLI changed.

## Handoff Format

Write:
- exact files changed
- migrations added
- endpoint/API contract summary
- test commands and results
- unresolved risks
- whether frontend can proceed against final contracts

