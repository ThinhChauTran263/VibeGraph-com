# Phase 4 Team Plan: User Workspace And Admin Console

## Goal
Finish the remaining User/Admin product surface without creating file conflicts between three developers.

## Work Split

| Dev | Scope | Primary Paths | Can Work In Parallel |
| --- | --- | --- | --- |
| Dev1 | User backend: quota enforcement and user feedback/report APIs | `src/main/java/**`, `src/test/java/**` | Yes, with Dev2 if report DTO names are coordinated |
| Dev2 | Admin backend: overview, users, plans/quota override, admin feedback | `src/main/java/**`, `src/test/java/**` | Yes, with Dev1 if report service ownership is respected |
| Dev3 | Frontend user workspace and admin console | `vibegraph-web/**` | Yes, consumes stable contracts from Dev1/Dev2 |

## Conflict Avoidance Rules
- Dev1 owns user-facing report endpoints under `/api/account/reports`.
- Dev2 owns admin-facing endpoints under `/api/admin/**`.
- Dev3 owns frontend only and must not edit backend Java.
- Dev1 and Dev2 must not both edit the same report entity/repository in the same round without reporting it.
- Shared exception codes must stay stable:
  - `ACCOUNT_BLOCKED`
  - `QUOTA_EXCEEDED`
  - `API_KEYS_DISABLED`
  - `API_KEY_PLAN_LIMIT_REACHED`
  - `FORBIDDEN`
- No one uses `git add .`.
- No one commits/pushes without Supervisor approval.

## Implementation Order
1. Dev1 starts quota enforcement first because frontend quota states depend on real backend behavior.
2. Dev2 starts admin overview/users in parallel.
3. Dev3 starts user workspace using already-pushed account/API-key APIs.
4. Dev3 waits for Dev1/Dev2 handoff before wiring reports and admin console actions that need new endpoints.

## Current Stable Backend APIs
- `GET /api/account/profile`
- `PATCH /api/account/profile`
- `GET /api/account/usage`
- `GET /api/account/projects`
- `POST /api/account/api-keys`
- `GET /api/account/api-keys`
- `PATCH /api/account/api-keys/{id}/disable`
- `POST /api/admin/api-keys`
- `GET /api/admin/api-keys?userId=...`
- `PATCH /api/admin/api-keys/{id}/disable`

## Contract Decisions
- Blocked accounts return `403 ACCOUNT_BLOCKED`.
- Quota is source storage only.
- Quota exceeded returns `409 QUOTA_EXCEEDED`.
- API key creation disabled returns `API_KEYS_DISABLED`.
- API key plan limit returns `API_KEY_PLAN_LIMIT_REACHED`.
- Admin delete user copy is soft deactivate, not hard delete.
- Closed reports show `Deletes after <date>` and are eligible for cleanup after 7 days.
- Admin online chart polls overview every 15-30 seconds until realtime API exists.
- Admin-created users use manual/temp password flow. No invite email assumptions.

## Supervisor Gate
For each dev handoff, Supervisor checks:
- Scope stayed inside assigned files.
- Tests pass.
- GitNexus risk is reported for backend work.
- No secrets or local env files staged.
- No unrelated refactors.
- Handoff includes changed files and known limitations.
