# Claude Handoff - Phase 6 Slice 1

Status: COMPLETE (backend only)  
Date: 2026-07-16

## Implemented

- Preserved restricted-login behavior: valid credentials for blocked/deactivated accounts return a restricted JWT with `accountStatus` and safe reason; invalid credentials stay generic.
- Expanded `GET /api/account/session-state` to return `id`, `email`, `displayName`, `role`, `accountStatus`, and `safeReason` without extra quota/credit/announcement queries.
- Kept the restricted-account HTTP allowlist narrow and added route-level coverage for local/archive/GitHub import, local patch/CLI push, analyze, API-key creation, and MCP transport.
- Added analyze defense-in-depth: ownership first, then blocked-state and minimum-credit preflight before analysis; exact node-aware credit debit remains after successful analysis.
- Moved GitHub blocked-account enforcement before GitHub preflight/network work and restored minimum-credit preflight plus exact post-analysis metering.
- Added realtime enforcement for already-open sessions:
  - `/ws/**` is only the public transport handshake.
  - STOMP `CONNECT` requires native header `Authorization: Bearer <JWT>`.
  - blocked/deactivated/missing users are rejected fail-closed.
  - project `SUBSCRIBE` enforces ownership.
  - outbound `/topic/projects/{projectId}/updates|status` is revalidated for account status and the session's authorized project, so future messages are suppressed after block/deactivation.
  - session authorization is removed on `DISCONNECT`.
- Preserved MCP blocked-account ordering and fixed stale test/config wiring. The fallback Jackson mapper now registers discovered modules instead of publishing a plain mapper.
- Added node/minimum-aware credit-pricing overload while preserving existing three-argument callers.

## Final contracts

### Login

`POST /api/auth/login`

- Correct blocked/deactivated credentials: `200`, restricted token, safe user projection.
- Wrong password/unknown/OAuth-only local login: generic `401 INVALID_CREDENTIALS`; no blocked status disclosure.

### Session state

`GET /api/account/session-state`

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "email": "user@example.com",
    "displayName": "User",
    "role": "USER",
    "accountStatus": "ACTIVE | BLOCKED | DEACTIVATED",
    "safeReason": null
  },
  "error": null
}
```

Blocked/deactivated users may call this route and the narrow feedback/support routes. Product routes return structured `403 ACCOUNT_BLOCKED` using only the safe reason.

### Realtime STOMP

- Transport endpoint: `/ws/graph-updates` (SockJS).
- STOMP `CONNECT` must include native header:

```text
Authorization: Bearer <JWT>
```

- Subscribe only to owned project topics:
  - `/topic/projects/{projectId}/updates`
  - `/topic/projects/{projectId}/status`

Frontend integration note: Kiro must set the STOMP client's `connectHeaders.Authorization` to the current bearer token. No frontend file was changed in this slice.

## Files changed for Slice 1

Production:

- `src/main/java/com/vibegraph/auth/config/SecurityConfig.java`
- `src/main/java/com/vibegraph/auth/dto/AccountSessionStateResponse.java`
- `src/main/java/com/vibegraph/auth/service/AccountAccessGuard.java`
- `src/main/java/com/vibegraph/auth/service/CreditPricingService.java`
- `src/main/java/com/vibegraph/auth/websocket/RealtimeAccountAccessInterceptor.java`
- `src/main/java/com/vibegraph/common/config/McpServerConfig.java`
- `src/main/java/com/vibegraph/common/config/WebSocketConfig.java`
- `src/main/java/com/vibegraph/graph/controller/ProjectController.java`
- `src/main/java/com/vibegraph/graph/service/impl/TarballImportServiceImpl.java`

Tests/wiring:

- `src/test/java/com/vibegraph/auth/service/AccountAccessGuardTest.java`
- `src/test/java/com/vibegraph/auth/service/AccountServiceTest.java`
- `src/test/java/com/vibegraph/auth/service/CreditPricingServiceTest.java`
- `src/test/java/com/vibegraph/auth/web/AccountControllerTest.java`
- `src/test/java/com/vibegraph/auth/web/JwtAuthFilterTest.java`
- `src/test/java/com/vibegraph/auth/websocket/RealtimeAccountAccessInterceptorTest.java`
- `src/test/java/com/vibegraph/graph/controller/ProjectControllerTest.java`
- `src/test/java/com/vibegraph/graph/integration/ProjectApiIT.java`
- `src/test/java/com/vibegraph/graph/service/impl/TarballImportServiceImplTest.java`
- `src/test/java/com/vibegraph/mcp/McpCreditMeteringTest.java`
- `src/test/java/com/vibegraph/mcp/tool/McpToolsTest.java`
- `src/test/java/com/vibegraph/patch/controller/LocalPatchControllerTest.java`

## Migrations

None.

## Verification

Passed:

- Focused blocked/session/realtime/analyze/import/API-key/MCP test groups.
- `./mvnw clean test` (final run: BUILD SUCCESS).
- `./mvnw verify -q` reached Docker/Testcontainers using `npipe:////./pipe/dockerDesktopLinuxEngine` with no failure markers in captured output.
- `./mvnw verify -DskipTests` completed build, merged JaCoCo data, and reported all coverage checks met.
- Spring security context check: `AdminSecurityIT`, `JwtAuthFilterTest`, and `RealtimeAccountAccessInterceptorTest` passed (26 tests).

Runtime observation against an isolated Postgres database and backend on port 18080:

- health returned `UP`;
- active session-state returned the final six-field contract;
- after blocking the user directly in the control-plane DB, the existing JWT immediately received `403 ACCOUNT_BLOCKED` on `GET /api/projects`;
- the same JWT still read session-state as `BLOCKED`;
- valid login returned a restricted token and only `Contact support for account review`;
- the injected internal reason `INTERNAL_RUNTIME_REASON_DO_NOT_EXPOSE` did not appear in responses.

The isolated backend was stopped and verification database/file were removed.

## GitNexus

- Required pre-edit impacts were run. Most Slice 1 symbols were LOW/MEDIUM; new/untracked symbols were absent from the stale index.
- `CreditPricingService.calculateCredits` reported CRITICAL blast radius (six direct callers/five flows), so the change was implemented compatibly: existing three-argument calls retain their prior behavior, and a four-argument node/minimum-aware overload is used by analyze/GitHub.
- Final `detect_changes(scope=all)` reported CRITICAL globally because the shared working tree contains 712 changed symbols across 183 files (224 modified + 79 untracked at final inspection). This global risk is noisy and includes extensive unrelated work; no reset/stash was performed.

## Remaining risks / limitations

- The working tree was heavily dirty before this slice. Review/integration should isolate the listed files rather than treating global diff risk as Slice 1-only.
- Exact post-analysis debit can still lose a race if credits are consumed concurrently after the minimum preflight. Atomic debit fails safely, and no full reservation/refund protocol was introduced in this slice.
- API-key request authentication/use does not exist in the current backend. Slice 1 covers API-key creation and JWT/MCP product boundaries; project-bound API-key use belongs to Slice 3.
- Frontend must adopt the documented STOMP `CONNECT` bearer header before realtime works with the hardened backend contract.

## Frontend readiness

REST contracts are final for Slice 1. Frontend can poll `/api/account/session-state`, route blocked users to the safe support surface, and use login `user.accountStatus`/`safeReason`. Realtime requires the one documented integration update (`connectHeaders.Authorization`) by the frontend owner.

No frontend changes, commit, push, or merge were performed. No later Phase 6 slice was started.
