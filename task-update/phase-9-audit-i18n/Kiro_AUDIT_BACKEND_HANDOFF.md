# Task 1A - Audit Backend Handoff

## Files changed

- `src/main/java/com/vibegraph/auth/service/AuditLogWriter.java` - independent `REQUIRES_NEW` persistence, valid JSON truncation marker.
- `src/main/java/com/vibegraph/auth/service/AuditService.java` - delegates writes; retention audit; strict actor failure handling; non-spoofable remote IP.
- `src/main/resources/db/migration/V15__audit_log_transaction_hardening.sql` - removes user FKs that can block independent audit commits.
- `src/main/java/com/vibegraph/auth/service/AdminService.java` - user-create/API-key-toggle coverage; richer plan/quota metadata; private reasons excluded.
- `src/main/java/com/vibegraph/auth/service/AdminPlanManagementService.java` - plan create/update/deactivate/delete coverage.
- `src/main/java/com/vibegraph/auth/service/AdminPricingManagementService.java` - pricing create/update/deactivate coverage.
- `src/main/java/com/vibegraph/abuse/IpBlockService.java` - mandatory audit collaborator; removal targets canonical IP.
- Tests updated/added in audit, admin, plan, pricing, feature flag, announcement, and IP block suites.
- `src/test/java/com/vibegraph/auth/integration/AuditLogRepositoryIT.java` proves audit persistence after outer rollback on PostgreSQL.

## Audit actions added

- `USER_CREATE`
- `API_KEY_CREATION_TOGGLE`
- `PLAN_CREATE`, `PLAN_UPDATE`, `PLAN_DEACTIVATE`, `PLAN_DELETE`
- `PRICING_RULE_CREATE`, `PRICING_RULE_UPDATE`, `PRICING_RULE_DEACTIVATE`
- `AUDIT_RETENTION_UPDATE`
- Existing `IP_UNBLOCK` removal metadata now records the IP instead of the block UUID.

## Coverage matrix

| Endpoint / method | Action name | Audited | Reason if NO |
|---|---|---:|---|
| `POST /api/admin/users` | `USER_CREATE` | YES | |
| `PATCH /api/admin/users/{id}/block` | `USER_BLOCK` | YES | Only safe reason is retained. |
| `PATCH /api/admin/users/{id}/unblock` | `USER_UNBLOCK` | YES | |
| `PATCH /api/admin/users/{id}/deactivate` | `USER_DEACTIVATE` | YES | Only safe reason is retained. |
| `PATCH /api/admin/users/{id}/plan` | `PLAN_UPDATE` | YES | Previous/new plan and quota metadata included. |
| `PATCH /api/admin/users/{id}/quota` | `QUOTA_UPDATE` | YES | Storage and credit overrides included. |
| `PATCH /api/admin/users/{id}/api-key-creation` | `API_KEY_CREATION_TOGGLE` | YES | Previous/new disabled state included. |
| `POST /api/admin/credits/users/{id}/adjust` | `CREDIT_UPDATE` | YES | Delta included; private free-text reason excluded. |
| `PATCH /api/admin/api-keys/{id}/disable` | `API_KEY_DISABLE` | YES | Existing coverage. |
| `PATCH /api/admin/api-keys/{id}/lock` | `API_KEY_DISABLE` | YES | Same admin-lock service method. |
| `PATCH /api/admin/api-keys/{id}/unlock` | `API_KEY_UNLOCK` | YES | Existing coverage. |
| `POST /api/admin/plans` | `PLAN_CREATE` | YES | |
| `PUT /api/admin/plans/{code}` | `PLAN_UPDATE` | YES | |
| `DELETE /api/admin/plans/{code}` | `PLAN_DEACTIVATE` / `PLAN_DELETE` | YES | Outcome depends on active assignments. |
| `POST /api/admin/pricing-rules` | `PRICING_RULE_CREATE` | YES | |
| `PUT /api/admin/pricing-rules/{code}` | `PRICING_RULE_UPDATE` | YES | |
| `DELETE /api/admin/pricing-rules/{code}` | `PRICING_RULE_DEACTIVATE` | YES | |
| `POST /api/admin/feature-flags` | `FEATURE_FLAG_CHANGE` | YES | Existing coverage; positive assertion added. |
| `PUT /api/admin/feature-flags/{key}` | `FEATURE_FLAG_CHANGE` | YES | Existing coverage. |
| `DELETE /api/admin/feature-flags/{key}` | `FEATURE_FLAG_CHANGE` | YES | Existing coverage. |
| `POST /api/admin/announcements` | `ANNOUNCEMENT_CREATE` | YES | Existing coverage; positive assertion added. |
| `PUT /api/admin/announcements/{id}` | `ANNOUNCEMENT_UPDATE` | YES | Existing coverage. |
| `DELETE /api/admin/announcements/{id}` | `ANNOUNCEMENT_DELETE` | YES | Existing coverage. |
| `PATCH /api/admin/announcements/{id}/disable` | `ANNOUNCEMENT_DISABLE` | YES | Existing coverage. |
| `POST /api/admin/security/ip-blocks` | `IP_BLOCK` | YES | |
| `PATCH /api/admin/security/ip-blocks/{id}` | `IP_BLOCK` / `IP_UNBLOCK` | YES | Depends on resulting state. |
| `DELETE /api/admin/security/ip-blocks/{id}` | `IP_UNBLOCK` | YES | |
| `PUT /api/admin/audit-logs/retention` | `AUDIT_RETENTION_UPDATE` | YES | |
| Admin GET/SSE, health, polling | none | NO | Read-only or noisy operational traffic intentionally excluded. |

## Tests run + result

- `.\mvnw.cmd '-Dtest=*Audit*,*Admin*,*Feature*,*Plan*,*Pricing*' test` - PASS.
- `.\mvnw.cmd clean test` - PASS: 852 tests, 0 failures, 0 errors, 9 skipped.
- PostgreSQL rollback regression in `AuditLogRepositoryIT` ran with Testcontainers and passed.
- `git diff --check` - PASS; line-ending warnings only.
- IDE diagnostics - no errors in changed backend production files or added pricing test.

## Blockers / risk

- GitNexus query index warned that FTS indexes were missing; targeted impact analysis still ran.
- GitNexus reports CRITICAL aggregate working-tree risk because many concurrent frontend/backend changes exist. This task did not edit frontend code.
- Independent audit rows intentionally describe attempted completed actions even if a later outer transaction rolls back, per task requirement. User foreign keys were removed so uncommitted/locked users cannot deadlock `REQUIRES_NEW` audit inserts.
- Remaining unrelated risk: `IpBlockService` database uniqueness behavior for inactive/concurrent duplicate IPs predates this task and is not changed here.
- No commit, push, or merge performed.
