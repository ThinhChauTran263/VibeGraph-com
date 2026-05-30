# VibeGraph — Sprint Board / Backlog

> Board nội bộ (untracked) cho Sprint planning. Chi tiết nguồn: `task-breakdown-8week.md`, `requirements-trimmed.md`, `file-checklist.md`.

## Sprint 1 — ✅ CLOSED (2026-05-30, vertical-slice DoD met)

Vertical slice: đăng ký project local → analyze (parse → Neo4j raw Driver) → `GET /api/projects/{id}/graph` → Sigma render. Build + test xanh trong audit local: `./mvnw.cmd test`, `./mvnw.cmd verify`, `npm run type-check`, `npm run build`, `npm run test:unit -- --run`. Lưu ý `Neo4jGraphRepositoryIT` tự skip khi không có Neo4j reachable, nên CI Sprint 2 cần Testcontainers để kiểm chứng round-trip không phụ thuộc DB ngoài.

Checkpoint DoD đạt:
- **Phase 1 (visitors):** Class/Method/Field/SpringAnnotation + `ImportVisitorTest` (enabled, real).
- **Phase 2 (parser):** `ParserServiceTest` enabled + case fixture `sample-project` (nodes/edges non-empty).
- **Phase 3 (Neo4j):** `GraphSchemaTest` + `StorageAbstractionTest` (ArchUnit) pass; `Neo4jGraphRepositoryIT` đã enable nhưng có điều kiện skip nếu không có Neo4j reachable.
- **Phase 4 (REST):** `ProjectControllerTest` + `GraphControllerTest` + `GraphServiceTest` (API `getFullGraph`/`searchNodes`).
- **Phase 5 (FE):** router → `GraphView`/`GraphCanvas`; `vue-tsc` type-check + build pass.

Commits (branch `poc`, **ahead origin/poc 4 — CHƯA push**):
- `5064535` docs: sync docs and governance to raw-Driver architecture
- `dfca4d1` refactor: migrate Neo4j OGM to raw Java Driver; fix External-stub enrichment
- `b2ba0c5` fix(web): render router from app entrypoint  *(không do agent tạo)*
- `42c9d44` test: enable Sprint 1 visitor and graph API coverage

Push khi muốn lưu remote: `git push -u origin poc`.

Hoãn có chủ đích → Sprint 2: `VibeGraphApplicationTests` còn `@Disabled`; `Neo4jGraphRepositoryIT` cần được chạy bằng Testcontainers/Neo4j thật trong CI thay vì dựa vào DB local.

---

## Sprint 2 — Backlog

| # | Item | Severity | Nguồn / Vị trí |
|---|------|----------|----------------|
| 1 | Testcontainers context smoke — enable `VibeGraphApplicationTests` | 🟡 Medium | `VibeGraphApplicationTests` (@Disabled) |
| 2 | Public deploy: **auth + rate-limit** | 🔴 Critical | no spring-security; REST/MCP/actuator |
| 3 | Public deploy: **SSRF / path validation** | 🔴 Critical | `ProjectServiceImpl.validateRootPath` (allowed-root), `GithubImportRequest` |
| 4 | Async analyze + WebSocket progress | 🟠 Important | `ProjectController.analyze`, `graph/websocket/*` |
| 5 | `getFullGraph` pagination/limits | 🟠 Important | `Neo4jGraphRepository.getFullGraph` |
| 6 | INJECTS single source + D1/D2/D3 parser cleanup | 🟠 Important | `FieldVisitor`+`SpringAnnotationVisitor`; visitors |
| 7 | Persist project registry to Neo4j | 🟠 Important | `ProjectServiceImpl` (in-memory) |
| 8 | Exposed stubs → `FeatureNotImplementedException` | 🟠 Important | `getNeighborhood`/`getImpact`, `parseFileWithCache` |
| 9 | Parser/schema parity | 🟠 Important | `Package`/`File` nodes, `OWNS`/`CONTAINS`/`DEFINES`, `ANNOTATED_BY`, unresolved CALLS/stub/confidence |
| 10 | Frontend scaffold completion | 🟠 Important | layout panels, filters/focus, graph controls/search, diagram UI, `useWebSocket` |
| 11 | Diagram/MCP implementation | 🟠 Important | diagram services/repository/controller; MCP tools/services/analyzer |

### Chi tiết
1. **Testcontainers context smoke** — thêm Testcontainers Neo4j, enable `VibeGraphApplicationTests.contextLoads()` (hiện cần Driver bean + `Neo4jMigrationRunner` lúc startup). AC: context load xanh trong CI không cần Neo4j ngoài.
2. **Auth + rate-limit** — auth (API key/OIDC) cho REST + MCP; ẩn/bảo vệ actuator `metrics`/`prometheus`; rate-limit `analyze` + `import-github`.
3. **SSRF / path validation** — set `VIBEGRAPH_PROJECTS_ALLOWED_ROOT` ở prod (đang rỗng) hoặc tắt đăng ký local-path trên demo công khai; giữ regex chặn `github.com` cho import. *(🔴 xử lý trước khi expose demo công khai)*
4. **Async analyze + WS** — `AsyncConfig` virtual-thread executor; `analyze` trả `202` ngay; broadcast progress qua `/topic/projects/{id}/status`; FE `useWebSocket`.
5. **Pagination** — `LIMIT`/phân trang `getFullGraph` (FR-09 "paginated").
6. **INJECTS single source + D1/D2/D3** — gộp INJECTS một nguồn; `TypeNames.resolveFqn`; `Signatures.method(...)`; `springLayer` một nguồn (+ test ghim).
7. **Persist registry** — lưu project metadata vào `:Project` node, đọc lại khi `list/get` (tránh orphan khi restart).
8. **Stubs → 501** — đổi `UnsupportedOperationException` sang `FeatureNotImplementedException` khi wire endpoint scaffold.
9. **Parser/schema parity** — phát `Package`/`File` nodes và structural edges; thêm `ANNOTATED_BY`; hoàn tất unresolved CALLS theo contract hoặc cập nhật schema nếu quyết định giữ `External` stub.
10. **Frontend scaffold completion** — biến các panel/control composable từ TODO thành logic thật trước khi demo UX.
11. **Diagram/MCP implementation** — không tính diagram/MCP là done cho tới khi có endpoint/tool thật và test enabled.

> Mục 2–11 từ review 2026-05-30. Ưu tiên: **#2/#3 (Critical)** trước khi expose demo công khai.
