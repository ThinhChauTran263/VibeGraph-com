# BE-4 Prompt - Anti-Abuse / Rate Limit / IP Block

```text
You are BE-4 for VibeGraph Phase 7 backend.

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

Suggested files to inspect:
- Security filter chain
- JwtAuthFilter
- project import controllers/services
- admin security event APIs
- existing security events/entities
- request logging/filter patterns

Acceptance criteria:
- Opening concurrent import attempts beyond limit returns `CONCURRENT_IMPORT_LIMIT`.
- IP block blocks unauthenticated and authenticated requests from exact IP.
- Rate limit cannot be bypassed with spoofed forwarding header in default config.
- Admin can view enough data to identify abusive user/IP.
- Tests cover authenticated, unauthenticated, API-key/MCP/CLI where applicable.

Required tests:
- `./mvnw "-Dtest=*RateLimit*,*SecurityEvent*,*IpBlock*,*Import*,*Abuse*" test`
- Add concurrent import tests.
- Add IP header spoof tests.

Handoff:
Write `task-update/phase-7-backend-supervisor/BE-4_HANDOFF.md` with:
- files changed
- config keys added
- API endpoints added/changed
- tests run
- production tuning notes
```
