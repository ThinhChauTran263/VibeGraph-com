# Dev1 Task Brief: User Workspace Backend + Quota + Reports

## Owner
Dev1 owns backend APIs that a normal user needs after login.

## Branch And Git Rules
- Work on `poc`.
- Pull latest `origin/poc` before starting.
- Do not create a new branch unless Supervisor asks.
- Do not commit, push, merge, or run `git add .` without Supervisor approval.
- Stage only files in your scope.
- Before editing existing Java symbols, run GitNexus impact and report HIGH/CRITICAL before proceeding.
- Before handoff, run GitNexus detect changes.

## Current Baseline
Already completed and pushed:
- Account foundation: plans, account settings, project usage, report/message entities.
- Account APIs:
  - `GET /api/account/profile`
  - `PATCH /api/account/profile`
  - `GET /api/account/usage`
  - `GET /api/account/projects`
- API key management:
  - `POST /api/account/api-keys`
  - `GET /api/account/api-keys`
  - `PATCH /api/account/api-keys/{id}/disable`
  - admin API-key endpoints.

## Main Goal
Finish the remaining normal-user backend flow:
- Enforce source-storage quota.
- Add feedback/report user APIs.
- Keep blocked-account behavior correct.

## Slice A: Quota Enforcement

### Required Behavior
- Quota is source storage only.
- Effective limit is plan storage limit, unless `quotaOverrideBytes` is higher.
- Return `409 QUOTA_EXCEEDED` when a storage-affecting action would exceed quota.
- Error copy must be safe:
  - `Source storage quota exceeded. Free up storage or ask an admin for a quota override.`
- Never leak host paths, file content, tokens, secrets, or raw upload data in error bodies.
- Blocked accounts must still return `403 ACCOUNT_BLOCKED`, not quota errors.

### Enforce On
- Local project import.
- Archive upload/import.
- GitHub/tarball import if present.
- `POST /api/projects/{projectId}/patch`.
- Any analyze/import flow that changes source-storage usage.

### Accounting Rules
- New file counts full size.
- Replacing an existing file counts only the delta.
- Deleting a file reduces usage.
- Usage must never go below zero.
- `dryRun=true` must never persist usage.
- Local patch must be atomic: if quota fails, no file is written.
- Account usage endpoint must reflect successful changes.

### Tests Required
- Below quota succeeds.
- Above quota rejects with `409 QUOTA_EXCEEDED`.
- Patch valid file plus quota exceeded writes nothing.
- Replacement counts delta.
- Deletion reduces usage.
- Dry-run does not persist usage.
- Account usage reflects import/patch changes.
- Quota override above plan allows more storage.
- Blocked account returns `ACCOUNT_BLOCKED`, not `QUOTA_EXCEEDED`.

## Slice B: User Feedback/Report APIs

### Endpoints To Implement
- `POST /api/account/reports`
- `GET /api/account/reports`
- `GET /api/account/reports/{reportId}`
- `POST /api/account/reports/{reportId}/messages`
- `PATCH /api/account/reports/{reportId}/close`

### Required Behavior
- Users can only access their own reports.
- Report thread supports user/admin back-and-forth messages.
- User and admin can close a report.
- Closed reports expose `deletesAfter` timestamp equal to closed time plus 7 days.
- Treat deletion as eligible after that time, not guaranteed immediate deletion.
- No background cleanup job required unless already easy to add safely.

### Tests Required
- User can create report.
- User can list only their own reports.
- User cannot access another user's report.
- User can add message to own open report.
- Closed report rejects new messages unless project convention already allows reopen.
- Closing sets closed state and deletes-after timestamp.

## Files Likely Touched
- `src/main/java/com/vibegraph/auth/service/**`
- `src/main/java/com/vibegraph/auth/web/**`
- `src/main/java/com/vibegraph/auth/dto/**`
- `src/main/java/com/vibegraph/project/**`
- `src/main/java/com/vibegraph/patch/**`
- `src/main/java/com/vibegraph/common/exception/**`
- corresponding tests under `src/test/java/**`

Avoid touching:
- `vibegraph-web/**`
- `vibegraph-cli/**`
- admin UI docs
- unrelated parser/graph code

## Verification Commands
- Focused tests for quota/report/account/patch/import.
- `.\mvnw.cmd test`
- `.\mvnw.cmd verify`
- `npx gitnexus detect-changes --scope all --repo VibeGraph-com`

## Handoff Format
Report:
- Changed files.
- API contract added/changed.
- Exact quota accounting model.
- Test commands and pass/fail counts.
- GitNexus risk.
- Known limitations or deferred items.
- Confirm no commit/push unless explicitly approved.
