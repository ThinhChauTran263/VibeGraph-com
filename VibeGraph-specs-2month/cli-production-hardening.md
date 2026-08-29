# CLI Production Hardening

## Objective

Make `vibegraph-cli` safe and predictable for production API-key push/watch workflows while
retaining JWT project-management compatibility.

## Authentication Contract

- Project-bound API keys authenticate MCP and patch/watch only.
- Project management remains JWT-authenticated to avoid broadening API-key privileges.
- CLI login stores the rotating refresh cookie, refreshes one expired access token, and revokes the
  refresh session on logout when the backend is reachable.
- Secrets can be read from stdin or environment variables and are never printed in full.

## Push And Watch Safety

- Missing, unreadable, truncated, binary, oversized, empty, or otherwise uncertain Java scans fail
  closed before a patch or deletion is sent.
- Empty-project and large deletion batches require explicit push overrides; watch never applies a
  destructive batch automatically.
- Watch serializes requests, remembers events received during an active push, and performs an
  initial sync instead of silently establishing a baseline.
- Client file-count, file-size, and total-byte defaults match backend patch limits.

## Reliability And Distribution

- HTTP requests use a configurable timeout; idempotent reads retry transient failures.
- Config and snapshot writes are atomic, and snapshot IDs reject path traversal.
- Production API URLs require HTTPS, with localhost HTTP allowed for development.
- The package exposes version/help commands, strict option parsing, an isolated smoke test, a lock
  file, public npm metadata, and IDE/MCP setup documentation.

## Verification

- CLI unit/regression tests, hermetic smoke test, dependency audit, package dry-run, diff check, and
  GitNexus change detection must pass before release.
