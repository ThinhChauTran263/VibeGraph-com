# VibeGraph - Phân rã công việc (8 tuần)

Kế hoạch này được sắp xếp theo thứ tự phụ thuộc, không phải theo ranh giới module tưởng tượng. Codebase hiện tại là một backend Spring Boot đơn lẻ cộng với `vibegraph-web`.

## Kế hoạch Sprint

| Sprint | Tuần | Mục tiêu |
|---|---|---|
| 1 | 1-2 | Lát cắt dọc cục bộ: parse folder -> Neo4j -> REST -> đồ thị Sigma |
| 2 | 3-4 | Realtime, upload ZIP/TAR làm flow chính, import GitHub phụ, và diagram |
| 3 | 5-6 | MCP, độ bền vững, hiệu năng, hoàn thiện UI |
| 4 | 7-8 | Triển khai, tài liệu, sửa lỗi, hoàn thiện demo |

## Sprint 1 - Lát cắt dọc nền tảng

Cột mốc lịch sử của Sprint 1: dev có thể đăng ký một project Java cục bộ bằng local path, phân tích nó, gọi `GET /api/projects/{id}/graph`, và thấy một đồ thị trên trình duyệt. Từ quyết định product 2026-05-31, local path không còn là UX chính; Sprint 2 chuyển sang upload ZIP/TAR archive.

### Phase 1: Hợp đồng parser và các visitor

| # | Task | Tiêu chí chấp nhận |
|---|---|---|
| 1.1 | Hoàn thiện hợp đồng dữ liệu parser: `NodeData`, `EdgeData`, `ParseResult` | Hợp đồng khớp với `NodeTypeEnum`, `EdgeTypeEnum`, và `graph.ts` ở frontend |
| 1.2 | `ClassVisitor` -> `NodeData` | `ClassVisitorTest` pass với việc trích xuất class/interface/enum |
| 1.3 | `MethodVisitor` -> `NodeData` | `MethodVisitorTest` pass với params, return type, throws, constructor, route annotation |
| 1.4 | `FieldVisitor` -> `NodeData` | `FieldVisitorTest` pass với type, visibility, static/final, injection annotation |
| 1.5 | `ImportVisitor` -> `EdgeData` | Các import tạo ra `IMPORTS` edge và test được bật |
| 1.6 | Trích xuất Spring annotation | Spring layer, route, và metadata annotation được test |

Điểm kiểm tra:

- `mvn test -Dtest=ClassVisitorTest,MethodVisitorTest,FieldVisitorTest,ImportVisitorTest,SpringAnnotationVisitorTest` pass.
- Các test visitor không còn `@Disabled`.

### Phase 2: Parser service và structural edges

| # | Task | Tiêu chí chấp nhận |
|---|---|---|
| 1.7 | Hiện thực `FileUtils.scanJavaFiles` và `HashUtils.sha256` | Các test tiện ích được bật và pass |
| 1.8 | Hiện thực `ParserServiceImpl.parseFile(Path)` | Parse một file Java thành nodes và structural edges |
| 1.9 | Hiện thực structural edges | `HAS_METHOD`, `HAS_FIELD`, `HAS_INNER`, `EXTENDS`, `IMPLEMENTS` đã có; `Package`/`File` nodes và `OWNS`/`CONTAINS`/`DEFINES` còn là carry-over Sprint 2/3 |
| 1.10 | Hiện thực `ParserServiceImpl.parseProject(Path)` | Parse đệ quy các file `.java` và tổng hợp kết quả |
| 1.11 | Hiện thực xử lý lỗi khi parse | File lỗi tạo ra cảnh báo và không làm hỏng toàn bộ project |

Điểm kiểm tra:

- `ParserServiceTest` được bật và pass cho `parseFile` và `parseProject`.
- Project mẫu dưới `src/test/resources/sample-project` tạo ra nodes và edges không rỗng.

### Phase 3: Lưu trữ Neo4j

| # | Task | Tiêu chí chấp nhận |
|---|---|---|
| 1.12 | Xác nhận khởi động Neo4j cục bộ | `docker compose up -d neo4j` chạy được từ thư mục gốc repo |
| 1.13 | Hiện thực schema migration runner hoặc migration thủ công có tài liệu | `V1__init_schema.cypher` được áp dụng trước khi ghi đồ thị |
| 1.14 | Hiện thực `Neo4jGraphRepository.upsertProject` | Node project có thể được tạo/cập nhật |
| 1.15 | Hiện thực `upsertNodes` và `upsertEdges` | Dùng Cypher tham số hóa với `UNWIND` và `MERGE` |
| 1.16 | Hiện thực `getFullGraph` | Trả về dữ liệu tương thích với `GraphDataResponse` |
| 1.17 | Hiện thực `deleteFile` | Xóa hoặc soft-delete nodes/edges của một file theo quyết định lưu trữ |
| 1.18 | Bật test ArchUnit cho lớp trừu tượng lưu trữ | Các service không import trực tiếp Neo4j API |

Điểm kiểm tra:

- Test tích hợp Neo4j hoặc xác minh thủ công cục bộ có thể lưu và đọc một đồ thị.
- Không service nào ngoài repository impl import Neo4j API.

### Phase 4: REST API

| # | Task | Tiêu chí chấp nhận |
|---|---|---|
| 1.19 | Hiện thực `ProjectService` và `ProjectController` bản MVP | `POST /api/projects` đăng ký một đường dẫn gốc cục bộ |
| 1.20 | Hiện thực `AnalyzeService` bản MVP | `POST /api/projects/{id}/analyze` parse và lưu đồ thị |
| 1.21 | Hiện thực `GraphService` và `GraphController` bản MVP | `GET /api/projects/{id}/graph` trả về nodes, edges, và stats |
| 1.22 | Hiện thực `GlobalExceptionHandler` | Lỗi validation, not found, parse, và lỗi chung được map sang các response có kiểu |
| 1.23 | Cấu hình CORS cho môi trường dev | `http://localhost:5173`, `http://127.0.0.1:5173`, và origin của frontend Docker được cho phép ở dev |

Điểm kiểm tra:

- Các test controller/service cho đăng ký project, analyze, và lấy đồ thị đều pass.
- Luồng curl thủ công cục bộ chạy được với Neo4j.

### Phase 5: Frontend đồ thị bản MVP

| # | Task | Tiêu chí chấp nhận |
|---|---|---|
| 1.24 | Sửa hợp đồng type ở frontend | `npm run type-check` pass |
| 1.25 | Thêm các route router cho view home và graph | App không còn render trang khởi đầu của Vue |
| 1.26 | Hiện thực API client có kiểu | `projectApi` và `graphApi` khớp với response wrapper của backend |
| 1.27 | Hiện thực `graphAdapter.ts` | Chuyển response đồ thị từ API thành dữ liệu Graphology |
| 1.28 | Hiện thực `useSigma` và `GraphCanvas.vue` bản MVP | Render nodes/edges với kích thước ổn định và dọn dẹp đúng cách |
| 1.29 | Hiện thực layout tối thiểu và các trạng thái loading/error | Người dùng có thể kích hoạt/xem đồ thị mà không gặp UI hỏng |

Điểm kiểm tra:

- `npm run type-check` pass.
- Trình duyệt hiển thị đồ thị cho project mẫu cục bộ đã được phân tích.

### Đã hoàn thành sớm (kéo từ Sprint 2 về)

Một số task vốn được lên kế hoạch cho Sprint 2 đã được hoàn thành sớm ngay trong Sprint 1 khi hoàn thiện lát cắt dọc. Liệt kê ở đây để giữ bức tranh trạng thái rõ ràng.

| # | Task | Trạng thái |
|---|---|---|
| 2.11 | INJECTS edges (phát hiện injection qua constructor và Lombok) | ✅ ĐÃ XONG sớm trong Sprint 1 — đã xác minh 1→17 INJECTS edges. |
| 2.13 | CALLS edges đã resolve qua `JavaSymbolSolver` | 🟡 MỘT PHẦN trong Sprint 1 — đã có CALLS cho resolved in-project calls qua project-wide type solver. Chưa có stub-on-failure, chưa log tỉ lệ resolved, và chưa xử lý unresolved/library calls theo confidence thấp. |
| 2.12 | Gộp Spring layer detection vào `SpringAnnotationVisitor` | 🟡 MỘT PHẦN — đã thêm xử lý `CONFIG`/`ENTITY`/`@ControllerAdvice` nhưng đặt trong `ClassVisitor.springLayer()` thay vì `SpringAnnotationVisitor`; việc gộp CHƯA hoàn tất (xem nợ D1). |

## Sprint 2 - Tính năng cốt lõi

Cột mốc: người dùng có thể upload file ZIP/TAR của một project Java, xem tiến độ qua WebSocket, thấy graph sau khi parse xong, sau đó tiếp tục với import GitHub công khai và các diagram Use Case/Class.

### Cập nhật foundation hiện tại (2026-06-01)

- `mvn test` pass. `mvn verify` từng fail vì coverage khoảng 66% < 70%, nhưng blocker đã được xử lý bằng Testcontainers Neo4j.
- `Neo4jGraphRepositoryIT` hiện chạy Neo4j thật bằng Testcontainers: 5 tests run, 0 skipped khi Docker daemon đang chạy.
- `mvn verify` xanh khi Docker daemon available và coverage gate 70% pass. CI/runner phải có Docker daemon; nếu Docker không có, Testcontainers IT có thể skip và coverage có thể tụt lại.
- Archive foundation và import end-to-end đã có: `ArchiveImportException`, `ArchiveType`, `ArchiveTypeDetector`, `ProjectService.createProjectFromWorkspace`, `GlobalExceptionHandler` mapping archive 400 và upload oversize 413, `ArchiveExtractor`, `ArchiveImportService` orchestration sync, và `POST /api/projects/import-archive` sync.
- `ArchiveExtractor` đã xong: hỗ trợ ZIP, TAR, TAR_GZ/TGZ; chỉ materialize `.java`; chống path traversal, absolute path, Windows drive path; reject TAR symlink/hardlink/special file; bỏ qua ignored paths; test `EMPTY_ARCHIVE` / `OVERSIZE` / `EXTRACTION_FAILED`; `mvn verify` xanh sau khi thêm extractor.
- Sync archive upload E2E đã pass: `POST /api/projects/import-archive` trả `200 OK`, project status `ANALYZED`, progress `100`, frontend navigate sang `/projects/{id}/graph`.
- Async archive import đã có: `POST /api/projects/import-archive?async=true` trả `202 Accepted`, project status `ANALYZING`, progress `0`, background analyze qua `analysisExecutor`, status model `ANALYZED`/`FAILED`, và WebSocket status events `/topic/projects/{id}/status`.
- Frontend async support đã có: `importApi.uploadArchiveAsync(...)`, `useWebSocket.ts`, async mode trong `useArchiveImport`, `AddProjectArchive` prop `async?: boolean`, polling fallback `GET /api/projects/{id}`, watchdog timeout, và Vite SockJS fix `define.global = globalThis`.
- Async browser E2E đã pass qua poll fallback: nhận `202`, SockJS handshake `/ws/graph-updates/info` trả `200`, poll fallback thấy `ANALYZED`, navigate graph thành công; `no-java.zip` async trả `400` trước pha WebSocket.
- Default UI vẫn sync: `HomeView` render `<AddProjectArchive @imported="onImported" />`. Async support có sẵn qua `AddProjectArchive async` prop nhưng chưa bật mặc định.
- Hardening sau E2E đã xong: public JSON không còn expose server absolute `rootPath` (`ProjectResponse` vẫn giữ field/getter cho Java nội bộ), frontend `Project` type/test fixtures đã bỏ `rootPath`, và dev CORS đã allow thêm `http://127.0.0.1:5173`.
- Parser D3 đã xong: `Signatures.method(ownerFqcn, methodName, paramTypes)`, `MethodVisitor` dùng `Signatures`, `SpringAnnotationVisitor` dùng `Signatures` cho HANDLES_ROUTE source. Format signature không đổi: `owner.method(param1,param2)`.
- Frontend Upload UI đã xong cho sync flow và có async support; Home route `/` vẫn dùng sync mặc định.

| # | Task | Tiêu chí chấp nhận |
|---|---|---|
| 2.1 | Cấu hình STOMP endpoint `/ws/graph-updates` | ✅ Done cho SockJS/STOMP handshake; async E2E xác nhận `/ws/graph-updates/info` trả `200` |
| 2.2 | Hiện thực các method broadcast cập nhật đồ thị | ✅ Done cho project status events `/topic/projects/{id}/status`; watch debt: chưa quan sát push event thắng poll fallback |
| 2.3 | Hiện thực pipeline tăng dần cho file watcher | CREATE/MODIFY/DELETE re-parse một file và cập nhật đồ thị < 3s |
| 2.4 | Hiện thực `POST /api/projects/import-archive` | ✅ Done sync và async. Sync trả `200 OK` + `ANALYZED`/progress `100`; async `?async=true` trả `202 Accepted` + `ANALYZING`/progress `0`, analyze nền qua `analysisExecutor` |
| 2.5 | Hiện thực UI Add Project bằng upload ZIP/TAR | ✅ Done sync default và async support. User chọn archive từ file explorer, bấm Add, graph mở sau analyze. Async có sẵn qua prop `AddProjectArchive async` nhưng HomeView chưa bật mặc định |
| 2.6 | Hiện thực pre-flight + parse stream cho import GitHub | Dùng `GET /repos/{owner}/{repo}`, từ chối repo private/quá lớn, parse tarball GitHub như cùng pipeline archive |
| 2.6a | Hiện thực `POST /api/projects/import-github` end-to-end | Trả về response project được chấp nhận và gửi cập nhật tiến độ |
| 2.7 | Hiện thực endpoint lân cận đồ thị | `GET /graph/neighbors/{nodeId}?hops=N` hoạt động với giới hạn |
| 2.8 | Hiện thực service/API/frontend cho diagram Use Case | Mermaid hợp lệ render trong UI |
| 2.9 | Hiện thực service/API/frontend cho diagram Class | Bộ lọc package hoạt động |
| 2.10 | Hiện thực focus mode và các bộ lọc | Toggle node/edge và focus theo độ sâu hoạt động trong Sigma |
| 2.11 | Hiện thực phát hiện injection qua constructor và Lombok | Các Spring bean dùng constructor tường minh hoặc `@RequiredArgsConstructor`/`@AllArgsConstructor` trên các field `final` phát ra `INJECTS` edges tới type phụ thuộc; có test bao phủ — ✅ Đã làm sớm ở Sprint 1 (xem mục 'Đã hoàn thành sớm'). |
| 2.12 | Gộp Spring layer detection vào `SpringAnnotationVisitor` | Một nguồn sự thật duy nhất cho việc phát hiện layer; thêm xử lý `CONFIG`, `ENTITY`, và `@ControllerAdvice`; loại bỏ logic layer trùng lặp khỏi `ClassVisitor`; `SpringAnnotationVisitorTest` được bật và pass — 🟡 Một phần (xem nợ D1). |
| 2.13 | Nối `JavaSymbolSolver` để có `CALLS` edges đã resolve | 🟡 Một phần — parser đã cấu hình Symbol Solver và emit CALLS cho resolved in-project calls. Chưa có stub-on-failure, chưa log tỉ lệ resolved, và chưa xử lý library/unresolved calls theo confidence thấp. |

### Sprint 1 Carry-over / Nợ kỹ thuật

Những mục này phát sinh khi hoàn thiện lát cắt dọc Sprint 1. Chúng được theo dõi ở đây để Sprint 2 bắt đầu với hiểu biết rõ ràng về những gì đã bị hoãn lại. Không chặn lát cắt — nhưng mỗi mục là một rủi ro thực sự nếu để lại.

| # | Nợ | Ở đâu | Vì sao quan trọng | Cách sửa đề xuất |
|---|---|---|---|---|
| D1 | `springLayer()` bị trùng lặp ở `ClassVisitor` và `SpringAnnotationVisitor` | `ClassVisitor.java:175` (giữ logic + mở rộng với CONFIG/ENTITY/ControllerAdvice), `SpringAnnotationVisitor.java` (đã có phần đọc annotation riêng) | Mâu thuẫn trực tiếp với tiêu chí chấp nhận của task 2.12 ("loại bỏ logic layer trùng lặp khỏi `ClassVisitor`"). Hai nguồn sự thật = lệch nhau khi thêm annotation mới. | Chuyển toàn bộ `springLayer()` vào `SpringAnnotationVisitor`; để `ClassVisitor` ủy quyền hoặc ngừng phát ra property này; đóng 2.12 đúng cách. |
| D2 | `resolveTypeName()` bị copy-paste ở 3 visitor | `ClassVisitor.java:83`, `MethodVisitor.java:144`, `SpringAnnotationVisitor.java:127` | Độ chính xác của symbol resolution là đòn bẩy lớn nhất cho chất lượng đồ thị (target của CALLS, INJECTS, EXTENDS). Ba bản hiện thực nghĩa là ba lỗi phải sửa khi nó thay đổi. Lần thử trước với tiện ích `TypeNames` đã bị xóa vì chưa bao giờ được nối vào — phần việc đó cần làm lại, nhưng lần này phải thực sự được áp dụng. | Tách ra một tiện ích dùng chung (`TypeNames.resolveFqn(typeName, node)`); refactor cả 3 visitor để gọi nó; thêm test ghim hành vi chuẩn. |
| D3 | Method signature được dựng inline ở nhiều nơi | `MethodVisitor.java`, `SpringAnnotationVisitor.java`, `Signatures.java` | ✅ Xong. Một builder chuẩn duy nhất `Signatures.method(ownerFqcn, methodName, paramTypes)` đã được dùng cho method nodes/CALLS target và HANDLES_ROUTE source; format không đổi: `owner.method(param1,param2)`. | Giữ test ghim format và dùng `Signatures.method(...)` cho mọi signature mới. |

### Việc tiếp theo trong Sprint 2

1. Decide whether to enable async UI by default in `HomeView`; hiện mặc định vẫn sync, async chỉ bật khi truyền `AddProjectArchive async`.
2. Persist project registry thay vì in-memory.
3. Cleanup graph/project state khi analyze failure; async failure hiện có thể để lại project `FAILED` và partial graph debt.
4. Test WebSocket push path với project lớn hơn hoặc delayed analyze để quan sát progress meaningful và push thắng poll fallback.
5. `getFullGraph` limit/pagination hoặc project size cap trước khi xử lý repo lớn.
6. GitHub import dùng lại archive pipeline sau khi archive local đã an toàn.

## Sprint 3 - MCP, độ bền vững, hoàn thiện

Cột mốc: các tool MCP trả về ngữ cảnh project hữu ích và ứng dụng xử lý các project cỡ trung một cách đáng tin cậy.

| # | Task | Tiêu chí chấp nhận |
|---|---|---|
| 3.1 | Hiện thực `get_project_architecture` | Trả về layers, số đếm, patterns, và tham chiếu diagram |
| 3.2 | Hiện thực `get_class_context` | Trả về class, lân cận, methods, fields, edge vào/ra |
| 3.3 | Hiện thực `get_layer_pattern` | Trả về ví dụ và quy ước cho một layer |
| 3.4 | Hiện thực `get_impact_analysis` | Dùng duyệt impact của repository với độ sâu tối đa |
| 3.5 | Thêm annotation OpenAPI cho các REST controller | Tài liệu Swagger bao phủ các endpoint công khai |
| 3.6 | Cải thiện độ bền vững của parser | Xử lý lambda, method ref, symbol chưa resolve được mà không crash; đạt hơn 85% `CALLS` được resolve trên các project Spring Boot phổ biến; các call chưa resolve được đánh dấu confidence thấp |
| 3.7 | Thêm caching theo content-hash | File không đổi được bỏ qua |
| 3.8 | Thêm stats đồ thị, trạng thái rỗng, và UI lỗi | UX xử lý rõ ràng các trạng thái loading và thất bại |
| 3.9 | Tối ưu hiệu năng cho 5k nodes | Việc render vẫn dùng được và các query được giới hạn |

## Sprint 4 - Triển khai và đệm

Cột mốc: demo công khai đã chạy, tài liệu chính xác, và sản phẩm có thể demo end-to-end.

| # | Task | Tiêu chí chấp nhận |
|---|---|---|
| 4.1 | Cấu hình Docker Compose production và nginx | Backend build từ thư mục gốc repo, frontend từ `vibegraph-web` |
| 4.2 | Thiết lập domain, DNS, SSL | `vibegraph.com` phục vụ app qua HTTPS |
| 4.3 | GitHub Actions CI | Test backend và lint/type-check frontend chạy trên PR |
| 4.4 | Workflow triển khai | Nhánh main có thể deploy lên VPS hoặc deploy thủ công có tài liệu chạy được |
| 4.5 | Project mẫu cho demo | Luồng import/analyze chạy được với một repo Java công khai đã biết |
| 4.6 | Hoàn thiện tài liệu | README, hướng dẫn cài đặt, tích hợp MCP, và tài liệu triển khai khớp với code |
| 4.7 | Backlog sửa lỗi | Các bug ưu tiên cao được đóng trước khi ra mắt |
| 4.8 | Ghi hình/thuyết trình demo | Bài thuyết trình phi kỹ thuật phản ánh đúng hiện trạng hiện thực |

## Giảm thiểu rủi ro

| Rủi ro | Giảm thiểu |
|---|---|
| Symbol resolution của JavaParser thất bại trên project phức tạp | Phát ra edge với confidence thấp hơn và tiếp tục |
| Query Neo4j phình to không giới hạn | Áp dụng giới hạn hop, phân trang, và `LIMIT` trong Cypher |
| Thread WebSocket bị chặn bởi việc parse | Chạy parse bất đồng bộ |
| Giới hạn rate của GitHub API | Dùng `GITHUB_TOKEN` khi được cấu hình và hiển thị lỗi rõ ràng |
| Tarball chứa symlink hoặc file đặc biệt | Bỏ qua các tar entry không phải file thường |
| Hiệu năng đồ thị frontend suy giảm | Dùng reducer, label có giới hạn, và layout chạy trên worker |

## Điểm khởi đầu hiện tại

Tính đến audit repo ngày 2026-05-30, Sprint 1 vertical slice đã chạy: đăng ký project local → analyze bằng `ParserServiceImpl.parseProject` → ghi Neo4j raw Driver → `GET /api/projects/{id}/graph` → render Sigma. Sprint 2 không bắt đầu từ parser service nữa; hãy bắt đầu theo thứ tự rủi ro sau:

1. Chặn rủi ro public demo: auth/rate-limit, SSRF/path validation, actuator exposure, CORS production.
2. Hoàn tất realtime nền tảng: `AsyncConfig` executor, analyze async, `GraphUpdateController`, `WebSocketEventListener`, frontend `useWebSocket`.
3. Làm upload ZIP/TAR project end-to-end: frontend Add Project, backend `import-archive`, archive safety checks, parse từ stream/temp workspace, progress WS, auto-open graph.
4. Dùng lại pipeline archive cho GitHub import: pre-flight GitHub API, tarball stream, parse từ stream hoặc bổ sung `ParserService.parseString`, progress WS, UI import.
5. Hoàn tất watcher/incremental update: Java WatchService, debounce, `deleteFile`/upsert theo file, patch graph frontend.
6. Hoàn tất graph API mở rộng: neighbors/impact route + repository implementations, pagination/limit cho `getFullGraph`.
7. Làm diagram Use Case/Class end-to-end và dựng các panel frontend scaffold thành logic thật.
8. Trả nợ parser/schema: `Package`/`File` nodes, `OWNS`/`CONTAINS`/`DEFINES`, `ANNOTATED_BY`, call unresolved/stub/confidence logging, single source cho type/signature/layer.
