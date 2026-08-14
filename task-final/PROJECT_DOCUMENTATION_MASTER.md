# VibeGraph - Master Project Documentation

> **Audit snapshot:** 2026-08-14 (`Asia/Bangkok`)
>
> **Branch:** `backup-full-fixed-20260728`
>
> **Committed HEAD:** `d5154c4c368d7ca89fabb8da91a79858bea7af7b` - `docs: add the graph-rendering investigation handoff`
>
> **Backlog snapshot after the 2026-08-14 reconciliation:** Product `24 Done / 2 In Progress`; Release `55 Done / 8 In Progress / 3 New`; Sprint `160 Done / 10 In Progress / 22 New`.

Tài liệu này mô tả trạng thái có thể chứng minh của repository tại thời điểm audit. Bảng task chi tiết vẫn nằm trong `task-final/VibeGraph_WS3_Sprint-Trello-BBCH-ERD.md`; bằng chứng, lệnh tái hiện và giới hạn xác minh nằm trong `task-final/AUDIT-EVIDENCE-2026-08-14.md`. Các tài liệu trong `task/` và `task-update/` là lịch sử, không phải nguồn trạng thái hiện tại.

Audit đã inventory `100%` tracked paths (`1,246` đường dẫn từ `git ls-files`) rồi review sâu theo module/risk. Đây không phải claim đã đọc thủ công từng dòng của mọi report lịch sử, eval/spec, CLI, sample project hoặc untracked asset; giới hạn đó được ghi rõ trong evidence ledger.

## 0. Quy ước bằng chứng

| Nhãn | Ý nghĩa trong tài liệu này |
| --- | --- |
| **Committed** | Có trong `HEAD d5154c4` và đối chiếu được bằng source, config hoặc Git history. |
| **Working tree** | Có trong checkout hiện tại nhưng chưa commit; không được mô tả là merged, released hay production. |
| **Historical** | Có trong report, commit body hoặc QA session trước đó; không được coi là lần chạy mới trên checkout hiện tại. |
| **Fresh** | Được đọc lại từ source/artifact hoặc chạy lại trong phiên audit 2026-08-14. |
| **Unverified** | Chưa có bằng chứng runtime/acceptance trực tiếp; giữ trạng thái mở. |

### Ranh giới working tree tại snapshot

Checkout không sạch. Các thay đổi tracked đã tồn tại ở diagram inference, `Neo4jGraphRepository`, test diagram, Scrum Markdown, Qwen report và hai màn admin cùng test của chúng. Các file untracked đáng kể gồm helper diagram mới, graph fixture test, `Diagram/`, `scripts/drills/`, các helper/test admin dashboard và hai session report 2026-08-14. Vì vậy:

- Không dùng working tree để khẳng định một chức năng đã được phát hành.
- Các test fresh trên checkout hiện tại có thể bao gồm code chưa commit.
- GitNexus reports commit metadata at `HEAD d5154c4`, while its local file hashes currently include much of the uncommitted diagram/Neo4j/dashboard snapshot that existed when the index was refreshed. Later changes and unsupported/untracked files are not guaranteed to be indexed; source/Git evidence remains authoritative.
- `update/docs/Qwen/SO-SANH-TRUOC-SAU-UPGRADE-2026-08-14.md` is explicitly excluded from this audit by the user; it is preserved but supplies no evidence or task claim here.
- Chi tiết file-level được ghi trong `task-final/AUDIT-EVIDENCE-2026-08-14.md`, tránh lặp một danh sách dễ trôi trong master document.

---

# 1. Executive Summary

## 1.1 Sản phẩm hiện tại

VibeGraph là hệ thống phân tích mã Java theo thời gian thực: import project, parse bằng JavaParser/Symbol Solver, lưu knowledge graph trong Neo4j, cung cấp REST/MCP/realtime APIs và trực quan hóa bằng Vue + Sigma.js. Giai đoạn 2 bổ sung control plane đa người dùng trên PostgreSQL: auth, ownership, quota, plan/credit, API key, user workspace, admin console, anti-abuse, audit và notifications.

Kiến trúc hiện tại không còn là MVP Neo4j-only:

```text
Browser / CLI / MCP client
        |
        +-- REST + Fetch + cookie/API-key auth
        +-- STOMP: graph updates, report threads
        +-- SSE: request events, audit logs
        |
Spring Boot 4 / Java 21
        |
        +-- auth + abuse + patch       -> PostgreSQL control plane
        +-- parser + graph + diagram   -> Neo4j code graph/data plane
        +-- mcp                        -> 18 bounded tools
        +-- watcher                    -> incremental file-change pipeline
        +-- optional Supabase adapter  -> selected realtime/high-volume tables
```

## 1.2 Tiến độ backlog đã đối soát

| Cấp backlog | Tổng | Done | In Progress | New |
| --- | ---: | ---: | ---: | ---: |
| Product Backlog | 26 | 24 | 2 | 0 |
| Release Backlog | 66 | 55 | 8 | 3 |
| Sprint Backlog | 192 | 160 | 10 | 22 |

Tổng estimate là `945h`: `775h Done`, `72h In Progress`, `98h New`. Tỷ lệ task Done là `83.3%`; đây là tỷ lệ theo số task, không phải tuyên bố production readiness.

| Sprint | Phân bố trạng thái | Kết luận |
| --- | --- | --- |
| Sprint 1 | `31 Done` | Hoàn thành theo backlog. |
| Sprint 2 | `41 Done` | Hoàn thành theo backlog. |
| Sprint 3 | `38 Done / 12 New` | Parser/MCP/source/deep CPG/auth/CLI phần lõi đã có; OpenAPI, metrics, cache, benchmark và pagination còn mở. |
| Sprint 4 | `50 Done / 10 In Progress / 10 New` | User/admin surface đã rộng; final QA, graph polish, docs/demo và production deployment chưa đóng. |

Các thay đổi trạng thái được audit chấp nhận:

- `T80`, `T81`, `T82`: `New -> Done`, dựa trên implementation committed trong `MethodVisitor` và fresh `MethodVisitorTest` `33/33` pass. Plain visitor constructors suppress unresolved stubs, but the default Spring runtime enables Deep CPG and current constructor logic therefore emits low-confidence unresolved CALL stubs even when the dedicated stub flag is false.
- `RB32`: `New -> Done`, phản ánh nhóm lambda/method-reference đã được chứng minh.
- `T100`: `Done -> New`, vì chỉ có `vibegraph-web/nginx.conf.template` phục vụ SPA/security headers; không có `/api` reverse proxy, domain, TLS hoặc Certbot được chứng minh.

Lưu ý ngữ nghĩa: trạng thái `Done` ở Product/Release nghĩa là core release item đã được hiện thực; follow-up debt có thể vẫn mở ở Sprint task. Điều này giải thích các cặp như `RB29` với `T78/T79`, `RB56` với `T192`, `RB57` với `T138`, và `RB66` với các issue follow-up còn mở.

## 1.3 Kết luận readiness

| Phạm vi | Trạng thái có thể khẳng định |
| --- | --- |
| Local development/runtime | **Fresh:** bốn service Compose healthy; backend/frontend phản hồi đúng các kiểm tra health/auth/CORS cơ bản. |
| Committed implementation | Phần lớn product surface đã có trong `HEAD`; các giới hạn cụ thể được ghi ở Section 2. |
| Current working tree | Có refactor diagram/Neo4j/admin chưa commit; test fresh phải được đọc trong ranh giới này. |
| CI gates | Workflow đã cấu hình; không thể nói mọi gate hiện đang xanh vì latest dirty-worktree ESLint fail một lỗi. |
| Production deployment | **Chưa sẵn sàng/chưa được chứng minh:** không có production Compose/proxy/domain/TLS/auto-deploy/rollback/public acceptance. |

---

# 2. Implementation Status

## 2.1 Backend architecture

| Khu vực | Trạng thái và bằng chứng hiện tại |
| --- | --- |
| `parser/` | **Committed.** JavaParser visitors, Symbol Solver, Spring inference và deep CPG. Spring runtime mặc định bật deep CPG qua `vibegraph.parser.deep-cpg-enabled:${VIBEGRAPH_PARSER_DEEP_CPG:true}`; đặt `VIBEGRAPH_PARSER_DEEP_CPG=false` để opt out. Plain unit constructors có thể vẫn mặc định false, nên runtime config mới là nguồn sự thật cho application. |
| `graph/` | **Committed.** Import local/archive/GitHub, analyze, raw Neo4j Java Driver repository, REST graph/source/impact, realtime bridge và project trash. Control-plane service không dùng Neo4j OGM. |
| `diagram/` | **Committed core + working-tree refactor.** Backend trả canonical `UmlUseCaseResponse`; frontend `DiagramPanel` chuyển model thành SVG UML 2.5 bằng `renderUmlUseCaseSvg`. Deterministic inference đã có và Gemini refinement là tùy chọn. Nhiều helper inference mới trong dirty working tree chưa được coi là released. |
| `mcp/` | **Committed.** Spring AI MCP streamable HTTP `/mcp`, 18 tools, bounded output, project/ownership/path guards và metadata recovery. |
| `auth/` | **Committed.** Email/password, Google/GitHub OAuth2 redirect, ownership/quota/plan-credit/API key/user/admin/report/notification/audit surfaces. |
| `abuse/` | **Committed.** Staged edge/IP và identity/API-key throttling, IP block, registration/login guards, concurrent import guard và request-event telemetry. Window state dùng Caffeine bounded nhưng vẫn per-instance/best-effort, không phải cluster-wide enforcement. |
| `patch/` | **Committed.** Local Patch API cho CLI push/watch, đi qua auth, ownership, quota, credit và analyze scheduling. |
| `watcher/` | **Committed.** Recursive WatchService, debounce và incremental create/modify/delete pipeline; graph/report dùng STOMP. |

### Parser và graph caveats

- Lambda parsing, method-reference extraction và unresolved-call stub emission đã có. Runtime behavior is coupled to Deep CPG (`deepCpg or explicitFlag`), while plain constructors retain suppression; chưa có metric resolution-rate nên `T83` vẫn `New`.
- Full-graph snapshot cache/caps tồn tại, nhưng không tương đương content-hash skip unchanged files (`T84`) hay cursor/offset pagination (`T88`).
- Benchmark 500 file (`T85`) và acceptance 5000-node rendering (`T87`) chưa có fresh measurement.
- Baseline/lazy deep/Spring filtering/layout tuning đã nằm trong current history; không còn đúng khi gọi chúng là một nhánh chưa merge.
- `update/graph/03-ROOT-CAUSE.md` chứng minh mismatch đơn vị/zoom của noverlap; `update/graph/05-IMPLEMENTATION-PLAN.md` mới là kế hoạch. Scale-invariant overlap fix và browser acceptance chưa được chứng minh, nên `T187-T189` vẫn `In Progress`.

## 2.2 MCP tool surface

`src/main/java/com/vibegraph/mcp/MODULE-GUIDE.md` liệt kê đúng 18 tool được đăng ký:

| Nhóm | Tools |
| --- | --- |
| Architecture/context | `get_project_architecture`, `get_class_context`, `get_impact_analysis`, `get_layer_pattern` |
| Source/navigation | `trace_endpoint`, `find_references`, `get_source_file`, `search_source`, `get_method_source` |
| CPG/testing/change support | `get_method_cpg_context`, `find_related_tests`, `suggest_test_plan`, `plan_code_change`, `explain_failure_path`, `get_project_conventions` |
| Discovery/verification | `list_projects`, `verify_change`, `explain_compile_error` |

Các tool đọc Neo4j và source root đã được validate; output của test/change-plan vẫn dựa trên graph evidence kết hợp heuristic, nên người dùng/agent vẫn phải verify trước khi sửa. Tài liệu pointer `MCP_INTEGRATION.md` còn câu cũ “15 available tools”; đây là documentation drift, không thay đổi runtime surface 18 tool.

## 2.3 Data stores và migrations

| Store | Vai trò | Migration/state |
| --- | --- | --- |
| PostgreSQL primary | User, identity, ownership, refresh session, plan/credit, API key, report/notification, feature/admin/audit/abuse control plane. | **19 SQL files:** `V1-V15`, `V17-V20`; không có `V16`. |
| Neo4j | Knowledge graph theo `projectId`; raw driver + parameterized Cypher. | **2 Cypher files:** `V1__init_schema.cypher`, `V2__symbol_label.cypher`. |
| Supabase-compatible PostgreSQL | Optional target cho selected realtime/high-volume tables: reports/messages, runtime status, request/security events, announcements, notifications. | **1 SQL migration:** `db/supabase/V1__init_realtime_storage.sql`. `VIBEGRAPH_SUPABASE_ENABLED=false` mặc định. |

Supabase code có disabled fallback về primary datasource và có lựa chọn tách migration/runtime credentials. Audit không có bằng chứng production credential, remote migration hoặc cutover; vì vậy chỉ được gọi là optional implementation, không phải production storage đang hoạt động.

## 2.4 Authentication, authorization và session model

| Thành phần | Trạng thái thực tế |
| --- | --- |
| Local auth | `POST /api/auth/register`, `/login`, `/refresh`, `/logout`; `GET /api/auth/me`. BCrypt cho password. |
| OAuth | Spring Security `oauth2Login` redirect flow cho Google và GitHub, callback `/login/oauth2/code/{registrationId}`, account-linking/email checks. Không có custom `POST /api/auth/google`. |
| Access token | HS512 JWT, key tối thiểu 64 UTF-8 bytes, cookie HttpOnly; access lifetime default 30 phút. |
| Refresh session | Opaque token; database chỉ lưu SHA-256 hash, có `family_id`, single-use rotation, replay/family revocation và khoảng grace 30 giây cho concurrent tabs; absolute lifetime mặc định 7 ngày. |
| Browser API | Fetch wrapper với `credentials: 'include'`, `X-VibeGraph-Client: web`, one refresh-and-retry cycle; Axios không có trong `package.json`. |
| CSRF boundary | Spring CSRF mặc định bị disable nhưng unsafe browser-cookie requests được `CookieCsrfFilter` ràng buộc bằng custom client header. Đây là application boundary hiện tại, không nên ghi đơn giản là “không có CSRF”. |
| Client cache caveat | JWT không nằm trong localStorage, nhưng `stores/auth.ts` vẫn cache non-sensitive user JSON ở `vg_user` để bootstrap/router trước `/api/auth/me`. Do đó chưa thể khẳng định session bootstrap hoàn toàn server-authoritative. |
| API key | Project-bound, secret chỉ hiện một lần, hash-only storage, one active key/project và admin lock/resolution flow. |

## 2.5 Project lifecycle, notifications và realtime

- Delete project chuyển sang trash, retention mặc định 3 ngày; API có list trash, restore và irreversible purge, cùng scheduled sweep.
- Project trong trash vẫn giữ graph/source và tiếp tục tính quota; chỉ purge mới giải phóng resource.
- Re-import GitHub có xử lý purge duplicate đã nằm trong trash trước khi tính quota cho bản thay thế.
- Announcements sinh user notifications; account API/store hỗ trợ list, mark read và dismiss; UI có notification bell/banner.
- Graph updates và report threads dùng WebSocket/STOMP. Request security events và audit log dùng SSE.
- Audit SSE có backend/FE tests và polling/reconnect fallback, nhưng chưa có fresh browser EventSource/network trace; `T192` vẫn `New`.

## 2.6 Frontend và CLI

| Khu vực | Trạng thái hiện tại |
| --- | --- |
| Graph/explorer | Vue 3 + Sigma.js/Graphology; search/filter/detail/impact/source/diagram/realtime surfaces tồn tại. Graph overlap acceptance vẫn mở. |
| User workspace | Account/repositories, trash, API keys, usage/subscription, reports, notifications, tutorial/settings và responsive layout đã có. |
| Admin console | Overview/ECharts, users, plans/credits, security, flags, announcements, audit/settings/reports đã có. Dashboard/user-detail đang có refactor uncommitted và helper/test mới. |
| HTTP | Native Fetch + shared session-refresh wrapper; không dùng Axios. |
| i18n | `vue-i18n`, `en-US`/`vi-VN`, parity test; user selector vẫn ở sidebar trong khi admin ở header (`T183`). Graph/explorer còn technical/UI English, nên không có claim “zero hardcoded English”. |
| CLI | `vibegraph-cli` v0.1.0 hỗ trợ config/register/login/import-local/push/watch và local patch flow. Chưa có bằng chứng publish npm (`T138`). |

---

# 3. Verification Snapshot

## 3.1 Fresh checks - current checkout

| Phạm vi | Kết quả | Giới hạn cần giữ nguyên |
| --- | --- | --- |
| Backend Surefire artifacts | `1067` tests, `0` failures, `0` errors, `1` skipped; 144 XML files. | Timestamp trải từ 02:07 đến 02:50 và bao gồm nhiều lệnh; không phải một pristine run trên committed HEAD. |
| Backend Failsafe artifacts | `71` tests, `0` failures, `0` errors, `1` skipped; 12 XML files. | Artifact window riêng; không ghép tùy tiện thành một lệnh duy nhất. |
| Parser robustness | `MethodVisitorTest`: `33/33` pass in the current XML result. | Chạy trên current checkout; artifact proves the test result, not a standalone preserved full-console build line; dùng làm bằng chứng trực tiếp cho T80-T82. |
| Diagram helpers | `36/36` pass in current XML artifacts. | Có uncommitted diagram refactor trong checkout; no single pristine full-suite claim. |
| Auth/anti-abuse focused evidence | Relevant focused tests and current aggregate reports are present. | Focused tests were observed in-session, but no retained command/output artifact proves an independently reproducible total count, so no numeric focused-suite count is claimed. |
| Frontend type-check | PASS. | Current dirty working tree. |
| Frontend Oxlint | PASS, 0 warning/error. | Không thay ESLint gate. |
| Frontend Vitest | PASS, `67` files / `570` tests. | Có non-fatal warnings về test-router routes, unresolved `RouterLink` và thiếu `ErrorAlert` message prop. |
| Frontend coverage | Lines `70.95%`, statements `68.38%`, branches `59.07%`, functions `63.13%`. | Không có bằng chứng đây là 80% gate; chỉ là artifact fresh. |
| Frontend build | PASS, `960` modules in the latest fingerprint-stable rerun; `dashboard-echarts` chunk about `561.43 kB`. | Chunk lớn là quan sát build, chưa phải benchmark/performance acceptance. |
| Dependency audit | `npm audit --audit-level=high`: PASS, 0 high vulnerabilities. | Chỉ phản ánh npm audit ở thời điểm chạy. |
| Frontend ESLint | **FAIL**, exactly one error in the latest stable rerun: unused `ChartTone` at `DashboardView.vue:22`. | No `isExpired` error remains in the latest snapshot; frontend lint is still not green. |

## 3.2 Fresh local runtime

- `docker compose ps`: Postgres, Neo4j, backend và frontend đều healthy/up.
- Backend actuator trả HTTP `200` với `UP`.
- Frontend trả HTTP `200` và có CSP/security headers từ Nginx SPA container.
- `/api/account/profile` không auth trả HTTP `401`.
- CORS cho phép `http://localhost:3000` với credentials và từ chối `https://evil.example`.
- `docker compose --env-file .env.example config --quiet` **fail** vì `VIBEGRAPH_TRUSTED_PROXIES` để trống trong template nhưng Compose yêu cầu giá trị non-empty.

Đây là local runtime evidence. Nó không chứng minh VPS, public domain, TLS, reverse proxy, external OAuth callback hay production load.

## 3.3 Historical evidence

- Các report ngày 2026-08-12 ghi nhận full backend verify, 71 integration tests và JaCoCo gate pass; dùng làm lịch sử, không thay fresh final-tree run.
- QA 2026-07-14 ghi nhận stack Docker và các browser flows thời điểm đó; số frontend `49 files / 378 tests` đã được supersede bởi fresh `67 / 570`.
- Report Phase 9 ngày 2026-07-18/19 ghi focused audit/SSE tests pass; live browser audit EventSource vẫn chưa được ghi nhận.

## 3.4 GitNexus

Fresh `npx gitnexus status` reports commit metadata up-to-date at `d5154c4`. Fresh impact analysis for `Neo4jGraphRepository` returned `LOW` with four impacted nodes. The final audit rerun of `npx gitnexus detect-changes --repo VibeGraph-com` succeeded with `MEDIUM` risk, 14 files, 81 symbols and 3 affected processes (`GetFullGraph -> AsString`, `GetFullGraph -> Run`, `OnFileChange -> AsString`); untracked/late files remain outside complete change mapping. Earlier transient Ladybug locks and earlier 9-file/38-symbol and 10-file/81-symbol session outputs are superseded by this final rerun. GitNexus FTS is degraded, so empty concept searches are not evidence of absence. No commit was created.

---

# 4. CI, Deployment and Production Gaps

## 4.1 CI hiện có

| Workflow | Gate được cấu hình |
| --- | --- |
| `.github/workflows/backend.yml` | Java 21, `./mvnw verify`, upload Surefire/Failsafe report khi fail. |
| `.github/workflows/frontend.yml` | Node 22, `npm ci`, type-check, unit tests, lint, build, `npm audit --audit-level=high`. |

Backend workflow hiện chạy `./mvnw verify`, không phải câu cũ `-DskipITs test`. Frontend workflow có lint, nhưng latest dirty checkout vẫn fail ESLint một lỗi; do đó không có cơ sở tuyên bố mọi merge gate đang xanh. Backend workflow cũng ghi rõ CD build/push chưa được thêm vì registry chưa được operator quyết định.

## 4.2 Deployment hiện có và còn thiếu

**Có bằng chứng:**

- `docker-compose.yml` chạy bốn service local: PostgreSQL, Neo4j, backend, frontend.
- `application-prod.yaml`, Dockerfiles và deployment docs tồn tại.
- `vibegraph-web/nginx.conf.template` phục vụ SPA, cache static assets và security headers.

**Chưa có bằng chứng:**

- Production-specific Compose được acceptance (`T99`).
- Nginx `/api`/WebSocket reverse proxy (`T100`).
- DNS/domain (`T103`), Let's Encrypt/TLS (`T104`), HTTPS acceptance (`T105`).
- Auto-deploy/rollback workflow (`T108`).
- Public OAuth callback, production Supabase cutover, production backup/restore drill trong môi trường đích.

Vì Nginx template hiện chỉ phục vụ static SPA và cố ý không gửi HSTS (TLS terminator phải gửi), T100 và production readiness phải giữ mở.

---

# 5. Sprint Progress and Open Work

## 5.1 Những gì đã hoàn thành theo sprint

### Sprint 1 - `31/31 Done`

Archive import/safe extraction, JavaParser visitors, raw Neo4j persistence/migration, REST graph foundation và frontend Sigma core.

### Sprint 2 - `41/41 Done`

GitHub import, CALLS/Symbol Solver, node detail/impact, watcher + incremental STOMP, diagram foundation, MCP core, Docker/CI foundation và graph interaction surfaces.

### Sprint 3 - `38 Done / 12 New`

Đã có 18 MCP tools, source viewer, deep CPG runtime default-on, auth/ownership foundation, CLI/local patch, canonical Use Case model + frontend SVG rendering, lambda/method-reference robustness và low-confidence unresolved stubs (emitted by the default Spring runtime because Deep CPG is on). Mười hai task còn New:

`T78`, `T79`, `T83`, `T84`, `T85`, `T86`, `T87`, `T88`, `T89`, `T93`, `T94`, `T138`.

### Sprint 4 - `50 Done / 10 In Progress / 10 New`

Đã có user/admin workspace, plans/credits/quota, API-key lifecycle, anti-abuse, audit, notifications, i18n scoped surfaces, project trash/refresh sessions và local runtime foundation.

In Progress:

`T109`, `T112`, `T117`, `T118`, `T119`, `T120`, `T121`, `T187`, `T188`, `T189`.

New:

`T99`, `T100`, `T103`, `T104`, `T105`, `T108`, `T111`, `T116`, `T183`, `T192`.

## 5.2 Open work by evidence gap

| Nhóm | Task | Điều kiện đóng còn thiếu |
| --- | --- | --- |
| API/docs | `T78`, `T79`, `T116`, `T117` | OpenAPI/Swagger/API reference và user guide phải khớp current auth/user/admin/trash/MCP surface. |
| Parser/performance | `T83-T89`, `T93`, `T94`, `T120` | Resolution metric, content-hash cache, measured benchmarks, query/pagination work, graph stats/tech-debt evidence và tuning acceptance. |
| CLI distribution | `T138` | npm publication và acceptance matrix còn thiếu. |
| Production | `T99`, `T100`, `T103-T105`, `T108` | Production Compose, reverse proxy, DNS, TLS, HTTPS, deploy/rollback evidence. |
| Demo/handover | `T109`, `T111`, `T112` | Sample repo/demo assets/video/slides được hoàn tất và kiểm tra. |
| Final QA/polish | `T118`, `T119`, `T121` | Issue closure, full current browser E2E và product-level polish acceptance. |
| i18n | `T183` | User language selector chuyển/accept ở header, collapsed và mobile. |
| Graph | `T187-T189` | Implement scale-invariant overlap fix, resolve hit-testing/open questions và browser acceptance across zoom/filter/large graph. |
| Audit realtime | `T192` | Live browser EventSource/network trace và auth/CORS/lifecycle acceptance. |

---

# 6. Task Distribution

Số liệu phân công dưới đây được tính từ 192 Sprint task; task chung được chia đều như quy ước cũ.

| Thành viên | Tổng estimate | Done hours | Remaining hours | Trọng tâm còn lại |
| --- | ---: | ---: | ---: | --- |
| Khoa | ~229h | 196h | ~33h | Parser/cache/query, graph verification. |
| Vinh | ~219h | 170h | ~49h | Production deployment, parser/perf, audit SSE verification. |
| Danh | ~194h | 165h | ~29h | OpenAPI/API docs, graph stats, UI/UX polish. |
| Thịnh | ~182h | 163h | ~19h | Graph polish, selector placement, final QA/demo support. |
| Thái | ~120h | 81h | ~39h | Demo assets, user guide, final integration testing. |

Các số `~` do task `All`, `Thái + Thịnh` và `Vinh + Khoa` được chia đều; tổng chính thức vẫn là `945h`.

---

# 7. CSV Export and Maintenance

## 7.1 Trạng thái CSV

CSV was regenerated from the reconciled Markdown backlog during this audit and passed the cell/count/ID/estimate/PPS/ED validation recorded in `AUDIT-EVIDENCE-2026-08-14.md`. Không dùng timestamp 2026-07-26 làm bằng chứng current.

```powershell
Push-Location task-final
py -3 -X utf8 export_to_csv.py
Pop-Location
```

Trên máy audit dùng `py -3 -X utf8`; alias `python` trỏ tới Microsoft Store shim không khả dụng, còn bare `py -3` có thể crash khi exporter in emoji qua console `cp1252`.

Checklist sau export:

- `product_backlog.csv`: 26 rows, `24 Done / 2 In Progress`.
- `release_backlog.csv`: 66 rows, `55 Done / 8 In Progress / 3 New`.
- `sprint_backlog.csv`: 192 unique task IDs, không gap/duplicate, `160 Done / 10 In Progress / 22 New`.
- Sprint distribution: S1 `31 Done`; S2 `41 Done`; S3 `38 Done / 12 New`; S4 `50 Done / 10 In Progress / 10 New`.
- Total estimate `945h`; UTF-8 BOM được giữ.
- PPS row sum hiển thị `226.246`; phép tính exact/unrounded `226.25`; denominator `1800`; ED `30`.
- Markdown và CSV phải bằng nhau ở các cell nguồn.

## 7.2 Quy trình cập nhật

1. Cập nhật `task-final/VibeGraph_WS3_Sprint-Trello-BBCH-ERD.md` với state/note có evidence cụ thể.
2. Cập nhật `task-final/AUDIT-EVIDENCE-YYYY-MM-DD.md` hoặc ledger hiện hành khi snapshot/evidence boundary thay đổi.
3. Cập nhật master document này với branch, HEAD, test/runtime và caveat mới.
4. Chạy exporter bằng `py -3 -X utf8`, rồi validate counts/IDs/estimate/PPS/ED/BOM và cell equality; output `Exported` không tự thay thế validation.
5. Trước commit: chạy full repository verification theo `RULES.md`, `git diff --check`, `gitnexus_detect_changes()` và review toàn bộ diff. Audit này không tạo commit.

Quy ước task mới: dùng `T193+`, `RB67+`, `PB27+`; không sửa `task/` hoặc `task-update/` để giả làm trạng thái mới.

---

# 8. Evidence Index

| Cần kiểm tra | Nguồn bằng chứng chính |
| --- | --- |
| Snapshot, worktree, fresh checks, limitations | `task-final/AUDIT-EVIDENCE-2026-08-14.md` |
| State từng Product/Release/Sprint task | `task-final/VibeGraph_WS3_Sprint-Trello-BBCH-ERD.md` |
| MCP 18 tools | `src/main/java/com/vibegraph/mcp/MODULE-GUIDE.md`, `docs/mcp-integration.md` |
| Deep CPG default | `src/main/resources/application.yaml`, `ParserServiceImpl` |
| Auth/session/OAuth | `auth/config`, `auth/service`, `auth/oauth`, `auth/web`, `V18/V19` migrations |
| Fetch + session retry + `vg_user` caveat | `vibegraph-web/src/lib/api.ts`, `authRefresh.ts`, `stores/auth.ts`, `package.json` |
| PostgreSQL/Neo4j/Supabase migrations | `src/main/resources/db/migration`, `src/main/resources/db/supabase` |
| Trash/restore/purge/quota | `ProjectsProperties`, `ProjectController`, `ProjectTrashService`, `V17__project_trash.sql` |
| Graph overlap unresolved | `update/graph/03-ROOT-CAUSE.md`, `update/graph/05-IMPLEMENTATION-PLAN.md` |
| CI/deployment | `.github/workflows`, `docker-compose.yml`, `vibegraph-web/nginx.conf.template`, `.env.example` |
| Backend test artifacts | `target/surefire-reports`, `target/failsafe-reports`, `target/site/jacoco/jacoco.xml` |
| Frontend coverage artifact | `vibegraph-web/coverage/coverage-summary.json` |

---

*Master document refreshed from the 2026-07-26 version by repository-wide audit on 2026-08-14. It intentionally distinguishes committed code, dirty working-tree work, historical reports, fresh checks and unverified production scope.*
