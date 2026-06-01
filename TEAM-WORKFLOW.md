# VibeGraph — Team Workflow Playbook

> **Audience:** Tất cả 5 dev. Đọc ngay ngày đầu onboard.
> **Status:** Locked Sprint 1 day 1. Đổi quyết định nào phải sync cả team.
> **Owner:** Dev 5 (Integration / DevOps).

---

## 1. Quyết định đã chốt

| Quyết định | Lựa chọn | Lý do |
|------------|---------|-------|
| API contract | OpenAPI/Swagger Sprint 1 + codegen TS | Tránh schema drift backend ↔ frontend |
| Git branching | GitFlow (`main` / `develop` / `feature/*`) | Team 5 người, an toàn cho release |
| Cadence | Daily standup 15 phút + demo cuối tuần 30 phút | Bắt vấn đề nhanh + verify integration thường xuyên |

---

## 2. API Contract — OpenAPI driven

### 2.1 Backend setup (Sprint 1, Dev 2 làm trong day 2)

**Dependency thêm vào `pom.xml`:**
```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>2.6.0</version>
</dependency>
```

**Endpoints sau khi setup:**
- `http://localhost:8080/v3/api-docs` — JSON spec
- `http://localhost:8080/swagger-ui.html` — UI

**Convention bắt buộc cho Backend dev:**
- Mọi `@RestController` method có `@Operation(summary = "...")`
- Mọi DTO field có `@Schema(description = "...", example = "...")`
- Custom error response → `@ApiResponse(responseCode = "404", description = "...")`
- WebSocket message: tài liệu hóa schema riêng (OpenAPI không cover WS) — hợp đồng WS hiện mô tả trong `VibeGraph-specs-2month/requirements-trimmed.md` (FR-07) + `architecture.md`, chưa tách file schema riêng

### 2.2 Frontend codegen (Sprint 1, Dev 3 làm)

**Tool:** `openapi-typescript` (lighter than `openapi-generator`)

```bash
cd vibegraph-web
npm install -D openapi-typescript

# package.json scripts:
# "gen:api": "openapi-typescript http://localhost:8080/v3/api-docs -o src/types/api.ts"
```

**Workflow:**
1. Dev 2 commit thay đổi API → push
2. Dev 3 chạy `npm run gen:api` → cập nhật `src/types/api.ts`
3. TypeScript báo lỗi nếu component dùng field cũ
4. Fix → PR

**CI guard:** `.github/workflows/ci.yml` thêm step:
```yaml
- run: npm run gen:api
- run: git diff --exit-code src/types/api.ts
  # Fail nếu types out-of-sync với backend
```

### 2.3 Definition of Done
- [ ] Sprint 1 week 1: Swagger UI hoạt động, có ≥ 1 endpoint annotated
- [ ] Sprint 1 week 2: Frontend `src/types/api.ts` được generate, ít nhất 1 component dùng
- [ ] Sprint 2 onwards: không merge PR backend nếu thiếu `@Operation` cho endpoint mới
- [ ] CI fail nếu types drift

---

## 3. Git Workflow — GitFlow

### 3.1 Branch model

```
main             (production-ready, mỗi tag = 1 release)
  ↑ merge khi release
develop          (integration branch, demo cuối tuần chạy từ đây)
  ↑ merge feature
feature/<name>   (1 task = 1 branch, lifetime 2-5 ngày)
hotfix/<name>    (sửa bug production, branch từ main)
release/<ver>    (chuẩn bị release, freeze feature, chỉ fix bug)
```

### 3.2 Naming convention

| Branch | Format | Ví dụ |
|--------|--------|-------|
| Feature | `feature/<sprint>-<dev>-<short-desc>` | `feature/sp1-dev1-class-visitor` |
| Bug fix | `bugfix/<issue>-<short-desc>` | `bugfix/123-watcher-leak` |
| Hotfix | `hotfix/<short-desc>` | `hotfix/neo4j-timeout` |
| Release | `release/<version>` | `release/0.2.0` |

### 3.3 Commit message — Conventional Commits

Format: `<type>: <description>` (xem `~/.claude/rules/ecc/common/git-workflow.md`)

| Type | Khi dùng |
|------|----------|
| `feat` | Tính năng mới |
| `fix` | Sửa bug |
| `refactor` | Refactor không đổi behavior |
| `test` | Thêm/sửa test |
| `docs` | Tài liệu |
| `chore` | Build, deps, config |
| `perf` | Tối ưu performance |
| `ci` | GitHub Actions, Docker |

**Ví dụ:**
```
feat: add ClassVisitor for parser module
fix: resolve symbol solver NPE on generic types
refactor: extract MermaidGenerator to separate service
```

### 3.4 Pull Request rules

- **Mở PR khi:** branch ready, CI green, self-review xong
- **Reviewer:** ít nhất 1 dev khác (quy ước backend ↔ backend, frontend ↔ frontend)
- **Merge điều kiện:**
  - [ ] CI green (build + test + lint)
  - [ ] ≥ 1 approval
  - [ ] Không có comment `request changes` chưa giải quyết
  - [ ] Coverage không giảm (target 70%, xem `common/testing.md`)
  - [ ] Conflicts resolved
- **Merge strategy:** **Squash and merge** (giữ history `develop` sạch)
- **Delete branch sau merge:** auto

### 3.5 Release flow

```
1. Cuối Sprint 2: tạo branch release/0.1.0 từ develop
2. Chỉ commit bug fix vào release/0.1.0
3. Demo cho stakeholder
4. Merge release/0.1.0 → main (tag v0.1.0)
5. Merge release/0.1.0 → develop (back-merge fixes)
6. GitHub Action build image → push GHCR với tag v0.1.0
```

### 3.6 Quy ước an toàn

- [ ] **KHÔNG force-push `main` hoặc `develop`** (branch protection enabled)
- [ ] Force-push chỉ trên feature branch của bản thân
- [ ] Hotfix luôn merge cả về `main` VÀ `develop`
- [ ] Tag chỉ tạo trên `main`, format `vX.Y.Z` (semver)

---

## 4. Cadence — Daily standup + Weekly demo

### 4.1 Daily Standup — 09:30 mỗi ngày, 15 phút

**Format async-first** (Slack/Discord channel `#vibegraph-standup`), face-to-face khi cần discuss sâu.

Mỗi dev post 09:30:
```
**Hôm qua:** ...
**Hôm nay:** ...
**Blockers:** ... (mention người liên quan)
```

**Rules:**
- Cap 15 phút, ai có blocker thì stay sau để discuss
- Không update task list trong standup (đã có file-checklist.md)
- Blocker > 4 giờ → escalate ngay tới Tech Lead

### 4.2 Weekly Demo — Thứ Sáu 16:00, 30 phút

**Quy tắc bắt buộc:**
- 16:00 sharp, full stack chạy (`docker compose up -d`)
- Trên branch `develop` (không demo từ feature branch chưa merge)
- 1 dev luân phiên drive demo (tuần 1: Dev 1, tuần 2: Dev 2...)

**Agenda:**
1. **Recap milestones tuần** (5 phút) — Tech Lead
2. **Demo end-to-end** (15 phút) — driver dev
3. **Blockers / risks tuần sau** (5 phút) — cả team
4. **Action items** (5 phút) — capture vào GitHub Issues/Discussions (kênh `#vibegraph-dev`)

**Tiêu chí demo PASS:**
- Flow end-to-end chạy được trên Docker (không phải localhost dev mode)
- Không có exception trong logs
- Performance trong target NFR-01

**Tiêu chí FAIL → nợ kỹ thuật:**
- Demo skip vì "chưa kịp" → ưu tiên xử lý đầu tuần sau
- Component crash giữa demo → tạo bugfix branch ngay

### 4.3 Sprint review — cuối Sprint 2, 4, 6

- Stakeholder/khách hàng tham gia
- 60 phút: 30 phút demo + 20 phút Q&A + 10 phút retrospective nội bộ

---

## 5. 5 quy ước thực tế giúp đỡ va chạm

### 5.1 Test fixture chung
**Owner:** Dev 5 setup ngay Sprint 1 day 2.
**Path:** `src/test/resources/sample-projects/spring-boot-sample/`
**Nội dung tối thiểu:**
- 3 `@RestController` (2 method mỗi cái)
- 3 `@Service`
- 3 `@Repository`
- 2 entity, 2 DTO
- 1 `@Scheduled` task
- 1 `@KafkaListener` (mock dependency)
- 1 anti-pattern intentional (Controller gọi Repository trực tiếp) — để test warning detection

**Đạt được khi:**
- [ ] Mọi unit test parser/graph/diagram/mcp đều dùng fixture này
- [ ] CI build sample project thành công (verify code valid)

### 5.2 MCP skeleton sớm
**Owner:** Dev 5 Sprint 1 day 5 (không đợi Sprint 3).
**Mục tiêu:** Endpoint `/mcp` trả handshake + 1 dummy tool `ping`. Cursor/Kiro/Claude Code connect được.
**Lý do:** Risk Spring AI MCP API thay đổi → flush sớm.

### 5.3 Pair programming Day 1-3
**Bắt buộc:** Dev 1 + Dev 2 pair 3 ngày đầu chốt luồng `ParseResult` → `NodeData`/`EdgeData` → `GraphRepository` → `Neo4jGraphRepository` (raw Cypher).
**Format:** Cùng IDE (VS Code Live Share / IntelliJ Code With Me), 4 giờ/ngày.
**Output:** Interface `ParserService` + `AnalyzeService` lock cứng, có 1 happy-path integration test pass.

### 5.4 Logging convention
**Bắt buộc từ Sprint 1.**

```java
import org.slf4j.MDC;

// Khi nhận request hoặc start analyze:
MDC.put("projectId", projectId);
MDC.put("correlationId", UUID.randomUUID().toString());
try {
    // ... business logic
} finally {
    MDC.clear();
}
```

**Log format (logback-spring.xml):**
```
%d{ISO8601} [%X{correlationId}] [%X{projectId}] %-5level %logger{36} - %msg%n
```

**Đạt được khi:**
- [ ] Mọi log line trong realtime flow (file change → parse → push) có cùng `correlationId`
- [ ] Grep theo `correlationId` trace được full flow

### 5.5 Neo4j schema versioning
**Owner:** Dev 2 Sprint 1.
**Cách:** Trạng thái schema/migration do cơ chế migration (`Neo4jMigrationRunner` + metadata trong Neo4j) theo dõi, KHÔNG lưu trong entity Java. Khi schema đổi:
1. Thêm Cypher migration mới dưới `src/main/resources/db/migration/` (vd `V2__*.cypher`)
2. `Neo4jMigrationRunner` áp dụng lúc startup và ghi lại version đã áp dụng
3. Nếu mismatch không tự migrate được → log WARN, hint user `docker compose down -v` (Phase 1)
4. Phase 2: tự động migrate

---

## 6. Onboarding checklist (dev mới đọc ngày đầu)

### Setup môi trường
- [ ] Clone repo, đọc `README.md`
- [ ] Cài Docker, Java 21, Node 22, Maven 3.9
- [ ] Chạy `docker compose up -d` thành công
- [ ] Đọc `DEVOPS-GUIDE.md` Quickstart

### Đọc specs (theo thứ tự)
1. [ ] `VibeGraph-specs-2month/README.md` (10 phút) — nguồn chân lý thực thi MVP
2. [ ] `VibeGraph-specs-2month/requirements-trimmed.md` (20 phút) — FR + acceptance
3. [ ] `VibeGraph-specs-2month/architecture.md` (30 phút) — system design + Neo4j schema
4. [ ] `VibeGraph-specs-2month/task-breakdown-8week.md` (10 phút) — task theo Sprint
5. [ ] `TEAM-WORKFLOW.md` (file này, 15 phút)

### Đọc MODULE-GUIDE module mình phụ trách
- Dev 1: `parser/MODULE-GUIDE.md`
- Dev 2: `common/`, `graph/MODULE-GUIDE.md`
- Dev 3: (frontend) `vibegraph-web/README.md`
- Dev 4: `diagram/MODULE-GUIDE.md` *(steering/ đã defer post-MVP — bỏ qua)*
- Dev 5: `watcher/`, `mcp/MODULE-GUIDE.md`, `DEVOPS-GUIDE.md`

### Verify ready
- [ ] Run `mvn test` pass
- [ ] Run `cd vibegraph-web && npm run test:unit` pass
- [ ] Open Swagger UI: `http://localhost:8080/swagger-ui.html`
- [ ] Open Neo4j browser: `http://localhost:7474`
- [ ] Tham gia channel `#vibegraph-standup`, `#vibegraph-dev`

**Mục tiêu:** dev mới ready code task đầu tiên trong **1 ngày**.

---

## 7. RACI (ai làm gì)

| Hạng mục | Dev 1 | Dev 2 | Dev 3 | Dev 4 | Dev 5 |
|----------|-------|-------|-------|-------|-------|
| Parser engine | **R** | C | I | I | I |
| Neo4j + REST API | C | **R** | I | I | I |
| WebSocket | I | **R** | C | I | C |
| Sigma.js graph | I | C | **R** | C | I |
| Mermaid diagrams | I | C | C | **R** | I |
| MCP server | C | C | I | I | **R** |
| Steering files | I | I | I | **R** | C |
| File watcher | I | C | I | I | **R** |
| Docker / CI | I | I | I | I | **R** |
| Test fixture | C | C | I | I | **R** |
| OpenAPI codegen | I | **R** | C | I | I |

R=Responsible, C=Consulted, I=Informed.

---

## 8. Risk register (cập nhật weekly demo)

| Risk | Owner | Mitigation | Status |
|------|-------|-----------|--------|
| JavaParser Symbol Solver fail trên generic phức tạp | Dev 1 | Fallback confidence=0.5, log warning | Open |
| Spring AI MCP Starter API breaking change | Dev 5 | Pin version, MCP skeleton Sprint 1 | Open |
| Neo4j performance degrade khi graph > 30k nodes | Dev 2 | Index sớm, paginate, load test Sprint 2 | Open |
| WatchService miss event trên macOS | Dev 5 | Polling fallback 5s | Open |
| Schema drift backend ↔ frontend | Dev 2 + Dev 3 | OpenAPI codegen + CI guard | Mitigated |
| Sequence diagram phức tạp | Dev 4 | Cap depth=5, defer advanced | Open |

---

## 9. Communication channels

| Kênh | Mục đích |
|------|---------|
| `#vibegraph-standup` | Daily standup async |
| `#vibegraph-dev` | Discuss kỹ thuật, share link |
| `#vibegraph-prs` | Bot post PR notifications |
| `#vibegraph-alerts` | CI fail, prod alert |
| GitHub Issues | Bug, feature request, tracking |
| GitHub Discussions | Q&A, design proposals |

**Quy ước:**
- Mention `@channel` chỉ khi blocker > 4 giờ
- DM cá nhân chỉ cho chuyện riêng tư — kỹ thuật luôn public channel để cả team học
- Code review comment trên GitHub, không Slack


---

## 10. Removed legacy docs

Bộ tài liệu dài hạn cũ `VibeGraph-specs/` đã bị gỡ khỏi repo (xem `VibeGraph-specs-2month/README.md`). Các file cũ KHÔNG còn là nguồn chân lý; một số không có bản thay thế:

- `websocket-schema.md` — chưa có bản thay thế; hợp đồng WS hiện nằm trong `requirements-trimmed.md` (FR-07) + `architecture.md`
- `demo-log.md` — bỏ; action item ghi vào GitHub Issues/Discussions
- `CONTEXT-PROMPT.md` — bỏ; overview nằm trong `VibeGraph-specs-2month/README.md`
- `requirements.md` → `VibeGraph-specs-2month/requirements-trimmed.md`
- `architecture.md` → `VibeGraph-specs-2month/architecture.md`
- `task-breakdown.md` → `VibeGraph-specs-2month/task-breakdown-8week.md`

**Nguồn chân lý hiện tại:** thư mục `VibeGraph-specs-2month/` — `README.md`, `architecture.md`, `requirements-trimmed.md`, `task-breakdown-8week.md`, `file-checklist.md`, `deployment-plan.md`.
