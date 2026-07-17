# CodexCli Phase 7 Backend Handoff

## Files and data model

- Added `V10__phase7_support_audit_notifications.sql` for announcement creators,
  `user_notifications`, `audit_logs`, and singleton `audit_retention_settings` (90-day seed).
- Added `UserNotification`, `AuditLog`, and `AuditRetentionSetting` domain models plus repositories
  and projections under `com.vibegraph.auth`.
- Added `NotificationService`, `AuditRedactor`, and `AuditService`.
- Added `AccountNotificationController` and `AdminAuditController`.
- Extended announcements with `createdByUserId` and safe creator display/email fields.
- Extended `AdminService` overview aggregation with daily credit consumption and grouped security
  alert summaries; report admin detail now returns safe message DTOs instead of raw entities.
- Added audit calls for login/logout/failed login, account status and plan/quota/credit changes,
  API key create/disable, feature flag changes, report close/admin reply, and IP block/unblock.
- Added the Phase 7 spec at `VibeGraph-specs-2month/phase-7-support-admin-backend.md`.

## API contract

- `GET /api/account/notifications?limit=50` returns newest active notifications with `id`,
  `announcementId`, `title`, `body`, `creatorName`, `creatorDisplayName`, `creatorEmail`,
  `createdAt`, `severity`, `type`, `dismissible`, `read`, `readAt`, and `dismissedAt`.
- `GET /api/account/notifications/{id}` returns an owner-scoped notification detail.
- `PATCH /api/account/notifications/{id}/read` and `/dismiss` persist state in PostgreSQL.
- `GET /api/account/announcements` remains as a backward-compatible alias for the existing web
  shell and returns the same persisted notification projection.
- `GET /api/admin/audit-logs`, `GET /api/admin/audit-logs/{id}`,
  `GET /api/admin/audit-logs/retention`, and `PUT /api/admin/audit-logs/retention` expose paged,
  filterable audit data and retention configuration.
- Existing report REST/STOMP contracts remain in place. STOMP report subscriptions authorize the
  report owner or an admin; report mutations publish after commit.

## Aggregate query notes

- Admin overview uses `count`, grouped native projections, daily/monthly/quarterly/yearly credit
  aggregates, and `LIMIT 5` top-storage queries. It does not call `findAll()` for users, projects,
  project usage, or the credit ledger.
- Security alert summaries use grouped `security_events` rows from the last 24 hours.
- Notification materialization uses one parameterized `INSERT ... SELECT ... ON CONFLICT DO NOTHING`
  per user/app-load, followed by a bounded newest-first join query.
- Audit cleanup deletes only rows older than the configured cutoff; closing a report only sets
  `deleteAfter = closedAt + 7 days` and does not delete immediately.

## Tests and verification

- Stable disposable validation snapshot: exact required command
  `./mvnw "-Dtest=*Feedback*,*Report*,*Announcement*,*Notification*,*Audit*,*AdminOverview*" test`
  completed with **30 tests, 0 failures, 0 errors**.
- Dedicated notification/audit/report subset completed with **17 tests, 0 failures, 0 errors**.
- `git diff --check` passed (only existing CRLF normalization warnings).
- Main-source compile passed once before later concurrent worktree edits. The shared worktree's
  current wildcard test command is presently blocked at test compilation by unrelated concurrent
  FeatureGate constant and import-service constructor changes; those files were preserved and not
  reverted. The shared main-source compile is green.

## Frontend contract notes

- Frontend can keep polling `GET /api/account/notifications` or the compatibility announcements
  alias every 15-30 seconds; no websocket is required for announcement delivery.
- Dismiss/read must call the PATCH endpoints rather than relying on localStorage. Existing banner
  code can migrate incrementally because the alias preserves `id`, `title`, `body`, `creatorName`,
  and `createdAt`.
- Report realtime remains `/topic/reports/{reportId}` with `REPORT_MESSAGE_ADDED` and
  `REPORT_CLOSED` events; authorization is server-side per subscribed session.
- Admin overview response retains existing fields and adds daily credit points and security alert
  summaries without requiring frontend changes.
