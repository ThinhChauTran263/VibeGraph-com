# Dev3 Trello Tasks: Frontend User Workspace And Admin Console

> Owner: Dev3
> Scope: `vibegraph-web/**` only.

## Already Done By Team

| Task ID | Task | State | Note |
| --- | --- | --- | --- |
| D3-DONE-001 | Frontend auth login/register foundation | Done | Existing auth slice |
| D3-DONE-002 | Backend account APIs available | Done | profile/usage/projects |
| D3-DONE-003 | Backend API key APIs available | Done | user/admin API key endpoints |
| D3-DONE-004 | CLI work complete | Done | Not frontend scope |
| D3-DONE-005 | Plan/credit SQL foundation drafted | Review | UI should expect storage + credit fields once API lands |

## Active Backlog

| Task ID | Backlog | Task | Acceptance Criteria | Priority | State | Files / Scope |
| --- | --- | --- | --- | --- | --- | --- |
| D3-T001 | User workspace | Account layout/routes | Logged-in user has account/workspace navigation | 1 | New | router/layout/views |
| D3-T002 | Profile | Profile page | Loads email/displayName/role; PATCH displayName works | 1 | New | profile view/store/tests |
| D3-T003 | Usage | Plan/quota/credit meter | Shows plan, `used / limit used`, remaining, credits used/remaining; fixed-width meter/status | 1 | New | usage components/tests |
| D3-T004 | Projects | My projects page | Lists imported projects with status/last analyzed date | 1 | New | projects view/store/tests |
| D3-T005 | API keys | API key list/create/disable | One-time secret display; disable confirm; list never shows secret | 1 | New | API key view/store/tests |
| D3-T006 | Error states | Blocked/quota/credit/API-key disabled states | Required copy renders; actions disabled not hidden | 1 | New | shared error/status components |
| D3-T007 | Reports | User feedback UI | Create/list/thread/reply/close after Dev1 API lands | 2 | New | reports views/tests |
| D3-T008 | Admin overview | Admin dashboard | Polls overview 15-30s; rolling online chart; responsive | 1 | New | admin overview view/tests |
| D3-T009 | Admin users | User management UI | Dense table, filters, drawer actions, mobile stacked rows | 1 | New | admin users views/tests |
| D3-T010 | Admin quota/plan | Plan/quota override UI | Storage override input is MB, displays converted capacity, prevents override below current usage with inline validation | 1 | New | admin user drawer/tests |
| D3-T011 | Admin credits | Credit balance/ledger UI | Show current credit period, plan credits, override credits, ledger, and admin adjustment controls | 1 | New | admin user detail/tests |
| D3-T012 | Admin API keys | Admin API-key controls UI | Disable key creation and individual keys when API exists | 1 | New | admin user detail/tests |
| D3-T013 | Admin reports | Admin feedback UI | Thread reply/close; `Deletes after <date>` | 2 | New | admin reports views/tests |

## Credit Pricing Explanation For UI

If the UI shows pricing detail or admin pricing rules, use this formula:

```text
credits = base_credits
        + (file_count * per_file_credits)
        + (source_mb * per_mb_credits)
        + (node_count / 1000 * per_1k_nodes_credits)
```

Examples for help text/admin detail views:
- MCP tool call: 1 credit.
- CLI push 20 files: `1 + 20*0.1 = 3 credits`.
- Analyze 100 files, 20MB: `5 + 100*0.01 + 20*1 = 26 credits`.
- Archive import 50MB: `3 + 50 = 53 credits`.
- GitHub import 80MB: `3 + 80 = 83 credits`.

Do not present this as marketing copy. Put it in admin detail/status/help text only if needed.

## Required Tests

| Command | Required Result |
| --- | --- |
| `npm test -- --run` | PASS |
| `npm run typecheck` or `vue-tsc --build --noEmit` | PASS |
| `npm run build` | PASS |
| Browser smoke on port 5173 if visual routes changed | PASS / notes |

## Handoff Checklist

- [ ] Changed frontend files listed.
- [ ] Routes/pages added listed.
- [ ] API contracts consumed listed.
- [ ] Tests and counts reported.
- [ ] Responsive/browser smoke notes included.
- [ ] No backend/CLI files touched.
- [ ] No commit/push without Supervisor approval.
