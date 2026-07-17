# Phase 7 Support and Admin Backend

## Scope

This slice completes report communication, persisted announcement notifications, audit logs,
and polling-friendly admin overview data. It does not change feature enforcement, credit debit,
rate limiting, IP blocking, or frontend code.

## Guarantees

- Report owners and admins can exchange messages and close a report. Closing records
  `closedAt` and `deleteAfter = closedAt + 7 days`; scheduled cleanup only deletes rows whose
  `deleteAfter` is already eligible.
- Report STOMP subscriptions remain restricted to the report owner or an admin.
- Active announcements are materialized as per-user notification rows. Read and dismissed state
  survives browser/device changes and is never dependent on local storage.
- Notification list/detail responses expose only safe creator identity fields and are ordered
  newest first.
- Audit records cover authentication and scoped admin/security mutations. Structured details are
  redacted before persistence and never contain passwords, raw JWTs, API key secrets, or source
  content.
- Audit retention defaults to 90 days and is admin-configurable. Cleanup removes only records older
  than the configured cutoff.
- Admin overview metrics use count, aggregate, grouped, and top-N repository queries. The response
  is a read-only snapshot suitable for 15-30 second polling.

## API Surface

- User reports: `/api/account/reports/**`
- Admin reports: `/api/admin/reports/**`
- User notifications: `/api/account/notifications/**`
- Backward-compatible active announcements: `GET /api/account/announcements`
- Admin announcements: `/api/admin/announcements/**`
- Admin audit logs: `/api/admin/audit-logs/**`
- Admin overview: `GET /api/admin/overview`

## Data Model

- `announcements.created_by_user_id` links an announcement to its admin creator.
- `user_notifications` stores one row per user and announcement, including `read_at` and
  `dismissed_at`.
- `audit_logs` stores redacted action metadata and actor/target identifiers.
- `audit_retention_settings` stores the singleton retention duration, seeded to 90 days.
