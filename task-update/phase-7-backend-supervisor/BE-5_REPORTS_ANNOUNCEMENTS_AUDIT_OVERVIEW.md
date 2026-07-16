# BE-5 Prompt - Reports / Announcements / Audit / Overview

```text
You are BE-5 for VibeGraph Phase 7 backend.

Scope: Reports / Announcements / Audit / Admin Overview only.
Base branch: latest poc.
Do not commit, push, or merge.

Read first:
- AGENTS.md
- RULES.md
- task-update/phase-7-backend-supervisor/README.md

Goal:
Complete support communication, in-app announcements/notifications, audit logs, and optimized admin overview APIs.

Reports:
- User can create report/feedback.
- User and admin can reply back and forth in a thread.
- User or admin can close report.
- Closed report shows `deleteAfter`.
- Cleanup treats deletion as eligible after 7 days from close, not guaranteed immediate.
- Report thread realtime should notify subscribed user/admin.

Announcements / Notifications:
- Admin can create announcements for:
  - maintenance
  - plan changes
  - disk warning
  - CLI version update
  - security notice
  - general notice
- User receives active announcement after successful login / app load.
- User notification APIs:
  - list notifications newest first
  - detail by id
  - mark read
  - dismiss
- API response should include title, body, creator display/email safe projection, createdAt, severity/type.
- Do not store notification state only in localStorage.

Audit:
- Audit important actions:
  - login/logout
  - failed login
  - block/unblock/deactivate
  - plan/quota/credit update
  - API key create/disable
  - feature flag change
  - IP block/unblock
  - report close/admin reply
- Never log secrets, passwords, raw JWTs, raw API key secrets, private source content.
- Retention default: 90 days unless existing config says otherwise.
- Admin APIs for audit list/detail and retention setting.

Admin Overview:
- Use database aggregate/top-N queries, not loading all users/projects/ledger into memory.
- Return data for:
  - total users
  - online users
  - imported project/repository count
  - credit usage by day/month/quarter/year as available
  - system storage summary
  - plan distribution
  - top storage projects/users
  - security/abuse alerts summary
- Polling-friendly response. FE may poll every 15-30 seconds until websocket/SSE later.

Do not work on:
- Feature flag enforcement except emitting audit when flags change if already available.
- Credit debit/rate limit internals except reading aggregate data.
- Frontend UI.

Suggested files to inspect:
- FeedbackReportService/AdminService report methods
- announcement entities/controllers/services
- admin overview service/DTOs
- audit/security event services
- repositories for users/projects/usage/ledger

Acceptance criteria:
- Reports realtime events are authorized by report owner/admin.
- Announcement notifications are persisted per user state, not only localStorage.
- Audit logs redact sensitive fields.
- Admin overview does not use unbounded findAll for large tables.
- Report cleanup tests prove 7-day eligibility.

Required tests:
- `./mvnw "-Dtest=*Feedback*,*Report*,*Announcement*,*Notification*,*Audit*,*AdminOverview*" test`
- Add tests for announcement read/dismiss state.
- Add tests for audit redaction.
- Add repository/aggregate tests for overview where practical.

Handoff:
Write `task-update/phase-7-backend-supervisor/BE-5_HANDOFF.md` with:
- files changed
- API contract added/changed
- tests run
- aggregate query notes
- frontend contract notes
```
