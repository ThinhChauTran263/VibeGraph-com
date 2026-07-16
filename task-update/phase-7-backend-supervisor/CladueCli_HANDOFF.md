# BE-1 Handoff — Auth / Block / Session

## Status

Implementation was attempted on branch `poc` without commit, push, or merge. The worktree already contained unrelated Phase 7 changes before this task; those were not reverted.

## Files changed by this task

- `src/main/java/com/vibegraph/auth/service/AccountAccessGuard.java`
- `src/main/java/com/vibegraph/common/exception/AccountDeactivatedException.java`
- `src/main/java/com/vibegraph/auth/web/JwtAuthFilter.java`
- `src/main/java/com/vibegraph/mcp/MeteredToolCallback.java`
- `src/main/java/com/vibegraph/common/config/McpServerConfig.java`
- `src/test/java/com/vibegraph/auth/service/AccountAccessGuardTest.java`
- `src/test/java/com/vibegraph/auth/web/JwtAuthFilterTest.java`
- `src/test/java/com/vibegraph/mcp/McpCreditMeteringTest.java`
- `src/test/java/com/vibegraph/auth/websocket/RealtimeAccountAccessInterceptorTest.java`

The exact path for the new exception is `src/main/java/com/vibegraph/common/exception/AccountDeactivatedException.java`; the list above should be corrected by the integrator if copied verbatim.

## Intended fixed behavior

- Browser auth continues to use HttpOnly `vg_session`.
- CLI/API login continues to return a JWT for `Authorization: Bearer <jwt>` clients.
- Logout is allowlisted through account-status filtering so the cookie can be cleared.
- Current account status is checked against the repository rather than trusting old JWT status claims.
- Blocked product requests use `403` and `ACCOUNT_BLOCKED`.
- Deactivated product requests use `403` and `ACCOUNT_DEACTIVATED`.
- Safe reason text is returned; internal admin reasons are not intended to be exposed.
- MCP callbacks use the shared account-access assertion before feature, ownership, credit, or delegate work.
- Existing STOMP project delivery checks remain on outbound channels; additional regression coverage was added for post-connect status changes and SEND.

## Tests added/updated

- Account access guard tests for active, blocked, deactivated, and missing users.
- JWT filter test expectation for `ACCOUNT_DEACTIVATED`.
- MCP metering tests for blocked/deactivated accounts before delegate work.
- Realtime interceptor tests for deactivated/blocked post-connect project behavior.

## Tests run

The required focused Maven suite passed after `./mvnw clean compile` rebuilt production classes: **92 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. Named suites included `AuthController` (3), `JWT auth filter` (16), and `Realtime account access interceptor` (16).

Additional regression suite passed: `McpCreditMeteringTest,ExceptionsTest,RealtimeUpdateBroadcastTest` — **30 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**.

Full unit suite passed: `./mvnw.cmd clean test` — **799 tests, 0 failures, 0 errors, 9 skipped; BUILD SUCCESS**.

Full verification passed with `./mvnw verify` — **30 integration tests, 0 failures, 0 errors, 1 skipped; JaCoCo coverage checks met; BUILD SUCCESS**. A preceding `clean verify` attempt failed only because Windows held a lock on `target`; running `verify` against the already-clean build succeeded.

`git diff --check` reported only line-ending conversion warnings and no whitespace errors. `gitnexus detect_changes` reported 76 changed files / 371 symbols / 188 processes at critical aggregate risk because the shared worktree contains unrelated Phase 7 changes; that full scope must not be attributed to BE-1.

## Fixed / not fixed

### Intended/fixed in the implementation

- Added a distinct deactivation exception code.
- Added canonical product-access assertion to `AccountAccessGuard`.
- Updated JWT filter to use current database user role and account guard.
- Added MCP account guard wiring and callback enforcement.
- Added logout to restricted-account route allowlist.
- Added focused regression assertions for REST/MCP/realtime status behavior.

### Not fixed / must be verified by integrator

- Spring context wiring and the full unit/integration gates are now verified by Maven.
- Automated Java/security review agents could not be launched because the environment classifier was unavailable; manual security audit items remain documented below.
- The existing worktree contains unrelated Phase 7 admin/abuse/quota/report changes; do not attribute those files to BE-1.
- CSRF, trusted proxy hardening, stale JWT role versioning, runtime anti-abuse registration, and deny-by-default unknown STOMP SEND destinations were identified by audit but are outside this Auth/Block/Session slice and remain for the relevant Phase 7 owners.

## Realtime / frontend contract notes

- `WebSocketConfig` should retain the account interceptor on both inbound and outbound channels.
- Browser clients continue using the cookie-derived handshake principal; the cookie remains HttpOnly.
- Bearer-based STOMP clients must send `connectHeaders.Authorization: Bearer <jwt>` on CONNECT.
- Project topics must be ownership checked and revalidated after block/deactivation.
- Existing product decision preserved: blocked users may use the safe support/report surface; deactivated users are denied realtime support/report access unless the product owner changes that contract.
- No frontend files were modified.

## Required next steps

1. Integrator review of BE-1 files against unrelated Phase 7 changes in the shared worktree.
2. Run Java/security review agents when the execution environment is available.
3. Resolve the documented out-of-scope security items with their respective Phase 7 owners.
4. Before any commit, rerun GitNexus change detection on an isolated BE-1 diff and inspect `git diff --check`.
5. Do not commit, push, or merge as part of this handoff.
