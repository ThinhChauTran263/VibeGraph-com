# Phase 7 Backend - Agent Assignments

Base branch: `poc`
Supervisor: Codex

## Roster

| Agent name | Role |
| --- | --- |
| `CladueCli` | BE-1 Auth / Block / Session |
| `ClaudeChat` | BE-2 Feature Flags / System Controls |
| `Droid` | BE-3 Credit / Quota |
| `Kiro` | BE-4 Anti-Abuse / Rate Limit / IP Block |
| `CodexCli` | BE-5 Reports / Announcements / Audit / Overview |
| `gemini` | BE-6 Integration Reviewer |

## Operating rules

- Each agent gets its own branch or worktree from latest `poc`.
- Do not commit, push, or merge unless supervisor explicitly approves.
- Do not edit frontend UI in this phase.
- Do not revert unrelated changes from other agents.
- Run GitNexus impact before editing any Java symbol.
- Write a handoff file in `task-update/phase-7-backend-supervisor/`.

## Prompts

### CladueCli - BE-1 Auth / Block / Session

```text
You are CladueCli, working on VibeGraph Phase 7 backend.

Scope: Auth / Block / Session only.
Base branch: latest poc.
Do not commit, push, or merge.

Read first:
- AGENTS.md
- RULES.md
- task-update/phase-7-backend-supervisor/README.md

Goal:
Make blocked/deactivated account behavior complete and immediate across REST and realtime, while preserving web HttpOnly cookie auth and CLI Bearer-token compatibility.

Must implement or verify:
- Web auth uses HttpOnly cookie `vg_session`.
- CLI/API clients can still use `Authorization: Bearer <jwt>`.
- Blocked/deactivated users cannot access product flows:
  - import local/archive/GitHub
  - CLI push/local patch
  - analyze
  - API key create
  - MCP calls
  - graph/source/project product routes
- Blocked/deactivated users can still access a safe support/report surface if already allowed by product decision.
- Existing JWTs are checked against current account status on every request.
- STOMP/WebSocket CONNECT/SUBSCRIBE/SEND checks account status and ownership.
- Login for blocked/deactivated account returns safe reason contract without leaking internal admin reason.
- Logout clears cookie.

Do not work on:
- Feature flag enforcement beyond account status guard hooks.
- Credit/quota/rate-limit/IP block/audit/announcement UI.
- Frontend UI.

Acceptance criteria:
- Blocked user with old JWT immediately receives structured `ACCOUNT_BLOCKED` for product APIs.
- Blocked login receives safe reason, no internal reason leakage.
- Deactivated user receives structured account disabled response.
- STOMP events are not delivered to blocked/deactivated users after status changes.
- Tests prove REST and STOMP behavior.

Required tests:
- `./mvnw "-Dtest=*Auth*,*Blocked*,*Account*,*RealtimeAccountAccess*" test`

Handoff:
Write `task-update/phase-7-backend-supervisor/CladueCli_HANDOFF.md`
```

### ClaudeChat - BE-2 Feature Flags / System Controls

```text
You are ClaudeChat, working on VibeGraph Phase 7 backend.

Scope: Feature Flags / System Controls only.
Base branch: latest poc.
Do not commit, push, or merge.

Read first:
- AGENTS.md
- RULES.md
- task-update/phase-7-backend-supervisor/README.md

Goal:
Make feature flags real enforcement controls, not only admin CRUD.

Must implement or verify:
- Admin CRUD for feature flags exists and is protected by ROLE_ADMIN.
- Disabled features are enforced in backend before expensive work starts.
- Disabled feature returns structured `FEATURE_DISABLED` with safe message.
- User-facing operations must be disabled logically, not silently no-op.

Required flags:
- `registration`
- `api_keys.create.global`
- `cli.push`
- `import.local`
- `import.archive`
- `import.github`
- `project.analyze`
- `mcp.enabled`
- each MCP child tool flag, for example `mcp.tool.<toolName>`
- `usecase.generate`

MCP child tool behavior:
- Check `mcp.enabled` first.
- Then check `mcp.tool.<toolName>`.
- If either disabled, reject before credit metering or resource-heavy work.

Do not work on:
- Credit pricing/debit logic except ordering around flag checks.
- Request rate/IP block.
- Frontend UI.

Acceptance criteria:
- Turning off each flag blocks the real backend operation.
- Disabled operation returns deterministic structured error.
- MCP global and child flags are both tested.
- Flag checks happen before credit debit and before heavy filesystem/network work.
- Existing enabled behavior remains unchanged.

Required tests:
- `./mvnw "-Dtest=*FeatureFlag*,*Import*,*Analyze*,*ApiKey*,*Mcp*" test`

Handoff:
Write `task-update/phase-7-backend-supervisor/ClaudeChat_HANDOFF.md`
```

### Droid - BE-3 Credit / Quota

```text
You are Droid, working on VibeGraph Phase 7 backend.

Scope: Credit / Quota only.
Base branch: latest poc.
Do not commit, push, or merge.

Read first:
- AGENTS.md
- RULES.md
- task-update/phase-7-backend-supervisor/README.md

Goal:
Make source-storage quota and credits correct, race-safe, and easy to change from database/admin.

Product decisions:
- Quota unit shown to user/admin is MB.
- Source storage quota measures imported source storage, not graph DB size.
- Plans:
  - FREE: 100 MB + 100 credits
  - PRO: 500 MB + 500 credits
  - PRO_PLUS: 1024 MB + 1000 credits
  - MAX: 2048 MB + 2000 credits
  - ENTERPRISE: contact sales required; admin can override quota/credits per user
- Admin can set per-user plan, storage quota override, credit quota override.
- Admin validation must prevent storage quota override below current used MB.
- Credit reset follows registration day monthly cycle, with clamp for short months.
- Credit deduction applies only to MCP and CLI flows unless explicitly approved later.

Pricing rule formula:
`cost = ceil(base + perFile * fileCount + perMb * sizeMb)`

Default pricing:
- MCP_TOOL_CALL: base 1, perFile 0, perMb 0
- CLI_PUSH: base 1, perFile 0.1, perMb 0
- PROJECT_ANALYZE: base 5, perFile 0.01, perMb 1
- IMPORT_ARCHIVE: base 3, perFile 0, perMb 1
- IMPORT_GITHUB: base 3, perFile 0, perMb 1

Must implement or verify:
- Atomic/race-safe credit debit.
- Atomic/race-safe admin credit adjustment and quota update.
- No lost update between debit and admin adjustment.
- Ledger remains consistent with balance.
- Local import/source measurement is fail-closed.
- Size math uses long/BigDecimal safely, no overflow.
- Symlinks/path traversal do not inflate or escape measurement.
- Quota exceeded returns `QUOTA_EXCEEDED`.
- Credit exhausted returns `CREDIT_EXHAUSTED`.

Do not work on:
- Feature flag CRUD/enforcement except respecting existing guards.
- Rate-limit/IP block.
- Frontend UI.

Acceptance criteria:
- Concurrent debits cannot overspend.
- Concurrent debit + admin adjustment cannot lose updates.
- Admin quota override below current source storage usage is rejected.
- Monthly reset by registration day is deterministic.
- Quota uses MB in API/admin-facing values.
- Tests include PostgreSQL/concurrency where possible.

Required tests:
- `./mvnw "-Dtest=*Credit*,*Quota*,*Plan*,*Pricing*,*LocalImport*,*LocalPatch*" test`

Handoff:
Write `task-update/phase-7-backend-supervisor/Droid_HANDOFF.md`
```

### Kiro - BE-4 Anti-Abuse / Rate Limit / IP Block

```text
You are Kiro, working on VibeGraph Phase 7 backend.

Scope: Anti-Abuse / Rate Limit / IP Block only.
Base branch: latest poc.
Do not commit, push, or merge.

Read first:
- AGENTS.md
- RULES.md
- task-update/phase-7-backend-supervisor/README.md

Goal:
Give admin visibility and controls for abuse: request volume, suspicious users, concurrent imports, and exact IP blocking.

Product decisions:
- Admin wants to see user request rate per minute by user/IP/API key.
- Admin wants to detect DOS/DDOS/spam-like behavior.
- MVP IP block is exact IP first. CIDR is out unless explicitly approved later.
- If user is shown with IP, UI can reduce accidental block risk.
- Concurrent import guard should prevent one user opening 10 tabs and importing 10 projects at once.
- Production default for concurrent import: 1 active import per user.
- In local/test, it must be configurable for testing.

Must implement or verify:
- Request monitoring records/aggregates:
  - userId if authenticated
  - API key id/prefix if applicable
  - IP address
  - route/method/status
  - timestamp
  - requests per minute
- Do not trust spoofed `X-Forwarded-For` unless app is configured to trust a proxy.
- Rate limit returns 429 with `TOO_MANY_REQUESTS`.
- IP block returns structured `IP_BLOCKED` with safe reason.
- Admin APIs:
  - list request/security events
  - list top request users/IPs
  - create/update/remove IP block
  - optional expiration for IP block
- Concurrent import lock applies to local/archive/GitHub import.
- Lock releases on success, failure, validation failure, and exception.

Do not work on:
- Credit debit formula except ensuring rate/import guard happens before heavy work where appropriate.
- Announcements/reports/audit retention except event logging needed for abuse.
- Frontend UI.

Acceptance criteria:
- Opening concurrent import attempts beyond limit returns `CONCURRENT_IMPORT_LIMIT`.
- IP block blocks unauthenticated and authenticated requests from exact IP.
- Rate limit cannot be bypassed with spoofed forwarding header in default config.
- Admin can view enough data to identify abusive user/IP.
- Tests cover authenticated, unauthenticated, API-key/MCP/CLI where applicable.

Required tests:
- `./mvnw "-Dtest=*RateLimit*,*SecurityEvent*,*IpBlock*,*Import*,*Abuse*" test`

Handoff:
Write `task-update/phase-7-backend-supervisor/Kiro_HANDOFF.md`
```

### CodexCli - BE-5 Reports / Announcements / Audit / Overview

```text
You are CodexCli, working on VibeGraph Phase 7 backend.

Scope: Reports / Announcements / Audit / Admin Overview only.
Base branch: latest poc.
Do not commit, push, or merge.

Read first:
- AGENTS.md
- RULES.md
- task-update/phase-7-backend-supervisor/README.md

Goal:
Complete support communication, in-app announcements/notifications, audit logs, and optimized admin overview APIs.

Reports:
- User can create report/feedback.
- User and admin can reply back and forth in a thread.
- User or admin can close report.
- Closed report shows `deleteAfter`.
- Cleanup treats deletion as eligible after 7 days from close, not guaranteed immediate.
- Report thread realtime should notify subscribed user/admin.

Announcements / Notifications:
- Admin can create announcements for:
  - maintenance
  - plan changes
  - disk warning
  - CLI version update
  - security notice
  - general notice
- User receives active announcement after successful login / app load.
- User notification APIs:
  - list notifications newest first
  - detail by id
  - mark read
  - dismiss
- API response should include title, body, creator display/email safe projection, createdAt, severity/type.
- Do not store notification state only in localStorage.

Audit:
- Audit important actions:
  - login/logout
  - failed login
  - block/unblock/deactivate
  - plan/quota/credit update
  - API key create/disable
  - feature flag change
  - IP block/unblock
  - report close/admin reply
- Never log secrets, passwords, raw JWTs, raw API key secrets, private source content.
- Retention default: 90 days unless existing config says otherwise.
- Admin APIs for audit list/detail and retention setting.

Admin Overview:
- Use database aggregate/top-N queries, not loading all users/projects/ledger into memory.
- Return data for:
  - total users
  - online users
  - imported project/repository count
  - credit usage by day/month/quarter/year as available
  - system storage summary
  - plan distribution
  - top storage projects/users
  - security/abuse alerts summary
- Polling-friendly response. FE may poll every 15-30 seconds until websocket/SSE later.

Do not work on:
- Feature flag enforcement except emitting audit when flags change if already available.
- Credit debit/rate limit internals except reading aggregate data.
- Frontend UI.

Acceptance criteria:
- Reports realtime events are authorized by report owner/admin.
- Announcement notifications are persisted per user state, not only localStorage.
- Audit logs redact sensitive fields.
- Admin overview does not use unbounded findAll for large tables.
- Report cleanup tests prove 7-day eligibility.

Required tests:
- `./mvnw "-Dtest=*Feedback*,*Report*,*Announcement*,*Notification*,*Audit*,*AdminOverview*" test`

Handoff:
Write `task-update/phase-7-backend-supervisor/CodexCli_HANDOFF.md`
```

### gemini - BE-6 Integration Reviewer

```text
You are gemini, the integration reviewer for VibeGraph Phase 7 backend.

Scope: Review, merge gate, conflict resolution guidance, final report.
Base branch: poc plus worker branches/handoffs.
Do not implement new product features unless fixing a merge blocker.
Do not push/merge unless supervisor explicitly asks.

Read first:
- AGENTS.md
- RULES.md
- task-update/phase-7-backend-supervisor/README.md
- all BE-*_HANDOFF.md files that exist

Goal:
Verify BE-1 through BE-5 are correct, secure, non-overlapping, and mergeable.

Review order:
1. BE-1 Auth / Block / Session
2. BE-2 Feature Flags / System Controls
3. BE-3 Credit / Quota
4. BE-4 Anti-Abuse / Rate Limit / IP Block
5. BE-5 Reports / Announcements / Audit / Overview

For each worker:
- Inspect git diff.
- Check changed files are in scope.
- Run their focused tests.
- Run security review for auth/authorization/race/secrets.
- Identify CRITICAL/HIGH/MEDIUM findings.
- Do not let a CRITICAL/HIGH finding pass unresolved.
- Compare handoff notes against the actual diff.
- Flag any overlapping file ownership between workers.
- If two workers touched the same file, verify the edits are orthogonal and safe to merge.
- If overlap is risky, stop and propose merge order instead of guessing.
- Verify the worker did not silently expand scope into frontend or unrelated backend areas.

Final full gate:
```bash
./mvnw clean test
./mvnw clean verify
npx gitnexus detect_changes --repo VibeGraph-com
git diff --check
git status --short --branch
```

Security checklist:
- No hardcoded secrets, JWTs, passwords, raw API keys.
- No token/password/source content in audit logs.
- No unbounded admin overview queries.
- No missing ownership checks for project/report/API-key/MCP operations.
- No race in credit debit/admin adjustment/quota override.
- No spoofed IP trust without trusted proxy config.
- Feature flags actually block real operations.
- Blocked/deactivated users cannot use product flows even with old JWT.
- Hidden no-op behavior is not acceptable for disabled features.
- Any leftover TODO or stub must be called out explicitly.

Output:
Write `task-update/phase-7-backend-supervisor/BE-6_FINAL_REVIEW.md` with:
- PASS/REQUEST CHANGES verdict
- merged/pending worker branches
- findings by severity
- tests run and exact results
- GitNexus risk summary
- final backend contract summary for frontend agents
```
