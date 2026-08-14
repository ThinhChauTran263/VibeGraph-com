# VibeGraph Audit Evidence - 2026-08-14

This ledger defines the evidence boundary for the `task-final` refresh. It records what was observed, where it came from, and what remains unverified. It does not promote uncommitted code to released functionality.

## 1. Snapshot

| Item | Observed value | Evidence |
| --- | --- | --- |
| Workspace | `D:\Users\User\IdeaProjects\VibeGraph` | audit shell working directory |
| Branch | `backup-full-fixed-20260728` | `git rev-parse --abbrev-ref HEAD` |
| HEAD | `d5154c4c368d7ca89fabb8da91a79858bea7af7b` | `git rev-parse HEAD` |
| HEAD subject | `docs: add the graph-rendering investigation handoff` | `git log -1` |
| Audit date | `2026-08-14` (`Asia/Bangkok`) | local audit session |
| GitNexus index | up-to-date at `d5154c4` | `npx gitnexus status` |
| Task-final history | no `task-final` commit after `df64de3` | `git log df64de3..d5154c4 -- task-final` returned no rows |

Evidence labels used below:

- **Committed**: present in `HEAD` and supported by source/config or committed history.
- **Working tree**: present in the current checkout but not committed at the snapshot.
- **Historical**: recorded by an earlier report, commit body, or QA session.
- **Fresh**: reproduced from the current checkout during this audit, or read from artifacts generated during this run window.

## 2. Worktree boundary

The following changes were observed before or during this documentation audit. They are preserved and are not described as merged or released.

### Tracked modifications

- `src/main/java/com/vibegraph/diagram/service/impl/UseCaseInferenceEngine.java`
- `src/main/java/com/vibegraph/graph/repository/impl/neo4j/Neo4jGraphRepository.java`
- `src/test/java/com/vibegraph/diagram/service/impl/UseCaseInferenceEngineHelperTest.java`
- `task-final/VibeGraph_WS3_Sprint-Trello-BBCH-ERD.md`
- `update/docs/Qwen/EXECUTION-REPORT-DOT4-7-2026-08-13.md`
- `vibegraph-web/src/views/admin/DashboardView.vue`
- `vibegraph-web/src/views/admin/UserDetailDrawer.vue`
- `vibegraph-web/src/views/admin/__tests__/DashboardView.spec.ts`
- `vibegraph-web/src/views/admin/__tests__/UserDetailDrawer.spec.ts`

### Untracked/current workspace files

- `Diagram/` and `scripts/drills/`
- Diagram helper classes: `UseCaseActorGuesser`, `UseCaseClassFallback`, `UseCaseDomainGuesser`, `UseCaseEndpointRules`, `UseCaseModelMerge`, and `UseCaseNameNormalizer`
- `src/test/java/com/vibegraph/diagram/service/impl/UseCaseInferenceEngineGraphFixtureTest.java`
- Qwen session reports dated 2026-08-14
- Admin helper modules `dashboard-echarts.ts`, `dashboard-transforms.ts`, `user-detail-format.ts`, `UserApiKeyList.vue`, and their new tests

The user-designated unrelated file `update/docs/Qwen/SO-SANH-TRUOC-SAU-UPGRADE-2026-08-14.md` is preserved but explicitly excluded from this audit's evidence. `.tmp-gitnexus-auth-context.json` was transient and absent at the final snapshot; ignored `audit-*.log` files remain local diagnostics, not backlog evidence.

## 3. Repository coverage boundary

`git ls-files` returned `1,246` tracked paths. They were classified into disjoint inventory groups before the risk-based source/config/test review:

| Inventory group | Tracked files |
| --- | ---: |
| Backend main source | 523 |
| Backend tests and test resources | 174 |
| Backend main resources | 26 |
| Frontend | 214 |
| CLI | 16 |
| Root/config/Docker/GitHub/Ops | 25 |
| Scripts | 12 |
| Database docs/schema | 6 |
| Evaluation artifacts | 15 |
| Docs and formal specs | 34 |
| Task/backlog/history/review/update | 198 |
| Sample projects | 3 |
| **Total** | **1,246** |

This establishes path inventory coverage, not manual line-by-line semantic review of every historical report, evaluation artifact, formal specification, CLI file, sample project, or untracked diagram asset. Those areas are either inventory-only or explicitly listed as limited scope; source/config/test/runtime evidence remains authoritative for claims.

## 4. Fresh verification

### Backend

| Check/artifact | Aggregate result | Evidence |
| --- | --- | --- |
| Unit/Surefire reports | `1067` tests, `0` failures, `0` errors, `1` skipped | `target/surefire-reports/`; 144 XML files |
| Integration/Failsafe reports | `71` tests, `0` failures, `0` errors, `1` skipped | `target/failsafe-reports/`; 12 XML files |
| JaCoCo artifact | report generated on 2026-08-14 | `target/site/jacoco/jacoco.xml` |
| Diagram helper tests | `36` test cases in current XML artifacts, all pass | targeted Maven invocations against current working tree; no single pristine full-suite claim |
| Parser robustness test | `33` tests, all pass in the current XML result | `.\mvnw.cmd -B -DskipITs -Dtest=MethodVisitorTest test` at 02:50 +07:00; the retained artifact proves the test result, but not a standalone preserved full-console `BUILD SUCCESS` line |
| Auth/anti-abuse evidence | Relevant focused tests and aggregate reports were observed | No retained command/output artifact proves an independently reproducible total count; no numeric focused-suite count is used as a closing metric. |

The aggregate XML timestamps span several commands, and the worktree contains uncommitted diagram/Neo4j changes. They prove the recorded tests passed, but are not represented as one pristine CI run on committed `HEAD`.

### Frontend

| Command/check | Result |
| --- | --- |
| `npm run type-check` | PASS |
| `npx oxlint .` | PASS; 0 warnings/errors |
| `npm run test:unit -- --run` | PASS; `67` test files / `570` tests |
| `npm run build` | PASS; `960` modules in the latest stable rerun; `dashboard-echarts` chunk about `561.43 kB` |
| `npm audit --audit-level=high` | PASS; `0` high vulnerabilities |
| `npx eslint . --no-cache` | FAIL: exactly one unused `ChartTone` at `DashboardView.vue:22`; no `isExpired` error remains in the latest snapshot |

The current coverage artifact is not an 80% gate. The latest successful coverage run reports lines `70.95%`, statements `68.38%`, branches `59.07%`, and functions `63.13%` in `vibegraph-web/coverage/coverage-summary.json` and `.vibegraph/fe-full-final3.log`. The warnings listed there are non-fatal and belong to that coverage run: incomplete test-router routes, unresolved `RouterLink` in a landing test, and a missing `ErrorAlert` message prop.

### Local runtime

- `docker compose ps`: Postgres, Neo4j, backend, and frontend are `Up`/healthy.
- Backend health returns HTTP `200` and `status: UP`.
- Frontend returns HTTP `200` with CSP and security headers.
- Unauthenticated `/api/account/profile` returns HTTP `401`.
- CORS allows `http://localhost:3000` with credentials and rejects `https://evil.example`.
- `docker compose --env-file .env.example config --quiet` fails because `VIBEGRAPH_TRUSTED_PROXIES` is blank in the template.

The frontend container was started during the runtime audit. Its image predates the current 2026-08-14 dirty dashboard/helper changes and has no source bind mount; the backend image likewise runs built artifacts rather than the current Java working tree. Therefore health proves the previously built local images/configuration run, not that current uncommitted refactors are executing. This is not production deployment or public-domain evidence.

## 5. Source/config facts

| Claim | Evidence | Status |
| --- | --- | --- |
| MCP surface is 18 tools | `src/main/java/com/vibegraph/mcp/MODULE-GUIDE.md`, `mcp/tool/`, `docs/mcp-integration.md` | committed |
| Additional MCP tools | `list_projects`, `verify_change`, `explain_compile_error` | committed |
| Deep CPG default | `application.yaml` and `ParserServiceImpl`; set `VIBEGRAPH_PARSER_DEEP_CPG=false` to opt out | committed |
| Unresolved CALL stubs | Dedicated flag defaults false, but effective `MethodVisitor` behavior is `deepCpg || emitUnresolvedCallStubs`; Spring Deep CPG defaults true, while plain constructors/deep-off suppress stubs | committed |
| PostgreSQL migrations | 19 SQL files: `V1-V15`, `V17-V20`; no `V16` | committed |
| Neo4j migrations | `V1__init_schema.cypher`, `V2__symbol_label.cypher` | committed |
| Supabase migration | `db/supabase/V1__init_realtime_storage.sql` | committed |
| HTTP client | Fetch in `vibegraph-web/src/lib/api.ts`; Axios absent from `package.json` | committed |
| Realtime channels | graph/reports use WebSocket/STOMP; request events/audit use SSE | committed |
| Project lifecycle | Trash retention defaults to 3 days and is configurable; restore/purge/retention exist; quota remains used until purge | committed |
| Supabase runtime | optional and disabled by default; no production cutover proof | committed implementation, runtime unverified |
| Auth session | HS512 access JWT cookie plus rotating hashed refresh sessions, replay/family revocation, CSRF client-header boundary | committed |
| Cached user caveat | `vg_user` remains in auth store/session bootstrap | committed |
| Graph overlap | root-cause/plan exist under `update/graph/`; final scale-invariant fix not proven | committed investigation, implementation open |
| Deployment | local Compose and SPA Nginx template exist; no production proxy/domain/TLS/auto-deploy proof | committed config, production open |

## 6. Backlog reconciliation decisions

- T80, T81, and T82 are marked `Done`: current committed code plus a fresh 33-test `MethodVisitorTest` run prove lambda parsing, method-reference handling, and unresolved-call stub emission. Plain constructors suppress stubs; Spring runtime Deep CPG enables them through `deepCpg or explicitFlag`. T83 remains `New` because no resolution-rate metric is emitted.
- RB32 is marked `Done` for the lambda/method-reference release item; its note documents the Deep CPG/stub coupling.
- CSV was regenerated from the reconciled Markdown and then validated for table equality, row widths, IDs, status totals, sprint distribution, estimate total, PPS and ED. Result: Sprint `160 Done / 10 In Progress / 22 New`; Release `55 Done / 8 In Progress / 3 New`; Product `24 Done / 2 In Progress`.
- T100 remains `New`: `nginx.conf.template` serves the SPA/security headers but does not prove a production `/api` reverse proxy, domain, TLS, or Certbot route.
- T84, T99, T100, T103-T105, T108, T116, T117, T118, T119, T120, T121, T138, T183, and T192 remain open.
- T187-T189 remain `In Progress`: tuning exists, but the documented noverlap unit/zoom root cause and integrated large-graph acceptance are not closed.
- Product/Release `Done` denotes a completed core release item; explicitly listed follow-up debt can remain open. This explains RB29, RB56, RB57, and RB66 without silently rewriting the historical taxonomy.

## 7. GitNexus and limitations

- `npx gitnexus status` reports commit metadata current at `d5154c4`; the local index also contains hashes for much of the dirty diagram/Neo4j/dashboard snapshot, while late/untracked files are not guaranteed to be indexed. It must not be interpreted as a HEAD-only or complete working-tree snapshot.
- Earlier upstream impact analysis for `Neo4jGraphRepository` reported `LOW` risk and four impacted nodes.
- Final audit rerun of `npx gitnexus detect-changes --repo VibeGraph-com` succeeded with medium risk, 14 files, 81 symbols and 3 affected processes; earlier 10-file/81-symbol and Ladybug-lock outputs are superseded session results. FTS remains degraded, so empty concept queries are not evidence of absence.

## 8. Evidence intentionally not claimed

There is no current evidence for production VPS deployment, a public domain, TLS/Certbot, auto-deploy/rollback, a live browser EventSource trace for audit SSE, full browser E2E after the current dashboard refactor, npm publication, OpenAPI generation, unchanged-file content-hash parsing, 500-file/5000-node benchmarks, or a distributed rate-limit/runtime state store.

## 9. Reproduction commands

```powershell
git status --short
git status --short --untracked-files=all
git rev-parse --abbrev-ref HEAD
git rev-parse HEAD
npx gitnexus status
npx gitnexus detect-changes --repo VibeGraph-com
.\mvnw.cmd -B -DskipITs -Dtest=MethodVisitorTest test
.\mvnw.cmd test -DskipITs -Dtest=UseCaseInferenceEngineHelperTest,UseCaseInferenceEngineGraphFixtureTest
Push-Location vibegraph-web
npm run type-check
npx oxlint .
npx eslint . --no-cache
npm run test:unit -- --run
npm run test:coverage
npm run build
npm audit --audit-level=high
Pop-Location
docker compose ps
docker compose --env-file .env.example config --quiet
```

The Maven aggregate counts can be recomputed from the XML files under `target/surefire-reports/` and `target/failsafe-reports/`; manually typed counts are not treated as stronger evidence than those artifacts.
