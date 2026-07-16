# Dev1 Trello Tasks: Backend User Workspace

> Owner: Dev1
> Scope: backend user APIs, quota enforcement, user report APIs.

## Already Done By Team

| Task ID | Task | State | Note |
| --- | --- | --- | --- |
| D1-DONE-001 | JWT/auth foundation exists | Done | Do not reimplement |
| D1-DONE-002 | Phase 4 account schema foundation exists | Done | plans/settings/usage/report entities |
| D1-DONE-003 | Account profile API | Done | `GET/PATCH /api/account/profile` |
| D1-DONE-004 | Account usage API | Done | `GET /api/account/usage` |
| D1-DONE-005 | Account projects API | Done | `GET /api/account/projects` |
| D1-DONE-006 | User API key API | Done | `POST/GET/PATCH /api/account/api-keys` |
| D1-DONE-007 | Local patch API exists | Done | Dev1 must add quota enforcement only |
| D1-DONE-008 | Plan/credit SQL foundation drafted | Review | V3 adds credit balance, ledger, and pricing rules |

## Active Backlog

| Task ID | Backlog | Task | Acceptance Criteria | Priority | State | Files / Scope |
| --- | --- | --- | --- | --- | --- | --- |
| D1-T001 | Quota | Finalize source-storage quota model | Effective limit = plan storage limit or higher override; usage never negative | 1 | New | account/project usage services |
| D1-T002 | Quota | Enforce quota on import-local | Above quota rejects with `409 QUOTA_EXCEEDED`; below quota succeeds | 1 | New | project import service/tests |
| D1-T003 | Quota | Enforce quota on archive/GitHub/tarball import | Above quota rejects safely; no host path/content leak | 1 | New | archive/github import services/tests |
| D1-T004 | Quota | Enforce quota on local patch | Atomic; replacement counts delta; deletion reduces usage; dry-run no persist | 1 | New | `src/main/java/com/vibegraph/patch/**` |
| D1-T005 | Quota | Update account usage after storage changes | `GET /api/account/usage` reflects import/patch/delete | 1 | New | service/tests |
| D1-T006 | Quota | Blocked-account ordering | Blocked account returns `ACCOUNT_BLOCKED`, not `QUOTA_EXCEEDED` | 1 | New | auth/account/patch tests |
| D1-T007 | Credits | Credit pricing service | Read active `credit_pricing_rules`; do not hardcode prices in business logic | 1 | New | credit service/repository/tests |
| D1-T008 | Credits | Credit balance period service | Create/find current monthly `user_credit_balances` with plan snapshot | 1 | New | credit service/tests |
| D1-T009 | Credits | Deduct credit for MCP calls | MCP tool call writes negative ledger row and updates used credits | 1 | New | MCP service boundary/tests |
| D1-T010 | Credits | Deduct credit for CLI operations | CLI push/watch patch/analyze consumes credits through backend paths | 1 | New | patch/import/analyze services/tests |
| D1-T011 | Credits | Credit exceeded error | Insufficient credits returns stable error, no partial side effects | 1 | New | common exception/tests |
| D1-T012 | Reports | User create/list/detail reports | User can see only own reports | 1 | New | `/api/account/reports` |
| D1-T013 | Reports | User report messages | User can reply in own open report thread | 1 | New | report service/tests |
| D1-T014 | Reports | User close report | Close sets status and `deletesAfter = closedAt + 7 days` | 1 | New | report service/tests |
| D1-T015 | Cleanup | Cleanup eligibility support | Closed reports are eligible after 7 days; document if no scheduler yet | 2 | New | service/docs/tests if implemented |

## Credit Pricing Formula

Dev1 must implement credit calculation from DB rows in `credit_pricing_rules`.

```text
credits = base_credits
        + (file_count * per_file_credits)
        + (source_mb * per_mb_credits)
        + (node_count / 1000 * per_1k_nodes_credits)
```

Rules:
- Do not hardcode credit prices in service logic.
- Hardcoding operation codes is acceptable.
- Apply a clear rounding rule and document it in handoff.
- Never charge less than `minimum_credits`.
- Write every deduction/adjustment to `credit_ledger`.

Examples:
- `MCP_TOOL_CALL`: `1` = 1 credit.
- `CLI_PUSH` with 20 files: `1 + 20*0.1 = 3 credits`.
- `PROJECT_ANALYZE` with 100 files and 20MB: `5 + 100*0.01 + 20*1 = 26 credits`.
- `IMPORT_ARCHIVE` 50MB: `3 + 50*1 = 53 credits`.
- `IMPORT_GITHUB` 80MB: `3 + 80*1 = 83 credits`.

## Required Tests

| Command | Required Result |
| --- | --- |
| Focused quota/report tests | PASS |
| Focused credit/pricing/ledger tests | PASS |
| `.\mvnw.cmd test` | PASS |
| `.\mvnw.cmd verify` | PASS |
| `npx gitnexus detect-changes --scope all --repo VibeGraph-com` | Risk reported |

## Handoff Checklist

- [ ] Changed files listed.
- [ ] API contracts listed.
- [ ] Quota accounting model explained.
- [ ] Tests and counts reported.
- [ ] GitNexus risk reported.
- [ ] No commit/push without Supervisor approval.
