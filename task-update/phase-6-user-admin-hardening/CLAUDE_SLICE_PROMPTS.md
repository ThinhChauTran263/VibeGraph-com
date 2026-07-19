# Claude Slice Prompts

Use these one at a time if Claude's context is too small. Do not paste all slices at once.

## Slice 1 - Blocked Account / Session State

```text
Tiếp tục VibeGraph Phase 6 Backend.

Chỉ làm Slice 1:
- blocked account realtime/session-state
- login blocked account shows safe reason
- existing logged-in blocked account loses product access quickly
- product endpoints reject blocked accounts

Đọc:
task-update/phase-6-user-admin-hardening/README.md
task-update/phase-6-user-admin-hardening/CLAUDE_BACKEND_CLI_MCP.md

Không làm feature flags/API key/MCP/rate-limit/audit ở slice này.
Không commit/push.
Ghi handoff vào CLAUDE_HANDOFF_SLICE1.md.
```

## Slice 2 - Feature Flags Enforcement

```text
Tiếp tục VibeGraph Phase 6 Backend.

Chỉ làm Slice 2:
- backend enforce feature flags thật
- controlled flags: registration, import.local, import.archive, import.github, cli.push, api_keys.create.global, mcp.enabled, usecase.generate
- disabled feature returns structured error
- tests proving disabled flags block real operations

Không làm API key project binding/MCP child tools/rate-limit/audit ở slice này.
Không commit/push.
Ghi handoff vào CLAUDE_HANDOFF_SLICE2.md.
```

## Slice 3 - API Key Project Binding

```text
Tiếp tục VibeGraph Phase 6 Backend.

Chỉ làm Slice 3:
- API key must bind to one owned project for MVP
- create request includes projectId
- list response includes project info
- backend rejects key use for other projects
- admin-created keys also validate target user's project ownership
- preserve one-time secret behavior

Không làm CLI/MCP child toggles/rate-limit/audit ở slice này.
Không commit/push.
Ghi handoff vào CLAUDE_HANDOFF_SLICE3.md.
```

## Slice 4 - MCP Child Tool Toggles + Key Context

```text
Tiếp tục VibeGraph Phase 6 Backend/CLI/MCP.

Chỉ làm Slice 4:
- mcp.enabled disables all MCP tools
- each MCP tool has child flag
- backend checks global then child flag
- MCP/API-key auth resolves project context from project-bound key
- reject project override to a different project
- update CLI only if needed for key-bound project context

Không làm rate-limit/IP block/audit ở slice này.
Không commit/push.
Ghi handoff vào CLAUDE_HANDOFF_SLICE4.md.
```

## Slice 5 - Concurrent Import Guard

```text
Tiếp tục VibeGraph Phase 6 Backend.

Chỉ làm Slice 5:
- configurable per-user concurrent import limit
- default production limit: 1 active import per user
- apply to local/archive/GitHub or tarball import paths
- release lock on success/failure
- return structured CONCURRENT_IMPORT_LIMIT error
- tests for concurrent attempts

Không làm rate-limit/IP block/audit ở slice này.
Không commit/push.
Ghi handoff vào CLAUDE_HANDOFF_SLICE5.md.
```

## Slice 6 - Request Monitoring / Rate Limit / IP Block

```text
Tiếp tục VibeGraph Phase 6 Backend.

Chỉ làm Slice 6:
- request/security event monitoring by user/IP/API key
- requests per minute metrics
- rate limit returns 429 TOO_MANY_REQUESTS
- exact IP block with reason and optional expiration
- admin APIs for request monitor and IP block/watchlist
- do not trust spoofed forwarding headers unless configured proxy is trusted

Không làm audit retention in this slice unless needed for event logging.
Không commit/push.
Ghi handoff vào CLAUDE_HANDOFF_SLICE6.md.
```

## Slice 7 - Audit Logs + Retention

```text
Tiếp tục VibeGraph Phase 6 Backend.

Chỉ làm Slice 7:
- audit_logs for high-risk admin/user actions
- retention policy default 90 days
- admin APIs for retention setting and audit log list/detail
- scheduler removes/marks logs older than retention
- sanitize metadata, never log secrets/tokens/passwords/API key raw secret/source file content

Không commit/push.
Ghi handoff vào CLAUDE_HANDOFF_SLICE7.md.
```

