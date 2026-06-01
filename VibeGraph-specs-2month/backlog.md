# VibeGraph — Sprint Board / Backlog

> Board nội bộ (untracked) cho Sprint planning. Chi tiết nguồn: `task-breakdown-8week.md`, `requirements-trimmed.md`, `file-checklist.md`.

## Sprint 1 — ✅ CLOSED (2026-05-30, vertical-slice DoD met)

Vertical slice: đăng ký project local → analyze (parse → Neo4j raw Driver) → `GET /api/projects/{id}/graph` → Sigma render. Build + test xanh trong audit local: `./mvnw.cmd test`, `./mvnw.cmd verify`, `npm run type-check`, `npm run build`, `npm run test:unit -- --run`. Neo4j integration coverage hiện chạy qua Testcontainers; CI Sprint 2 cần Docker daemon để kiểm chứng round-trip không phụ thuộc DB ngoài.

Checkpoint DoD đạt:
- **Phase 1 (visitors):** Class/Method/Field/SpringAnnotation + `ImportVisitorTest` (enabled, real).
- **Phase 2 (parser):** `ParserServiceTest` enabled + case fixture `sample-project` (nodes/edges non-empty).
- **Phase 3 (Neo4j):** `GraphSchemaTest` + `StorageAbstractionTest` (ArchUnit) pass; `Neo4jGraphRepositoryIT` chạy Neo4j thật bằng Testcontainers khi Docker daemon sẵn sàng.
- **Phase 4 (REST):** `ProjectControllerTest` + `GraphControllerTest` + `GraphServiceTest` (API `getFullGraph`/`searchNodes`).
- **Phase 5 (FE):** router → `GraphView`/`GraphCanvas`; `vue-tsc` type-check + build pass.

Commits (branch `poc`, **ahead origin/poc 4 — CHƯA push**):
- `5064535` docs: sync docs and governance to raw-Driver architecture
- `dfca4d1` refactor: migrate Neo4j OGM to raw Java Driver; fix External-stub enrichment
- `b2ba0c5` fix(web): render router from app entrypoint  *(không do agent tạo)*
- `42c9d44` test: enable Sprint 1 visitor and graph API coverage

Push khi muốn lưu remote: `git push -u origin poc`.

Hoãn có chủ đích → Sprint 2: `VibeGraphApplicationTests` còn `@Disabled`; CI cần Docker daemon để Testcontainers Neo4j giữ `mvn verify` và coverage gate 70% xanh ổn định.

---

## Sprint 2 — Backlog

### Cập nhật foundation hiện tại (2026-06-01)

- Sprint 1 vertical slice đã xong; `mvn test` pass.
- Blocker verify gate đã được xử lý bằng Testcontainers Neo4j: `Neo4jGraphRepositoryIT` chạy thật bằng container, 5 tests run, 0 skipped khi Docker daemon đang chạy.
- `mvn verify` đã xanh khi Docker daemon available; coverage gate 70% pass. CI/runner phải có Docker daemon để Testcontainers chạy on-track.
- Nếu Docker không available, Testcontainers integration test có thể skip; coverage có thể tụt lại dưới gate. Vì vậy CI không được coi runner không Docker là đủ điều kiện verify.
- Archive import end-to-end đã có: `ArchiveExtractor`, `ArchiveImportService` orchestration sync, và `POST /api/projects/import-archive` sync. Sync browser E2E đã pass: endpoint trả `200 OK`, status `ANALYZED`, progress `100`, frontend navigate sang `/projects/{id}/graph`.
- `ArchiveExtractor` đã hỗ trợ ZIP, TAR, TAR_GZ/TGZ; chỉ materialize `.java`; chống path traversal, absolute path, Windows drive path; reject TAR symlink/hardlink/special file; bỏ qua ignored paths; test `EMPTY_ARCHIVE` / `OVERSIZE` / `EXTRACTION_FAILED`.
- Async archive import đã có: `POST /api/projects/import-archive?async=true` trả `202 Accepted`, project status `ANALYZING`, progress `0`, background analyze qua `analysisExecutor`, status model `ANALYZED`/`FAILED`, và WebSocket status events trên `/topic/projects/{id}/status`.
- Frontend async support đã có: `importApi.uploadArchiveAsync(...)`, `useWebSocket.ts`, async mode trong `useArchiveImport`, `AddProjectArchive` prop `async?: boolean`, polling fallback `GET /api/projects/{id}`, watchdog timeout, và Vite SockJS fix `define.global = globalThis`.
- Async browser E2E đã pass qua poll fallback: nhận `202`, SockJS handshake `/ws/graph-updates/info` trả `200`, poll fallback thấy `ANALYZED`, navigate graph thành công. Case `no-java.zip` async trả `400` trước pha WebSocket.
- Default UI vẫn sync: `HomeView` render `<AddProjectArchive @imported="onImported" />`. Async support sẵn sàng qua `AddProjectArchive async` prop nhưng chưa bật mặc định.
- Parser D3 đã xong: `Signatures.method(ownerFqcn, methodName, paramTypes)`, `MethodVisitor` dùng `Signatures`, `SpringAnnotationVisitor` dùng `Signatures` cho HANDLES_ROUTE source. Format signature không đổi: `owner.method(param1,param2)`.
- Frontend Upload UI đã xong phần sync và async support; Home route `/` hiển thị upload UI sync theo mặc định.

| # | Item | Severity | Nguồn / Vị trí |
|---|------|----------|----------------|
| 1 | Testcontainers context smoke — enable `VibeGraphApplicationTests` | 🟡 Medium | `VibeGraphApplicationTests` (@Disabled) |
| 2 | Public deploy: **auth + rate-limit** | 🔴 Critical | no spring-security; REST/MCP/actuator |
| 3 | Public deploy: **archive upload safety + path validation** | 🔴 Critical | `import-archive`, archive extraction, `ProjectServiceImpl.validateRootPath` fallback, `GithubImportRequest` |
| 4 | Archive upload + async analyze + WebSocket progress | ✅ Done / watch debt | sync + async import done; poll fallback E2E pass; WS push winning path not yet observed |
| 5 | `getFullGraph` pagination/limits | 🟠 Important | `Neo4jGraphRepository.getFullGraph` |
| 6 | INJECTS single source + D1/D2/D3 parser cleanup | 🟠 Important | `FieldVisitor`+`SpringAnnotationVisitor`; visitors |
| 7 | Persist project registry to Neo4j | 🟠 Important | `ProjectServiceImpl` (in-memory) |
| 8 | Exposed stubs → `FeatureNotImplementedException` | 🟠 Important | `getNeighborhood`/`getImpact`, `parseFileWithCache` |
| 9 | Parser/schema parity | 🟠 Important | `Package`/`File` nodes, `OWNS`/`CONTAINS`/`DEFINES`, `ANNOTATED_BY`, unresolved CALLS/stub/confidence |
| 10 | Frontend scaffold completion | 🟠 Important | layout panels, filters/focus, graph controls/search, diagram UI, `useWebSocket` |
| 11 | Diagram/MCP implementation | 🟠 Important | diagram services/repository/controller; MCP tools/services/analyzer |

### Chi tiết
1. **Testcontainers context smoke** — Neo4j repository integration coverage đã được đưa lên Testcontainers và verify gate đã xanh khi Docker daemon chạy. Phần còn lại: enable/giữ on-track `VibeGraphApplicationTests.contextLoads()` nếu cần context smoke riêng. AC CI: `mvn verify` chạy trên runner có Docker daemon; không phụ thuộc Neo4j ngoài.
2. **Auth + rate-limit** — auth (API key/OIDC) cho REST + MCP; ẩn/bảo vệ actuator `metrics`/`prometheus`; rate-limit `analyze` + `import-github`.
3. **Archive upload safety + path validation** — flow chính Sprint 2 là upload ZIP/TAR, nên phải chặn path traversal (`../`, absolute path), symlink nguy hiểm, file quá lớn, archive bomb; local-path registration chỉ giữ dev/internal fallback và phải tắt/allow-list khi expose demo công khai. Giữ regex chặn `github.com` cho import. *(🔴 xử lý trước khi expose demo công khai)*
4. **Archive upload + async analyze + WS** — done cho Sprint 2 core: `ArchiveImportService` orchestration, sync `POST /api/projects/import-archive` (`200 OK`, `ANALYZED`, progress `100`), async `?async=true` (`202 Accepted`, `ANALYZING`, progress `0`), background analyze qua `analysisExecutor`, status publisher `/topic/projects/{id}/status`, FE upload UI, FE async support, sync E2E pass, async E2E pass qua poll fallback. Watch debt: WebSocket push event path chưa được quan sát thắng poll vì analyze quá nhanh.
5. **Pagination** — `LIMIT`/phân trang `getFullGraph` (FR-09 "paginated").
6. **INJECTS single source + D1/D2/D3** — D3 đã xong bằng `Signatures.method(...)` và test ghim format; format signature không đổi. Còn lại: gộp INJECTS một nguồn; `TypeNames.resolveFqn`; `springLayer` một nguồn (+ test ghim).
7. **Persist registry** — lưu project metadata vào `:Project` node, đọc lại khi `list/get` (tránh orphan khi restart).
8. **Stubs → 501** — đổi `UnsupportedOperationException` sang `FeatureNotImplementedException` khi wire endpoint scaffold.
9. **Parser/schema parity** — phát `Package`/`File` nodes và structural edges; thêm `ANNOTATED_BY`; hoàn tất unresolved CALLS theo contract hoặc cập nhật schema nếu quyết định giữ `External` stub.
10. **Frontend scaffold completion** — biến các panel/control composable từ TODO thành logic thật trước khi demo UX.
11. **Diagram/MCP implementation** — không tính diagram/MCP là done cho tới khi có endpoint/tool thật và test enabled.

### Việc tiếp theo ưu tiên

1. Decide whether to enable async UI by default in `HomeView`; hiện mặc định vẫn sync, async chỉ bật khi truyền `AddProjectArchive async`.
2. Fix dev CORS gap cho `http://127.0.0.1:5173` hoặc chuẩn hóa qua Vite proxy.
3. Loại `rootPath` absolute server temp path khỏi public response/DTO.
4. Persist project registry thay vì in-memory.
5. Dùng graph cleanup khi analyze failure; async failure hiện có thể để lại project `FAILED` với `rootPath` trỏ tới workspace đã cleanup và partial graph debt.
6. Test WebSocket push path với project lớn hơn hoặc delayed analyze để quan sát push thắng poll fallback.
7. Thêm limit/pagination cho `getFullGraph` hoặc project size cap trước khi demo project lớn.
8. Dùng lại archive pipeline cho GitHub import end-to-end sau khi archive local đã an toàn.

> Mục 2–11 từ review 2026-05-30. Ưu tiên: **#2/#3 (Critical)** trước khi expose demo công khai.
