# BE-1 Prompt - Auth / Block / Session

```text
You are BE-1 for VibeGraph Phase 7 backend.

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

Suggested files to inspect:
- src/main/java/com/vibegraph/auth/web/JwtAuthFilter.java
- src/main/java/com/vibegraph/auth/web/AuthController.java
- src/main/java/com/vibegraph/auth/service/AuthCookieService.java
- src/main/java/com/vibegraph/auth/service/AccountAccessGuard.java
- src/main/java/com/vibegraph/auth/websocket/RealtimeAccountAccessInterceptor.java
- src/main/java/com/vibegraph/auth/service/AccountSettingsService.java
- src/main/java/com/vibegraph/graph/**
- src/main/java/com/vibegraph/mcp/**

Acceptance criteria:
- Blocked user with old JWT immediately receives structured `ACCOUNT_BLOCKED` for product APIs.
- Blocked login receives safe reason, no internal reason leakage.
- Deactivated user receives structured account disabled response.
- Support/report allowlist still works if product decision says it should.
- STOMP events are not delivered to blocked/deactivated users after status changes.
- Tests prove REST and STOMP behavior.

Required tests:
- Focused auth/security tests:
  `./mvnw "-Dtest=*Auth*,*Blocked*,*Account*,*RealtimeAccountAccess*" test`
- Add/extend tests for old-token-after-block behavior.
- Add/extend tests for internal reason redaction.

Handoff:
Write `task-update/phase-7-backend-supervisor/BE-1_HANDOFF.md` with:
- files changed
- tests run
- fixed / not fixed list
- any contract notes for frontend
```
