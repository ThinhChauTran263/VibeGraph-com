# Dev2 Task Brief: Admin Backend APIs

## Owner
Dev2 owns backend APIs for admin dashboard and user/account administration.

## Branch And Git Rules
- Work on `poc`.
- Pull latest `origin/poc` before starting.
- Do not create a new branch unless Supervisor asks.
- Do not commit, push, merge, or run `git add .` without Supervisor approval.
- Stage only files in your scope.
- Before editing existing Java symbols, run GitNexus impact and report HIGH/CRITICAL before proceeding.
- Before handoff, run GitNexus detect changes.

## Dependency Boundary
Do not edit Dev1 quota/report internals unless blocked. If you need report data for admin feedback, add admin service methods around existing report entities and coordinate naming with Dev1.

## Main Goal
Build the admin backend needed for Phase 4:
- overview dashboard metrics
- user management
- plan/quota override management
- API key admin controls
- feedback/report admin workflow

## Slice A: Admin Overview API

### Endpoint
- `GET /api/admin/overview`

### Response Should Include
- total registered users
- online users count
- total imported projects
- total reports
- open reports
- blocked users
- current timestamp

### Notes
- Realtime online users can be approximate for Phase 4.
- If no realtime presence store exists, return a best-effort count and document the source.
- Frontend will poll this endpoint every 15-30 seconds and maintain the rolling chart series in memory.

### Tests Required
- Admin can fetch overview.
- Normal user gets forbidden.
- Metrics map correctly from repositories/services.

## Slice B: Admin User Management

### Endpoints
- `GET /api/admin/users?page=&size=&q=&status=&plan=`
- `POST /api/admin/users`
- `GET /api/admin/users/{userId}`
- `PATCH /api/admin/users/{userId}/block`
- `PATCH /api/admin/users/{userId}/unblock`
- `PATCH /api/admin/users/{userId}/deactivate`
- `PATCH /api/admin/users/{userId}/plan`
- `PATCH /api/admin/users/{userId}/quota-override`
- `PATCH /api/admin/users/{userId}/api-key-creation`

### AdminCreateUserRequest Contract
```ts
interface AdminCreateUserRequest {
  email: string
  displayName: string
  role: 'USER' | 'ADMIN'
  planCode: 'FREE' | 'PRO' | 'TEAM'
  temporaryPassword: string
}
```

### Required Behavior
- Admin-only for all endpoints.
- Block stores safe user-facing reason.
- Blocked user sign-in/JWT access must return `ACCOUNT_BLOCKED`.
- Deactivate copy is soft disable, not hard delete.
- Plan update must use existing plan table.
- Quota override cannot be below current source-storage usage.
- API key creation disable overrides plan availability.
- Keys for blocked accounts must be unusable immediately if API-key auth exists later.

### Stable Error Codes
- `ACCOUNT_BLOCKED`
- `QUOTA_EXCEEDED`
- `API_KEYS_DISABLED`
- `API_KEY_PLAN_LIMIT_REACHED`
- `FORBIDDEN`

### Tests Required
- Normal user forbidden from admin endpoints.
- Admin can create user with temp password.
- Duplicate email rejected safely.
- Admin can block/unblock with reason.
- Blocked user receives `ACCOUNT_BLOCKED`.
- Deactivate disables sign-in/API access.
- Plan update works for FREE/PRO/TEAM.
- Quota override below current usage rejects.
- API key creation disable affects user key creation.

## Slice C: Admin Feedback APIs

### Endpoints
- `GET /api/admin/reports?page=&size=&status=&q=`
- `GET /api/admin/reports/{reportId}`
- `POST /api/admin/reports/{reportId}/messages`
- `PATCH /api/admin/reports/{reportId}/close`

### Required Behavior
- Admin can see all reports.
- Admin can reply in a report thread.
- Admin and user can close report.
- Closed report shows `deletesAfter`.

### Tests Required
- Admin can list all reports.
- Normal user forbidden from admin report endpoints.
- Admin reply appears in thread with ADMIN sender.
- Closing sets status and deletes-after timestamp.

## Files Likely Touched
- `src/main/java/com/vibegraph/auth/web/Admin*.java`
- `src/main/java/com/vibegraph/auth/service/Admin*.java`
- `src/main/java/com/vibegraph/auth/dto/**`
- `src/main/java/com/vibegraph/auth/repository/**`
- `src/main/java/com/vibegraph/common/exception/**`
- corresponding tests under `src/test/java/**`

Avoid touching:
- `vibegraph-web/**`
- `vibegraph-cli/**`
- Dev1 local patch quota code unless coordinated
- Docker/env files

## Verification Commands
- Focused admin tests.
- `.\mvnw.cmd test`
- `.\mvnw.cmd verify`
- `npx gitnexus detect-changes --scope all --repo VibeGraph-com`

## Handoff Format
Report:
- Changed files.
- Admin API contract.
- Test commands and pass/fail counts.
- GitNexus risk.
- Any open backend questions.
- Confirm no commit/push unless explicitly approved.
