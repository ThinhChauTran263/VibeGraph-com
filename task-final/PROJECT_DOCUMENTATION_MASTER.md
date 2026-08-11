# VibeGraph - Master Project Documentation (FINAL)

> **Bản FINAL — Cập nhật: 2026-07-26.** Đây là **nguồn sự thật mới nhất** về tiến độ dự án, thay thế toàn bộ tài liệu trong `task/` (bản 2026-06/07) và `task-update/` (các phase 1–9). Hai folder cũ chỉ giữ làm **lưu trữ lịch sử**, không cập nhật nữa.
>
> **Cách tài liệu này được lập:** đối chiếu chéo giữa (1) tài liệu Scrum gốc `task/`, (2) toàn bộ phase docs + session handoffs trong `task-update/`, (3) trạng thái **thực tế của codebase** ngày 2026-07-26 (cấu trúc package, 15 Flyway migrations, controllers, views, CLI, git log tới 25/07), và (4) các phiên QA live gần nhất (2026-07-14, 2026-07-18/19).

*Tài liệu tổng hợp dự án — Maintained cùng `VibeGraph_WS3_Sprint-Trello-BBCH-ERD.md` (bảng task chi tiết)*

---

## 📚 MỤC LỤC

1. [Executive Summary](#1-executive-summary)
2. [Implementation Status](#2-implementation-status)
3. [Sprint Progress & Updates](#3-sprint-progress--updates)
4. [Task Distribution](#4-task-distribution)
5. [CSV Export Guide](#5-csv-export-guide)
6. [Update Instructions](#6-update-instructions)

---

# 1. EXECUTIVE SUMMARY

## 🎯 TÓM TẮT QUAN TRỌNG

### Team Structure (5 người)

| Thành viên | Vai trò                                 | Chuyên môn                       |
| ---------- | --------------------------------------- | -------------------------------- |
| **Thái**   | Business Analyst, Product Owner, Tester | Requirements, acceptance testing |
| **Thịnh**  | Leader, Quản lý dự án, Vibecode         | Project management, coordination |
| **Khoa**   | Fullstack Developer                     | BE + FE, parser, controllers     |
| **Danh**   | Fullstack Developer                     | BE + FE, services, UI            |
| **Vinh**   | Backend Developer, Scrum Master         | BE, Neo4j, Postgres, DevOps      |

### Dự án đã đi qua 2 giai đoạn

**Giai đoạn 1 — MVP (Sprint 1–2, kết thúc ~cuối tháng 6):** Parse Java → Neo4j knowledge graph → Sigma.js visualization, 3 luồng import (local/archive/GitHub), realtime File Watcher + WebSocket, Impact Analysis, MCP tools, Docker + CI. **Review Giai đoạn 1 đạt kết quả tốt**; hội đồng yêu cầu bổ sung **xác thực + người dùng đăng nhập**.

**Giai đoạn 2 — Multi-user SaaS (tháng 7, đang hoàn thiện):** Auth (email + Google, JWT cookie HttpOnly), PostgreSQL control plane (15 Flyway migrations), ownership + quota + plan/credit, user workspace + admin console đầy đủ, anti-abuse, audit log realtime, CLI `@vibegraph/cli`, i18n EN/VI, Use Case UML SVG + AI refine (Gemini).

### Tiến độ realtime (tính từ bảng Sprint Backlog, 2026-07-26)

| Chỉ số | Giá trị |
| --- | --- |
| **Task Done** | **158 / 192 (82%)** — verify code lần cuối 26/07 |
| Task In Progress | 10 (graph polish, QA cuối, demo, USER_GUIDE) |
| Task New (chưa làm) | 24 (deploy VPS/SSL, demo video, OpenAPI, parser robustness, caching…) |
| Product Backlog Done | 24 / 26 (PB23 i18n + PB26 graph polish đang làm) |
| Release Backlog Done | 54 / 66 |
| Tổng giờ ước tính | 945h (578h kế hoạch gốc thực tính — bản cũ ghi 598h nhưng cộng lệch — + 367h Giai đoạn 2) |

### Sprint Status

| Sprint       | Phạm vi chính                                              | Done / Tổng task | Trạng thái |
| ------------ | ---------------------------------------------------------- | ---------------- | ---------- |
| **Sprint 1** | MVP nền tảng: import archive, parser, Neo4j, graph render  | 31/31            | ✅ 100%     |
| **Sprint 2** | GitHub import, realtime, diagram, MCP 4 tools, Docker/CI   | 41/41            | ✅ 100%     |
| **Sprint 3** | MCP 15 tools, source viewer, deep CPG + **GĐ2: auth, ownership, CLI** | 35/50 | 🚧 70% (15 task New: OpenAPI, parser robustness, caching, publish npm…) |
| **Sprint 4** | **GĐ2: user/admin console, plan/credit, anti-abuse, audit, i18n** + deploy/demo | 51/70 | 🚧 73% Done + 10 In Progress |

### Các luồng đã chạy end-to-end (đã QA live)

```
1) MVP:   Import (local/archive/GitHub) → Parse → Neo4j → REST → Sigma.js graph
          → File Watcher → WebSocket INCREMENTAL patch (không reset camera)
2) SaaS:  Register/Login (email/Google) → Cookie HttpOnly → User workspace
          (repositories/API keys/usage/subscription/reports) → quota + credit ledger
3) Admin: Login admin → /admin → Overview ECharts số liệu thật → Users/Plans/
          Security/Feature Flags/Announcements/Audit (SSE realtime)
4) CLI:   vibegraph login → push delta .java → analyze → graph cập nhật
5) MCP:   AI tool → /mcp (15 tools, API key gắn project) → context/impact/source
```

**Bằng chứng gần nhất:** 2026-07-14 Docker stack live healthy (BE/FE/Postgres/Neo4j), backend `mvnw verify` PASS, FE 49 files/378 tests PASS, browser QA 0 console error. 2026-07-19 audit + SSE focused tests PASS.

### ⚠️ Việc còn mở (xem chi tiết Sprint 3–4)

1. **Issue người dùng báo 2026-07-19 — còn 2/5** (verify code 26/07: #1 router, #3 i18n user, #4 i18n landing ĐÃ FIX): còn T183 (vị trí language selector user) + T192 (verify audit SSE live trên browser thật).
2. **Graph polish chưa merge** — nhánh `codex-backup-20260724-graph-lazy-deep` (lazy deep CPG, spring inference, layout/zoom) — T187–T189.
3. **Deploy production** — VPS/domain/SSL/auto-deploy chưa làm (T99, T103–T105, T108).
4. **Demo cuối** — video (T111), slides (T112), chốt repo demo (T109); USER_GUIDE cần viết lại cho GĐ2 (T117).
5. **Nợ kỹ thuật Sprint 3** — OpenAPI/Swagger (T78–T79), parser robustness lambda/method refs (T80–T83), content-hash caching (T84), Neo4j pagination (T86, T88).

---

# 2. IMPLEMENTATION STATUS

## 📊 Kiến trúc tổng thể hiện tại

```
com.vibegraph
├── auth/     ← GĐ2: control plane (Postgres/JPA) — user, ownership, plan/credit,
│               API key, report, notification, announcement, audit, admin APIs
├── abuse/    ← GĐ2: rate limit, IP block, concurrent import guard, request events
├── patch/    ← GĐ2: Local Patch API cho CLI (push/watch)
├── ai/       ← GĐ2: Gemini failover client (AI refine use case)
├── graph/    ← GĐ1: import, Neo4j repository, REST API, source viewer
├── parser/   ← GĐ1: JavaParser visitors + FlowAnalyzer (deep CPG opt-in)
├── mcp/      ← 15 MCP tools (Spring AI)
├── diagram/  ← Use Case UML SVG 2.5 + AI refine (Mermaid đã gỡ)
├── watcher/  ← File Watcher realtime
└── common/   ← config, exception, WebSocket/STOMP
```

**Nguyên tắc:** Control plane (Postgres/JPA/Flyway) tách data plane (Neo4j raw driver). Ownership lấy từ `projects.owner_id` (Postgres) — nguồn sự thật duy nhất. `graph` không phụ thuộc ngược `auth`.

### Backend — Giai đoạn 1 (đã ổn định)

| Module | Trạng thái | Ghi chú |
| --- | --- | --- |
| Parser (6 visitors + ParserServiceImpl + Symbol Solver) | ✅ Production | + FlowAnalyzer deep CPG opt-in (`VIBEGRAPH_PARSER_DEEP_CPG`) |
| Neo4j Repository (upsert/fullGraph/deleteFile/searchNodes/getImpact) | ✅ Production | Impact 3 profile: dependency / structural / type-data-flow; Package/File nodes đã có |
| REST API (Project/Import/Graph/Source/LocalProject controllers) | ✅ Production | + ownership guard GĐ2 ở mọi endpoint projectId |
| Realtime (FileWatcher + FileChangeBroadcaster + STOMP) | ✅ Production | CREATE/MODIFY/DELETE incremental, không reset camera |
| Diagram | ✅ Đổi hướng | **Use Case SVG UML 2.5** (`UmlUseCaseRenderer` + `UseCaseInferenceEngine` + `BaLabelBeautifier`) + AI refine (`LlmUseCaseRefiner`); **Class Diagram Mermaid đã gỡ sau UX review** |
| MCP (15 tools) | ✅ Production | context/impact/layer + source tools + senior tools (CPG, test plan, code-change plan, failure, conventions); metadata recovery sau restart |

### Backend — Giai đoạn 2 (mới)

| Module | Thành phần chính | Trạng thái |
| --- | --- | --- |
| Auth core | `JwtService`, `AuthService`, `AuthController`, `auth/oauth` (Google), `AuthCookieService` + `StatelessSessionCookieFilter` (cookie HttpOnly), `JwtAuthFilter`, `ApiKeyAuthFilter` | ✅ Done |
| Ownership/Quota | `AccountAccessGuard`, `ProjectUsageService`, `AccountQuotaSnapshot`, `StorageUnitConverter` (MB), blocked → `ACCOUNT_BLOCKED` trước `QUOTA_EXCEEDED` | ✅ Done |
| Plan/Credit | `CreditPricingService` (công thức từ DB `credit_pricing_rules`, không hardcode giá), `CreditBalanceService`, `CreditPeriodCalculator`, ledger đầy đủ | ✅ Done |
| User APIs | `AccountController` (profile/usage/projects), `AccountApiKeyController`, `AccountReportController` (thread + close + deletesAfter 7d), `AccountNotificationController`, credit ledger API | ✅ Done |
| Admin APIs (16 controllers) | Overview (số liệu thật + online history), Users (block/unblock/deactivate/plan/quota/credit), Plans, Pricing, FeatureFlags (+`FeatureGateService` enforce thật), Announcements, Storage (không lộ host path), SecurityMonitor (SSE), Audit (SSE + retention), Reports, ApiKeys, Credits | ✅ Done |
| Anti-abuse | `RateLimitFilter`, `IpBlockFilter`/`IpBlockService`, `ConcurrentImportGuard`, `RequestEvent*` + SSE realtime | ✅ Done |
| Audit | `AuditService` + `AuditLogWriter` (REQUIRES_NEW), `AuditRedactor`, coverage matrix đầy đủ admin mutations, V15 hardening, SSE stream | ✅ Done (live browser QA pending — T192) |
| Local Patch | `LocalPatchController`, `LocalPatchService`, `PatchAnalysisScheduler` (quota + credit + ownership) | ✅ Done |
| AI | `GeminiFailoverChatClient` (rotation, resilient) + `LlmUseCaseRefiner` | ✅ Done |

### Database

| DB | Vai trò | Schema |
| --- | --- | --- |
| **PostgreSQL** (control plane) | user, identity, ownership, plan, credit, API key, report, notification, announcement, feature flag, abuse, audit | Flyway **V1–V15**: init_auth → phase4_account → plans_and_credits → credit_override → deactivation → admin_ops → anti_abuse → credit_quota_defaults → audit_notifications → canonical_flags → project_bound_api_keys → api_key_lifecycle → api_key_lock_resolution → audit_log_transaction_hardening |
| **Neo4j** (data plane) | knowledge graph code (node/edge theo projectId) | `V1__init_schema.cypher` qua `Neo4jMigrationRunner` |

### Frontend (`vibegraph-web`)

| Khu vực | Thành phần | Trạng thái |
| --- | --- | --- |
| Graph core (GĐ1) | GraphCanvas + useSigma + graphAdapter + FilterPanel/SearchBar/NodeDetailPanel/ImpactAnalysisPanel + useGraphRealtime | ✅ Production |
| Auth | LoginView/RegisterView + stores/auth + router guard role-aware (admin → `/admin`, user → `/dashboard`, không dùng localStorage `vg_user`) | ✅ Done (issue #1 đã fix 22/07 — T190) |
| User workspace | `UserLayout` (sidebar collapse) + views/user: Overview, Repositories(Projects), ApiKeys, Usage, Subscription, Reports, Notifications, Tutorial, Settings | ✅ Done |
| Admin console | `AdminLayout` + views/admin: Dashboard (ECharts), UsersTable + UserDetailDrawer, PlansCredits, Security, FeatureFlags, Announcements, Audit (SSE live), Settings, Reports | ✅ Done |
| i18n | vue-i18n `src/language` (en-US/vi-VN 73KB) + `LanguageSelector` + parity test | ✅ Landing + user dashboard đã dịch xong (verify 26/07 — T182/T191); còn vị trí selector user (T183) |
| Landing | `LandingView.vue` | ✅ Có; i18n hoàn chỉnh (139 khóa t('landing...') — T191 Done) |
| Tests | 49 files / 378 tests + type-check + build | ✅ PASS (2026-07-14) |

### CLI (`vibegraph-cli`)

`@vibegraph/cli` 0.1.0 — bin `vibegraph`; lib: scanner/snapshot/push/watch/ignore/project-target. Luồng: `config set-url` → `register/login` → `projects import-local` → `push`/`watch` (delta `.java`, deny-list secret/build). Docs: `docs/local-patch.md`. **Chưa publish npm** (T138).

---

# 3. SPRINT PROGRESS & UPDATES

## Sprint 1 ✅ 100% (31/31 task) — MVP nền tảng

Import archive (zip/tar/tar.gz, chống zip-slip, ≤100MB), 6 parser visitors + structural edges, Neo4j raw driver + migration + upsert/fullGraph, REST API + GlobalExceptionHandler, FE core (api client, Pinia, graphAdapter, useSigma/GraphCanvas, SearchBar, states), Import Archive UI. Vertical slice end-to-end chạy từ tuần 7.

## Sprint 2 ✅ 100% (41/41 task) — Tính năng MVP còn lại

GitHub import (pre-flight + tarball + DI fix), Symbol Solver CALLS, Node Detail + Impact Analysis (BE+FE), realtime CREATE/MODIFY/DELETE incremental (FileWatcher → FileChangeBroadcaster → STOMP → FE patch tại chỗ), Diagram Mermaid (sau này đổi SVG UML), MCP 4 tools đầu + Spring AI config, FilterPanel + click/select highlight (Focus Mode N-hop đã gỡ sau UX review), Docker + compose + env profiles + CI backend/frontend, verify cuối: BE 279 tests, FE 161 tests.

## Sprint 3 🚧 70% (35/50 Done, 15 New) — MCP mở rộng + nền Giai đoạn 2

**Đã xong (gốc):** 15 MCP tools (source file/search/method, endpoint trace, references, method CPG, related tests, test plan, code-change plan, explain failure, conventions), MCP metadata recovery sau restart, T90–T92 UI polish, T95 Package/File nodes, T96–T98 testing (JaCoCo gate, visitor tests, IT Testcontainers).

**Đã xong (GĐ2 bổ sung vào Sprint 3):**
- **Auth & Ownership (T122–T133):** Postgres + Flyway, JPA entities, JWT + BCrypt + cookie HttpOnly, Google OAuth + account linking, ownership guard toàn API, migrate project cũ + admin bootstrap, FE auth, WS/STOMP auth, MCP/API key auth, quota nền, test matrix A-không-đọc-được-B.
- **CLI & Local Patch (T134–T137):** `@vibegraph/cli` + backend patch API.
- **Source viewer + Deep CPG (T139–T143):** SourceController + CodeViewerModal redact secret; deep CPG opt-in.
- **UML SVG (T184–T185):** renderer + inference + BA labels.

**Còn New (15):** OpenAPI/Swagger (T78–T79), parser robustness lambda/method refs/stub-on-failure/resolution rate (T80–T83), content-hash caching (T84), benchmark 500 file (T85), Neo4j optimize + pagination (T86, T88), 5000-node render test (T87), tech debt D1/D2 (T93–T94), publish npm CLI (T138).

## Sprint 4 🚧 69% Done + 11 In Progress (48 Done / 11 In Progress / 11 New trên 70 task) — Product surface GĐ2 + bàn giao

**Đã xong:**
- **User workspace BE (T144–T150):** account APIs, API key, reports thread, notifications, credit ledger API, quota enforcement toàn luồng.
- **Plan/Credit (T151–T155):** schema V3/V4/V9, pricing theo DB, trừ credit MCP/CLI/analyze/import, admin plan/pricing CRUD, credit override/adjust.
- **Admin BE (T156–T162):** overview số liệu thật, users management (block hiệu lực ngay), feature flags enforce thật, announcements, storage overview, reports, focused tests.
- **Anti-abuse (T163–T166):** rate limit, IP block, concurrent import guard, request events SSE.
- **Audit (T167–T169):** coverage matrix đầy đủ, REQUIRES_NEW + V15 + retention, SSE + AuditView live.
- **FE user/admin (T170–T176):** shells + sidebar collapse, toàn bộ views, ECharts dashboard, notifications bell, blocked UX, 378 tests + QA live.
- **API key lifecycle (T177–T179):** project-bound, 1 key/project, admin lock/resolution.
- **i18n (T180–T182 + T191):** foundation + dịch xong landing, user dashboard, shells (verify code 26/07). **AI refine Gemini (T186).**
- **Fix issue #1 (T190):** router guard role-aware, bỏ localStorage `vg_user` (22/07).
- Hạ tầng/tài liệu gốc đã xong: nginx (T100), prod profile (T101), DEPLOYMENT.md (T102), CI (T106–T107), DEMO_SCRIPT (T110), README CLI-first (T113), MCP guide (T114), architecture.md (T115).

**In Progress (10):** T109 chốt repo demo, T112 slides (nền presentation.html), T117 USER_GUIDE GĐ2, T118 bug backlog (còn 2/5 issue), T119 final E2E, T120–T121 perf/polish, T187–T189 graph lazy deep + layout (nhánh backup 24–25/07, chưa merge `poc`).

**New (9):** T99 compose prod, T103–T105 domain/SSL, T108 auto-deploy, T111 video, T116 API reference, T183 vị trí selector, T192 verify audit SSE live.

## 📊 Thay đổi lớn so với kế hoạch gốc

1. **Thêm nguyên Giai đoạn 2** (71 task T122–T192, 367h) sau review GĐ1 — không có trong kế hoạch ~578h ban đầu.
2. **Diagram đổi hướng:** Mermaid Use Case + Class → **SVG UML 2.5 Use Case + AI refine**; Class Diagram gỡ hẳn.
3. **Auth model:** JWT localStorage (kế hoạch ban đầu của phase-1 doc) → **cookie HttpOnly** (quyết định cuối, an toàn hơn).
4. **Realtime tách 2 kênh:** STOMP (graph, reports) và SSE (request events, audit logs).
5. **RULE cập nhật:** PostgreSQL/JPA/Flyway giờ là stack chính thức của control plane (trước đây nằm ngoài scope MVP); Redis/Kafka vẫn không dùng.

---

# 4. TASK DISTRIBUTION

## 👥 Workload theo thành viên (tính từ bảng Sprint Backlog FINAL, task chung chia đều)

| Thành viên | Tổng giờ | Giờ đã Done | S1 | S2 | S3 | S4 | Ghi chú phân công GĐ2 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| **Khoa**  | 229h | 196h | 32 | 47 | 86 | 64 | Auth core, ownership, quota, credit service, MCP senior tools, deep CPG, CLI push/watch, feature flags |
| **Vinh**  | 219h | 157h | 20 | 22 | 76 | 101 | Postgres/Flyway, security filter chain, WS auth, admin BE (overview/users/storage), anti-abuse, audit, plan/pricing CRUD |
| **Danh**  | 194h | 165h | 24 | 44 | 62 | 64 | Google OAuth, patch API, source viewer, UML SVG + inference, user views, admin dashboard ECharts, users UI, i18n |
| **Thịnh** | 182h | 163h | 34 | 42 | 32 | 74 | FE auth + shells + admin ops views, notifications/blocked UX, FE QA gates, graph polish, docs/README, điều phối |
| **Thái**  | 120h | 81h  | 10 | 15 | 24 | 71 | Test auth/ownership IT, user reports/notifications, announcements, admin reports, test suites BE, demo docs |

**Tổng: 945h.** Năng lực tham chiếu: 5 người × 8h × 12 ngày = 480h/sprint (dư năng lực; thực tế GĐ2 dồn vào tháng 7).

**Lưu ý:** Giờ còn lại chưa Done tập trung ở: Thái (demo video/slides/final QA ~39h), Vinh (deploy VPS/SSL/auto-deploy ~62h — phần lớn là task New chờ hạ tầng), Danh (i18n landing/polish còn lại ~29h), Thịnh (graph polish + vị trí selector ~19h), Khoa (parser robustness/caching ~33h).

## 📋 Task còn mở theo nhóm (ưu tiên đề xuất)

| Ưu tiên | Nhóm | Task | Người |
| --- | --- | --- | --- |
| 🔥 1 | Fix 2 issue còn lại | T183 (vị trí selector), T192 (verify audit SSE live) | Thịnh, Vinh |
| 🔥 2 | Merge graph polish về `poc` | T187–T189 (review + merge nhánh backup) | Thịnh, Khoa |
| 🔥 3 | Final QA + demo | T109, T110 (bổ sung GĐ2), T119, T117 | Thái, Thịnh |
| 🟡 4 | Deploy production | T99, T103–T105, T108 | Vinh |
| 🟡 5 | Demo video/slides | T111, T112 | Thái |
| 🟢 6 | Nợ kỹ thuật Sprint 3 | T78–T88, T93–T94, T138 | Khoa, Vinh, Danh |

---

# 5. CSV EXPORT GUIDE

## 📤 Export Status

✅ **5 file CSV đã tạo lại (2026-07-26)** trong `task-final/csv_exports/` từ đúng nội dung bản FINAL:

1. `product_backlog.csv` — Product Backlog (**PB01–PB26**, 26 dòng)
2. `release_backlog.csv` — Release Backlog (**RB01–RB66**, 66 dòng)
3. `sprint_backlog.csv` — Sprint Backlog (**T01–T192**, 192 task + header nhóm)
4. `pps_calculation.csv` — PPS (đã thêm 17 dòng tính năng GĐ2; tổng tính lại = 226.246 PPS / ED 1800 — bản cũ ghi 171.664 nhưng cộng lệch)
5. `ed_calculation.csv` — Environment Difficulty (giữ nguyên, ED = 30)

**Encoding:** UTF-8 with BOM (mở tiếng Việt trong Excel không lỗi font).

## 🔄 Usage Workflow

### Bước 1: Export CSV (sau khi sửa file .md)

```bash
cd <repo>/task-final
python export_to_csv.py
```

Script tự đọc `VibeGraph_WS3_Sprint-Trello-BBCH-ERD.md` cạnh nó và ghi vào `csv_exports/` — không cần sửa đường dẫn.

### Bước 2–4: Upload Drive → Google Sheets → Share team

Giữ nguyên quy trình cũ: kéo thả 5 CSV vào folder `VibeGraph Project/Sprint Backlogs/` trên Drive → "Open with Google Sheets" → Share cho Khoa, Danh, Vinh, Thái, Thịnh (Editor/Commenter).

### Validation trước khi upload

- [ ] `product_backlog.csv` = 26 dòng, `release_backlog.csv` = 66 dòng, `sprint_backlog.csv` có đủ T192
- [ ] Tiếng Việt hiển thị đúng trong Excel
- [ ] Cột State chỉ chứa: New / In Progress / Done (hoặc Removed)

---

# 6. UPDATE INSTRUCTIONS

## 🔄 Cách duy trì bản FINAL

1. **Chỉ sửa `task-final/`** — không sửa `task/` và `task-update/` nữa (archive).
2. Khi một task đổi trạng thái: sửa cột **State** (`New → In Progress → Done`) và **Note** (kèm bằng chứng: file path, ngày test, kết quả lệnh) trong `VibeGraph_WS3_Sprint-Trello-BBCH-ERD.md`.
3. Chạy lại `python export_to_csv.py` → upload Drive.
4. Task mới: đánh số tiếp **T193+** (RB tiếp **RB67+**, PB tiếp **PB27+**), xếp vào sprint phù hợp, có Assign + Estimate + Note.
5. Cập nhật dòng "**Số liệu realtime**" ở header file WS3 và bảng "Tiến độ realtime" ở Section 1 tài liệu này khi số Done thay đổi đáng kể.
6. Quy ước Note: bắt đầu bằng ✅ (Done) / 🚧 (In Progress) / ⬜ (New), kèm đường dẫn file code hoặc ngày QA làm bằng chứng.

## 🎯 Milestone đề xuất tiếp theo

1. ⚡ **Tuần này:** Fix 2 issue còn lại (T183, T192) + merge nhánh graph polish (T187–T189) → chạy final E2E (T119).
2. 🔥 **Trước bàn giao:** USER_GUIDE GĐ2 (T117) + demo script GĐ2 (T110) + chốt repo demo (T109) + video/slides (T111–T112).
3. 🟡 **Khi có VPS:** T99 → T103–T105 → T108 (deploy + SSL + auto-deploy).
4. 🟢 **Sau bàn giao:** nợ kỹ thuật Sprint 3 (OpenAPI, parser robustness, caching, pagination, publish npm CLI).

## 📁 File References

### Bản FINAL (nguồn sự thật — CHỈ CẬP NHẬT Ở ĐÂY):
- `task-final/VibeGraph_WS3_Sprint-Trello-BBCH-ERD.md` — Backlog + Sprint chi tiết ⭐
- `task-final/PROJECT_DOCUMENTATION_MASTER.md` — Tài liệu này
- `task-final/export_to_csv.py` — Script export CSV
- `task-final/csv_exports/*.csv` — 5 CSV (sinh tự động, có thể tạo lại)

### Archive (KHÔNG cập nhật nữa):
- `task/` — bản Scrum gốc (snapshot tới 2026-07-02)
- `task-update/` — phase docs + handoffs GĐ2 (snapshot tới 2026-07-19)

### Tài liệu kỹ thuật liên quan (ngoài task-final):
- `README.md` (2026-07-19, CLI-first) · `MCP_INTEGRATION.md` + `docs/mcp-integration.md` · `docs/local-patch.md` · `DEPLOYMENT.md` · `DEVOPS-GUIDE.md` · `DEMO_SCRIPT.md` · `VibeGraph-specs-2month/` (architecture, requirements, neo4j-schema, security-multiuser-roadmap, file-checklist)

---

## 🔍 Where to Find Information

| Cần biết...                        | Xem...                                        |
| ---------------------------------- | --------------------------------------------- |
| Tiến độ tổng thể + số liệu         | Section 1 — Executive Summary                 |
| Chi tiết module đã làm tới đâu     | Section 2 — Implementation Status             |
| Từng sprint đã/chưa làm gì         | Section 3 — Sprint Progress                   |
| Ai làm gì, còn bao nhiêu giờ       | Section 4 — Task Distribution                 |
| Cách export CSV                    | Section 5 — CSV Guide                         |
| Cách cập nhật tài liệu này         | Section 6 — Update Instructions               |
| Trạng thái từng task cụ thể        | `VibeGraph_WS3_Sprint-Trello-BBCH-ERD.md`     |

---

*Bản FINAL tạo: 2026-07-26 — đối chiếu tài liệu cũ + codebase thực tế + QA sessions*
*Maintained By: Thịnh (Leader) — cập nhật theo Section 6*
