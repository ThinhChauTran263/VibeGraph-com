# CodexCli CLI API Key Handoff

Status: API-key-first CLI integration is complete in the shared dirty worktree. No commit, push, merge, reset, checkout, or revert was performed. Parallel-agent changes were preserved and overlapping backend test fixes were merged in place.

## Exact files changed in this scope

CLI implementation and tests:

- `vibegraph-cli/bin/vibegraph.js`
- `vibegraph-cli/lib/project-target.js` (new)
- `vibegraph-cli/lib/push.js`
- `vibegraph-cli/lib/watch.js`
- `vibegraph-cli/test/api.test.js` (new)
- `vibegraph-cli/test/auth.test.js` (new)
- `vibegraph-cli/test/push.test.js`
- `vibegraph-cli/test/shell.test.js`

Documentation:

- `vibegraph-cli/README.md`
- `docs/local-patch.md`

Backend current-project endpoint and focused tests:

- `src/main/java/com/vibegraph/patch/controller/LocalPatchController.java`
- `src/test/java/com/vibegraph/patch/controller/LocalPatchControllerTest.java`

Parallel-overlap compilation/test repairs needed by the shared backend gate:

- `src/main/java/com/vibegraph/auth/repository/ApiKeyRepository.java`
  - Restored missing `Modifying`, `Query`, and `Param` imports for parallel lifecycle changes.
- `src/test/java/com/vibegraph/auth/web/AccountApiKeyControllerTest.java`
  - Restored the missing `ApiKeyAdminLockedException` import.
- `src/test/java/com/vibegraph/auth/service/ApiKeyServiceTest.java`
  - Removed six obsolete Mockito stubs after the parallel service contract changed.
- `src/test/java/com/vibegraph/auth/web/ApiKeyAuthFilterTest.java`
  - Removed a trailing blank line reported by the diff gate.

This handoff file is also new:

- `task-update/api-key-lifecycle-supervisor/CodexCli_CLI_API_KEY_HANDOFF.md`

## New command contract

API-key configuration:

```text
vibegraph auth set-key <apiKey>
vibegraph auth status
vibegraph auth clear
```

- The key is stored in the existing VibeGraph config directory (`~/.vibegraph/config.json` by default) with restrictive file permissions.
- `VIBEGRAPH_API_KEY` overrides the stored key.
- `VIBEGRAPH_API_URL` overrides the stored/default API URL.
- User-facing config/status output only displays a masked value such as `vbg_abcd...wxyz`; the raw key is never logged.

Project-bound API-key workflow:

```text
vibegraph push --root <path> [--dry-run]
vibegraph watch --root <path>
```

- These commands resolve the project from the configured API key and send patches to `POST /api/projects/current/patch`.
- Requests send `X-API-Key: <raw key>` and do not send `Authorization: Bearer ...` when an API key is available.
- Root-only push/watch require a project-bound API key and fail with an actionable message when no key is configured.
- Snapshot identity uses a SHA-256 fingerprint of the API key, never the raw key.

Doctor behavior:

- Checks backend health.
- Reports whether an API key is configured without revealing it.
- When a key is configured, sends an empty dry-run patch to `/api/projects/current/patch` to distinguish an active key from disabled, locked, or invalid credentials.

Interactive shell suggestions now include:

- `/help`
- `auth set-key `
- `auth status`
- `auth clear`
- `push --root `
- `watch --root `

The existing slash-triggered suggestion behavior remains intact.

## Backend dependency

The backend now provides `POST /api/projects/current/patch`. `LocalPatchController` reads the project ID from `ApiKeyRequestContext`, which is populated by the API-key filter, and delegates to the same patch application path as the legacy route. The endpoint fails closed with `401` when no API-key request context is present.

This endpoint is required for project-ID-free CLI commands because the API key is the project/repository identity. Backend API-key authentication remains intentionally restricted to MCP and patch routes.

## Compatibility note

The legacy commands remain supported:

```text
vibegraph projects push <projectId> --root <path> [--dry-run]
vibegraph watch <projectId> --root <path>
```

- If an API key is configured, legacy project-ID commands still prefer `X-API-Key` and omit Bearer auth.
- If no API key is configured, legacy commands can fall back to the existing JWT token and print a clear compatibility/dev warning.
- Existing `login`, `register`, and `me` commands remain for backward compatibility and development; the recommended product workflow is API-key-first.

## Tests and verification

- CLI baseline before this work: `37` passed, `1` skipped.
- CLI required gate: PASS
  - `npm --prefix vibegraph-cli test`
  - `45` passed, `0` failed, `1` skipped (`46` total).
  - Tests cover masked persistence/output, clear/status behavior, environment overrides, `X-API-Key` precedence, Bearer omission, doctor authentication, project-bound push parsing/request routing, shell suggestions, and existing scanner/ignore/snapshot/push behavior.
  - No real backend/network is used by the focused CLI tests.
- Backend focused gate: PASS
  - `.\mvnw.cmd "-Dtest=*ApiKey*,*LocalPatch*,*Mcp*" test`
  - `142` passed, `0` failures, `0` errors.
- Backend full clean gate: PASS
  - `.\mvnw.cmd clean test`
  - `818` tests run, `0` failures, `0` errors, `9` skipped.
- `LocalPatchControllerTest`: `7` passed, including current-project resolution and missing-context fail-closed coverage.
- `git diff --check`: PASS. Git only emitted expected LF/CRLF conversion warnings for the shared Windows worktree.

## Shared-worktree risk note

GitNexus aggregate change detection reported CRITICAL because all parallel agents together changed approximately 75 files, 346 symbols, and 42 execution flows. Scope-level impact checks for this CLI/current-patch work were LOW, except the parallel `ApiKeyRepository` surface at MEDIUM. The aggregate rating reflects the intentionally shared dirty worktree, not a CLI-only critical blast radius.

No commit, push, or merge was performed.

## Droid Request-Changes Resolution (2026-07-18)

- **H3 ANSI-sensitive CLI gate: FIXED.** Interactive-header assertions strip ANSI control sequences before matching, making the test deterministic under inherited color settings.
- **API-key/Bearer regression: FIXED.** The API client already selected exactly one credential; the regression now checks both current-project and explicit-project push endpoints use `X-API-Key` and omit `Authorization` whenever a key exists.
- **L1 snapshot permission risk: NOT FIXED (accepted MVP).** Snapshots contain no raw API key; Windows does not reliably enforce POSIX mode bits. Config secrets continue to use restrictive file permissions.

Verification: `npm --prefix vibegraph-cli test` PASS — 45 passed, 0 failed, 1 platform skip. No commit/push/merge.