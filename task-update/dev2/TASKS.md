# Dev2 Trello Tasks: Backend Admin

> Owner: Dev2
> Scope: admin backend APIs, admin user/account/report management.

## Already Done By Team

| Task ID | Task | State | Note |
| --- | --- | --- | --- |
| D2-DONE-001 | JWT/auth foundation exists | Done | Do not reimplement |
| D2-DONE-002 | Role.ADMIN exists | Done | Use existing role model |
| D2-DONE-003 | Phase 4 account schema foundation exists | Done | plans/settings/usage/report entities |
| D2-DONE-004 | Admin API key endpoints exist | Done | `POST/GET/PATCH /api/admin/api-keys` |
| D2-DONE-005 | API key exceptions exist | Done | `API_KEYS_DISABLED`, `API_KEY_PLAN_LIMIT_REACHED` |
| D2-DONE-006 | Plan/credit SQL foundation drafted | Review | V3 seeds Free/Pro/Pro Plus/Max/Enterprise; V4 adds account credit override |

## Active Backlog

| Task ID | Backlog | Task | Acceptance Criteria | Priority | State | Files / Scope |
| --- | --- | --- | --- | --- | --- | --- |
| D2-T001 | Overview | Admin overview endpoint | `GET /api/admin/overview` returns users/online/projects/reports metrics | 1 | New | admin controller/service/tests |
| D2-T002 | Users | Admin user list/detail | Pagination/search/filter; normal user forbidden | 1 | New | `/api/admin/users` |
| D2-T003 | Users | Admin create user | Uses temp password; duplicate email safe reject | 1 | New | admin user service/tests |
| D2-T004 | Users | Block/unblock user | Stores safe reason; blocked login/JWT returns `ACCOUNT_BLOCKED` | 1 | New | account settings/auth tests |
| D2-T005 | Users | Deactivate user | Soft disable sign-in/API access; no hard-delete copy | 1 | New | admin user service/tests |
| D2-T006 | Plan | Expose current plan catalog | FREE/PRO/PRO_PLUS/MAX/ENTERPRISE include storage, monthly credits, contact sales | 1 | New | plan DTO/controller/tests |
| D2-T007 | Plan | Update user plan | FREE/PRO/PRO_PLUS/MAX/ENTERPRISE updates work through plan table | 1 | New | plan/settings service/tests |
| D2-T008 | Quota | Storage quota override management | Admin inputs MB, backend converts to bytes, rejects override below current source-storage usage | 1 | New | settings service/tests |
| D2-T009 | Credits | Admin credit overview | Admin can see user's current credit balance and ledger | 1 | New | admin credit API/tests |
| D2-T010 | Credits | Admin credit override and adjustment | Admin can set monthly credit override and add/subtract credits with ledger source `ADMIN` | 1 | New | admin credit service/tests |
| D2-T011 | Credits | Pricing rule admin/read API | Admin can view pricing rules; write API optional if safe | 2 | New | pricing controller/tests |
| D2-T012 | API keys | Disable API key creation | User create-key returns `API_KEYS_DISABLED` | 1 | New | settings + API key tests |
| D2-T013 | API keys | Disable specific user API key | Admin can disable any key; normal user cannot | 1 | New | admin API key service/tests |
| D2-T014 | Reports | Admin report list/detail | Admin sees all reports; normal user forbidden | 1 | New | `/api/admin/reports` |
| D2-T015 | Reports | Admin report messages | Admin reply appears with admin sender | 1 | New | report service/tests |
| D2-T016 | Reports | Admin close report | Close sets status and deletes-after timestamp | 1 | New | report service/tests |

## Credit Pricing Formula For Admin UI/API

Admin APIs should expose pricing rules in a way the dashboard can explain charges.

```text
credits = base_credits
        + (file_count * per_file_credits)
        + (source_mb * per_mb_credits)
        + (node_count / 1000 * per_1k_nodes_credits)
```

Examples:
- MCP tool call: 1 credit.
- CLI push 20 files: `1 + 20*0.1 = 3 credits`.
- Analyze 100 files, 20MB: `5 + 100*0.01 + 20*1 = 26 credits`.
- Archive import 50MB: `3 + 50 = 53 credits`.
- GitHub import 80MB: `3 + 80 = 83 credits`.

Admin plan/credit screens should make clear that prices come from DB pricing rules, not constants.

## Required Tests

| Command | Required Result |
| --- | --- |
| Focused admin tests | PASS |
| Focused plan/credit admin tests | PASS |
| `.\mvnw.cmd test` | PASS |
| `.\mvnw.cmd verify` | PASS |
| `npx gitnexus detect-changes --scope all --repo VibeGraph-com` | Risk reported |

## Handoff Checklist

- [ ] Changed files listed.
- [ ] Admin API contract listed.
- [ ] Tests and counts reported.
- [ ] GitNexus risk reported.
- [ ] Any conflict with Dev1 report/quota code reported.
- [ ] No commit/push without Supervisor approval.
