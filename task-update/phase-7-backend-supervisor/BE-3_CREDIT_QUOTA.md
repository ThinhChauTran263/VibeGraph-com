# BE-3 Prompt - Credit / Quota

```text
You are BE-3 for VibeGraph Phase 7 backend.

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

Important:
- If current product decision says web import/analyze should not debit credit yet, do not re-enable web debit. Keep pricing documented but enforce only approved charge points.

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

Suggested files to inspect:
- plan/pricing entities, repositories, migrations
- UserCreditBalanceRepository
- credit/ledger services
- quota/source storage services
- import/analyze/CLI patch/push services
- AdminService quota/credit methods

Acceptance criteria:
- Concurrent debits cannot overspend.
- Concurrent debit + admin adjustment cannot lose updates.
- Admin quota override below current source storage usage is rejected.
- Monthly reset by registration day is deterministic.
- Quota uses MB in API/admin-facing values.
- Tests include PostgreSQL/concurrency where possible.

Required tests:
- `./mvnw "-Dtest=*Credit*,*Quota*,*Plan*,*Pricing*,*LocalImport*,*LocalPatch*" test`
- Add concurrency tests for debit-vs-debit and debit-vs-admin-adjustment.
- Add validation tests for quota override below current usage.

Handoff:
Write `task-update/phase-7-backend-supervisor/BE-3_HANDOFF.md` with:
- files changed
- DB migration notes
- tests run
- formula implementation notes
- exact charge points where credits are deducted
```
