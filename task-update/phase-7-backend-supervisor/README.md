# Phase 7 - Backend Supervisor Multi-Agent Plan

Status: READY FOR PARALLEL BACKEND AGENTS
Base branch: `poc`
Supervisor: Codex
Workers: BE-1, BE-2, BE-3, BE-4, BE-5
Reviewer/Integrator: BE-6

## Muc tieu

Hoan thien backend cho User/Admin product surface da chot:

- Block/deactivate user phai co hieu luc ngay.
- Feature flags phai enforce that, khong chi CRUD.
- Credit/quota phai race-safe va dung don vi MB.
- Anti-abuse phai theo doi request, concurrent import, IP block.
- Reports, announcements, audit, overview phai co API va realtime/aggregate dung.
- FE se lam sau khi BE contract on dinh.

## Agent Board

| ID | Owner | Scope | Prompt |
| --- | --- | --- | --- |
| BE-1 | Auth / Block / Session | Account status, session, blocked/deactivated enforcement | `BE-1_AUTH_BLOCK_SESSION.md` |
| BE-2 | Feature Flags / System Controls | Global and child feature flag enforcement | `BE-2_FEATURE_FLAGS_SYSTEM.md` |
| BE-3 | Credit / Quota | Source storage quota, credits, race safety | `BE-3_CREDIT_QUOTA.md` |
| BE-4 | Anti-Abuse / Rate Limit / IP Block | request metrics, rate limit, concurrent imports, IP block | `BE-4_ANTI_ABUSE_RATE_IP.md` |
| BE-5 | Reports / Announcements / Audit / Overview | support realtime, notifications, audit, optimized admin overview | `BE-5_REPORTS_ANNOUNCEMENTS_AUDIT_OVERVIEW.md` |
| BE-6 | Integration Reviewer | merge gate, conflict control, final strict review | `BE-6_INTEGRATION_REVIEWER.md` |

## Coordination Rules

- Each worker uses its own branch or worktree from latest `poc`.
- No worker commits, pushes, or merges unless supervisor explicitly asks.
- Every worker must run GitNexus impact before editing Java symbols, per repo rules.
- Every worker writes a handoff file in this folder.
- Avoid frontend work in Phase 7 except DTO/type notes in handoff.
- If a task needs a shared DTO/error code, document it in handoff before changing unrelated areas.
- Do not revert unrelated changes from other agents.
- Do not hardcode secrets, test credentials, JWTs, raw API keys, or production config.

## Merge Order

1. BE-1: auth/block/session guard foundation.
2. BE-2: feature flag enforcement on top of guard foundation.
3. BE-3: credit/quota race safety.
4. BE-4: abuse/rate/IP controls.
5. BE-5: report/announcement/audit/overview aggregate surface.
6. BE-6: final integration review and full gate.

## Global Error Codes

Workers should reuse existing error shape and add only if missing:

- `ACCOUNT_BLOCKED`
- `ACCOUNT_DEACTIVATED`
- `FEATURE_DISABLED`
- `QUOTA_EXCEEDED`
- `CREDIT_EXHAUSTED`
- `CONCURRENT_IMPORT_LIMIT`
- `TOO_MANY_REQUESTS`
- `IP_BLOCKED`
- `API_KEYS_DISABLED`
- `API_KEY_PLAN_LIMIT_REACHED`
- `FORBIDDEN`

## Global Gate

Backend:

```bash
./mvnw clean test
./mvnw clean verify
```

Focused suites must cover:

- blocked/deactivated REST + STOMP behavior
- feature disabled behavior for all controlled operations
- credit/quota concurrent debit and admin adjustment
- concurrent import guard
- rate limit and IP block
- report realtime and announcement notification APIs
- audit log redaction and retention
- admin overview aggregate queries

Repo:

```bash
npx gitnexus detect_changes --repo VibeGraph-com
git diff --check
git status --short --branch
```
