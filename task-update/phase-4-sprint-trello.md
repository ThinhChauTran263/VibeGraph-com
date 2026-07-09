# VibeGraph Phase 4 Sprint Trello: User Workspace And Admin Console

> Cap nhat: 2026-07-09
> Branch: `poc`
> Trang thai tong: Backend foundation + account/API-key slices da xong; plan/credit DB foundation da them vao task/SQL; quota, credit enforcement, report, admin APIs va frontend user/admin UI con phai lam.

## Progress Summary

| Area | Status | Note |
| --- | --- | --- |
| Auth/JWT | Done | Login/register/JWT filter/CurrentUser/block enforcement foundation da co |
| CLI local patch | Done | Interactive shell, logo/header, live suggestions, local patch demo/docs da xong |
| Phase 4 DB foundation | Done | plans, user_account_settings, project_usage, feedback_reports, feedback_messages |
| Plan/credit DB foundation | Review | V3 adds Free/Pro/Pro Plus/Max/Enterprise, balances, ledger, pricing rules; V4 adds per-account credit override |
| Account APIs | Done | profile, usage, projects |
| API key management APIs | Done | user/admin create/list/disable; one-time secret; BCrypt hash |
| Quota enforcement | New | Can enforce that su tren import/patch/analyze |
| Feedback/report APIs | New | User/admin report thread + close + deletesAfter |
| Admin APIs | New | overview, users, plan/quota, feedback |
| Frontend user workspace | New | UI chua lam day du |
| Frontend admin console | New | UI chua lam day du |

## Product Backlog

| ID | Role | Goal | Acceptance Criteria | Priority | Business Value | State | Owner | Note |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PB-AUTH | User | Dang ky/dang nhap bang JWT | Login/register tra token; JWT bao ve API; blocked account bi chan | 1 | High | Done | Backend | Da co tren `poc` |
| PB-CLI | Developer | Dung VibeGraph CLI local patch | CLI interactive, live suggestions, push/watch/local patch demo pass | 1 | High | Done | CLI | Da push len `poc` |
| PB-ACC-FOUND | User/Admin | Co nen tang account/plan/usage/report DB | Migration/entity/repository/service foundation ton tai | 1 | High | Done | Backend | Commit `d50aba1` |
| PB-ACC-API | User | Quan ly profile, usage, project da import | Account endpoints hoat dong va co tests | 1 | High | Done | Dev1 | Commit `d03904d` |
| PB-APIKEY | User/Admin | Tao/list/disable API key | One-time secret; hash-only; plan/disabled/blocked checks | 1 | High | Done | Dev1/Dev2 | Commit `d03904d` |
| PB-PLAN-CREDIT | User/Admin | Co plan va credit linh hoat trong DB | Plans + monthly credits + pricing rules + ledger ton tai, khong hardcode gia trong code | 1 | High | Review | Dev1/Dev2 | SQL foundation added in V3 |
| PB-QUOTA | User | Hien thi va enforce dung luong plan | Vuot quota bi `409 QUOTA_EXCEEDED`; usage cap nhat dung | 1 | High | New | Dev1 | Backend first, frontend wire later |
| PB-REPORT-U | User | Gui report va phan hoi thread | User chi xem report cua minh; close co deletesAfter | 1 | High | New | Dev1 | `/api/account/reports/**` |
| PB-ADMIN-OV | Admin | Xem dashboard tong quan | Overview co user/project/report/online count | 1 | High | New | Dev2 | Frontend poll 15-30s |
| PB-ADMIN-USER | Admin | Quan ly user | Create/block/unblock/deactivate/plan/quota/api-key controls | 1 | High | New | Dev2 | `/api/admin/users/**` |
| PB-REPORT-A | Admin | Quan ly feedback/report | Admin list/reply/close reports | 1 | High | New | Dev2 | `/api/admin/reports/**` |
| PB-FE-USER | User | Co user workspace UI | Profile/API keys/projects/quota/report states render | 1 | High | New | Dev3 | Can start with stable APIs |
| PB-FE-ADMIN | Admin | Co admin console UI | Overview chart/users/report UI responsive | 1 | High | New | Dev3 | Wait Dev2 contracts for full wiring |

## Release Backlog / Tasks

| Task ID | Backlog | Task | Acceptance Criteria | Priority | State | Owner | Files / Scope |
| --- | --- | --- | --- | --- | --- | --- | --- |
| T-P4-001 | PB-AUTH | JWT auth foundation | JWT secret from env; login/register works; protected APIs use JWT | 1 | Done | Backend | `src/main/java/com/vibegraph/auth/**` |
| T-P4-002 | PB-ACC-FOUND | Phase 4 account schema foundation | plans/settings/usage/report tables and entities exist | 1 | Done | Backend | migration + auth domain |
| T-P4-003 | PB-ACC-API | Account profile API | GET/PATCH profile pass tests | 1 | Done | Dev1 | `/api/account/profile` |
| T-P4-004 | PB-ACC-API | Account usage API | GET usage returns plan/used/limit/remaining | 1 | Done | Dev1 | `/api/account/usage` |
| T-P4-005 | PB-ACC-API | Account projects API | GET projects paginated; ownership only | 1 | Done | Dev1 | `/api/account/projects` |
| T-P4-006 | PB-APIKEY | User API key API | create/list/disable own keys; secret returned once | 1 | Done | Dev1 | `/api/account/api-keys` |
| T-P4-007 | PB-APIKEY | Admin API key API | admin create/list/disable user keys | 1 | Done | Dev2 | `/api/admin/api-keys` |
| T-P4-008 | PB-PLAN-CREDIT | Plan/credit SQL foundation | V3 creates Free/Pro/Pro Plus/Max/Enterprise + credit tables/rules; V4 adds account credit override | 1 | Review | Supervisor | `src/main/resources/db/migration/V3__plans_and_credits.sql`, `V4__account_credit_override.sql` |
| T-P4-009 | PB-PLAN-CREDIT | Plan domain/API mapping | Backend exposes storage + monthly credits + contact-sales flag | 1 | New | Dev2 | plan DTO/entity/service |
| T-P4-010 | PB-PLAN-CREDIT | Credit pricing service | Code reads `credit_pricing_rules`; only operation enum is hardcoded | 1 | New | Dev1 | credit service/tests |
| T-P4-011 | PB-PLAN-CREDIT | Credit deduction for MCP/CLI | MCP call and CLI operations write ledger and update balance | 1 | New | Dev1 | MCP/CLI-facing backend paths |
| T-P4-012 | PB-QUOTA | Quota service/model finalization | Effective source-storage quota is plan or higher override | 1 | New | Dev1 | backend service/repository |
| T-P4-013 | PB-QUOTA | Enforce quota on import/archive/github | Reject above quota before/without unsafe persisted state | 1 | New | Dev1 | project import services |
| T-P4-014 | PB-QUOTA | Enforce quota on local patch | Atomic patch; delta accounting; dry-run no persist | 1 | New | Dev1 | `patch/**` |
| T-P4-015 | PB-QUOTA | Usage accounting tests | replacement/delete/dry-run/override/blocked tests pass | 1 | New | Dev1 | tests |
| T-P4-016 | PB-REPORT-U | User report create/list/detail | User can manage own reports only | 1 | New | Dev1 | `/api/account/reports` |
| T-P4-017 | PB-REPORT-U | User report messages/close | User reply; close sets deletesAfter + 7 days | 1 | New | Dev1 | report services/tests |
| T-P4-018 | PB-ADMIN-OV | Admin overview API | Metrics for users/online/projects/reports | 1 | New | Dev2 | `/api/admin/overview` |
| T-P4-019 | PB-ADMIN-USER | Admin user list/detail/create | Pagination/filter; create temp password user | 1 | New | Dev2 | `/api/admin/users` |
| T-P4-020 | PB-ADMIN-USER | Admin block/unblock/deactivate | Safe reason; ACCOUNT_BLOCKED; soft deactivate copy | 1 | New | Dev2 | admin user service |
| T-P4-021 | PB-ADMIN-USER | Admin plan/quota/API-key controls | Plan update; storage override entered/validated in MB; credit override validation; API key creation disable | 1 | New | Dev2 | admin account settings |
| T-P4-022 | PB-REPORT-A | Admin report workflow | List/detail/reply/close reports | 1 | New | Dev2 | `/api/admin/reports` |
| T-P4-023 | PB-FE-USER | Frontend account profile | Profile load/update UI with tests | 1 | New | Dev3 | `vibegraph-web/**` |
| T-P4-024 | PB-FE-USER | Frontend usage/projects | Quota meter + my projects list | 1 | New | Dev3 | `vibegraph-web/**` |
| T-P4-025 | PB-FE-USER | Frontend plan/credit display | Shows plan storage + credits + Enterprise contact-sales state | 1 | New | Dev3 | `vibegraph-web/**` |
| T-P4-026 | PB-FE-USER | Frontend API keys | Create/list/disable; one-time secret state | 1 | New | Dev3 | `vibegraph-web/**` |
| T-P4-027 | PB-FE-USER | Frontend blocked/quota/credit error states | Required copy renders; actions disabled not hidden | 1 | New | Dev3 | `vibegraph-web/**` |
| T-P4-028 | PB-FE-USER | Frontend user feedback UI | Report list/thread/reply/close after backend lands | 2 | New | Dev3 | `vibegraph-web/**` |
| T-P4-029 | PB-FE-ADMIN | Admin overview UI | Poll overview 15-30s; rolling online chart | 1 | New | Dev3 | `vibegraph-web/**` |
| T-P4-030 | PB-FE-ADMIN | Admin user management UI | Dense table/drawers/actions responsive | 1 | New | Dev3 | `vibegraph-web/**` |
| T-P4-031 | PB-FE-ADMIN | Admin plan/credit management UI | Plan select, credit balance/ledger/adjustment controls | 1 | New | Dev3 | `vibegraph-web/**` |
| T-P4-032 | PB-FE-ADMIN | Admin feedback UI | Thread reply/close; deletes-after copy | 2 | New | Dev3 | `vibegraph-web/**` |
| T-P4-033 | QA | Backend full verification | `mvnw test`, `mvnw verify`, GitNexus detect pass | 1 | New | Dev1/Dev2 | backend |
| T-P4-034 | QA | Frontend full verification | frontend tests/typecheck/build pass | 1 | New | Dev3 | frontend |

## Rules

| Rule | Description |
| --- | --- |
| State | Use `New`, `Active`, `Blocked`, `Review`, `Done` |
| No conflict | Dev1 owns `/api/account/**`; Dev2 owns `/api/admin/**`; Dev3 owns `vibegraph-web/**` |
| No duplicate work | Items marked `Done` are reference only; do not reimplement unless Supervisor asks |
| No broad staging | Never use `git add .`; stage explicit files only |
| Backend gate | Focused tests + `.\mvnw.cmd test` + `.\mvnw.cmd verify` + GitNexus detect |
| Frontend gate | `npm test -- --run` + typecheck/vue-tsc + `npm run build` |
| Commit gate | No commit/push without Supervisor approval |

## Credit Pricing Formula

Credit pricing is database-driven through `credit_pricing_rules`; business code must not hardcode prices.

```text
credits = base_credits
        + (file_count * per_file_credits)
        + (source_mb * per_mb_credits)
        + (node_count / 1000 * per_1k_nodes_credits)
```

The implementation must apply the chosen rounding rule and never charge below `minimum_credits`.

Examples from the current seed:

| Operation | Formula Example | Result |
| --- | --- | ---: |
| `MCP_TOOL_CALL` | `1` | 1 credit |
| `CLI_PUSH` | `1 + 20 files * 0.1` | 3 credits |
| `PROJECT_ANALYZE` | `5 + 100 files * 0.01 + 20MB * 1` | 26 credits |
| `IMPORT_ARCHIVE` | `3 + 50MB * 1` | 53 credits |
| `IMPORT_GITHUB` | `3 + 80MB * 1` | 83 credits |

## Update Instructions

When a dev finishes a task:
1. Update that dev's `task-update/devX/TASKS.md`.
2. Update this file's matching task row `State`.
3. Add commit hash only after Supervisor commits.
4. Keep handoff short: changed files, tests, risk, blockers.
