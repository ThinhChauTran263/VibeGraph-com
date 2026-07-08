# VibeGraph - Yêu cầu (Phạm vi 2 tháng)

**Version:** 2.1<br>
**Timeline:** 8 weeks<br>
**Đối tượng:** Dự án Java, chế độ local đơn người dùng (single-tenant) kèm bản demo công khai đơn giản<br>
**Bố cục triển khai:** backend đơn module kèm `vibegraph-web`

## Yêu cầu chức năng

### FR-01: Phân tích mã nguồn Java - Tối quan trọng

Phân tích các file `.java` trong thư mục dự án bằng JavaParser 3.28.0 và Symbol Solver. Xuất ra các đối tượng trung lập với parser là `NodeData`, `EdgeData` và `ParseResult`, không phải các entity Neo4j.

Tiêu chí chấp nhận:

- Phân tích 500 file Java trong dưới 30 giây trên lớp VPS mục tiêu.
- Trích xuất các loại node: `Project`, `Package`, `File`, `Class`, `Interface`, `Enum`, `Method`, `Field`, `Annotation`, `Route`.
- Trích xuất các loại edge: `OWNS`, `CONTAINS`, `DEFINES`, `HAS_METHOD`, `HAS_FIELD`, `HAS_INNER`, `EXTENDS`, `IMPLEMENTS`, `OVERRIDES`, `IMPORTS`, `TYPE_OF`, `RETURNS`, `PARAMETER_TYPE`, `THROWS`, `CALLS`, `INJECTS`, `HANDLES_ROUTE`, `ANNOTATED_BY`.
- Phát hiện các annotation Spring: `@Controller`, `@RestController`, `@Service`, `@Repository`, `@Component`, `@RequestMapping`, `@Autowired`, `@Scheduled`, `@KafkaListener`.
- Bỏ qua các file có lỗi cú pháp, ghi lại cảnh báo và tiếp tục phân tích các file khác.
- Mục tiêu độ chính xác của call graph: hơn 85% trên các dự án Spring Boot phổ biến. Các lời gọi chưa giải quyết được có thể tạo ra các node method stub với độ tin cậy thấp hơn.

> **Trạng thái code sau audit 2026-05-30:** parser hiện đã phát ra `Class`, `Interface`, `Enum`, `Annotation`, `Method`, `Field`, `Route` và các edge chính `HAS_METHOD`, `HAS_FIELD`, `HAS_INNER`, `EXTENDS`, `IMPLEMENTS`, `TYPE_OF`, `RETURNS`, `PARAMETER_TYPE`, `THROWS`, `CALLS`, `INJECTS`, `HANDLES_ROUTE`. `Project` node được tạo ở tầng repository khi đăng ký project, nhưng parser chưa phát ra `Package`/`File` node và chưa phát ra `OWNS`/`CONTAINS`/`DEFINES`/`ANNOTATED_BY`/`OVERRIDES` như data contract mục tiêu. `CALLS` hiện chỉ ghi các lời gọi resolve được trong project; chưa có stub-on-failure và chưa có logging tỷ lệ resolved. Các phần thiếu này là backlog Sprint 2/3, không được tính là đã hoàn tất Sprint 1.

### FR-02: Lưu trữ Neo4j - Tối quan trọng

Lưu trữ dữ liệu graph trong Neo4j 5.x Community thông qua interface `GraphRepository` bằng cách dùng Neo4j Java Driver thuần (không dùng Spring Data Neo4j OGM hay các entity `@Node`). Các service không được phụ thuộc trực tiếp vào API của Neo4j. Chỉ các class nằm dưới `common/config` (ví dụ schema migration runner) và các class triển khai repository dưới `graph/repository/impl/neo4j` mới được phép import API của Neo4j driver.

Tiêu chí chấp nhận:

- Mọi node domain được lưu đều có `projectId`.
- `Neo4jGraphRepository` đã hiện thực `upsertProject`, `upsertNodes`, `upsertEdges`, `deleteFile`, `getFullGraph`, `searchNodes`, `getImpact` (3 profile: dependency/structural/type-data-flow). Method `getNeighborhood` đã bị loại bỏ khỏi interface (commit `ab00104`) — tính năng neighborhood được phục vụ gián tiếp qua lazy expand phía frontend (`useGraphExpand`).
- Cypher chỉ dùng tham số; không nối chuỗi đầu vào của người dùng.
- Cập nhật tăng dần (incremental) có thể thay thế dữ liệu graph cho một file đã thay đổi.
- Các ràng buộc và index của schema từ `src/main/resources/db/migration/V1__init_schema.cypher` được áp dụng bằng migration runner lúc khởi động hoặc bằng một lệnh thủ công có tài liệu hướng dẫn trước khi sử dụng.

### FR-03: Trực quan hóa Force Graph - Tối quan trọng

Hiển thị dữ liệu graph bằng Sigma.js và Graphology trong frontend Vue.

Tiêu chí chấp nhận:

- Dùng các loại node schema từ FR-01 làm hợp đồng dữ liệu (data contract).
- Các nhóm hiển thị (visual category) có thể suy ra nhãn hiển thị như `APIEndpoint` từ node `Route` hoặc `Constructor` từ `Method.properties.kind`, nhưng đây không phải là các loại node được lưu trữ.
- Cung cấp màu sắc ổn định cho tất cả các loại node và edge của schema.
- Panel filter hỗ trợ bật/tắt theo loại node và loại edge kèm số lượng đếm.
- Focus mode: nhấp vào một node để làm nổi bật các node lân cận và làm mờ các phần tử graph không liên quan.
- Độ sâu focus: All, 1, 2, 3 và 5 hop.
- Panel explorer có thể focus các node graph theo file hoặc package.
- Panel chi tiết node hiển thị các kết nối đến và đi.
- Panel legend và các điều khiển graph hiển thị trong khung nhìn graph.
- Layout ForceAtlas2 chạy mà không chặn luồng UI chính.

> **Trạng thái code (cập nhật 2026-06-21):** `GraphView`, `GraphCanvas`, `useSigma`, `useGraphData`, `stores/graph`, adapter và màu/type contract đã có và build/type-check/test unit pass. `FilterPanel`, `NodeDetailPanel`, `SearchBar`, `ImpactAnalysisPanel` đã hoàn thiện và wired vào `GraphCanvas`; focus/selection highlight + filter actions hoạt động. Một số panel phụ (`ExplorerPanel`, `FlowsPanel`, `LegendPanel`, `CodeInspector`, `GraphControls`) vẫn là scaffold cho sprint sau. FR-03 đạt graph render + search/filter/detail; các panel phụ còn lại là enhancement.

### FR-04: Use Case Diagram - Cao

Tạo flowchart Mermaid từ các route của controller, các scheduled job và các message listener.

> **Trạng thái:** ✅ Done — `UseCaseDiagramServiceImpl` + `DiagramController` `GET /api/projects/{id}/diagrams/usecase` sinh Mermaid `flowchart LR`; FE `DiagramPanel` render. (Job/listener actors chưa render vì parser chưa emit @Scheduled/@KafkaListener/@EventListener.)

Tiêu chí chấp nhận:

- Endpoint: `GET /api/projects/{id}/diagrams/usecase`.
- Đầu ra là cú pháp Mermaid hợp lệ.
- Xử lý `@RestController`, `@RequestMapping`, `@Scheduled` và `@KafkaListener` ở những nơi có dữ liệu đã phân tích.

### FR-05: Class Diagram - Cao

Tạo class diagram Mermaid từ dữ liệu class, interface, enum, field, method, kế thừa và injection đã phân tích.

> **Trạng thái:** ✅ Done — `ClassDiagramServiceImpl` + `DiagramController` `GET /api/projects/{id}/diagrams/class?package=...` sinh Mermaid `classDiagram`; FE `DiagramPanel` render với bộ lọc package.

Tiêu chí chấp nhận:

- Endpoint: `GET /api/projects/{id}/diagrams/class`.
- Hỗ trợ tùy chọn lọc theo package.
- Đầu ra bao gồm các field, method, và các quan hệ `EXTENDS`, `IMPLEMENTS`, `INJECTS` khi có sẵn.

### FR-06: Sequence Diagram — Đã hoãn sang post-MVP

Mã FR-06 được giữ chỗ có chủ đích. Tính năng Sequence Diagram đã được hoãn sang post-MVP (xem mục "Ngoài phạm vi MVP 2 tháng"). Mã FR-06 cố tình không được tái sử dụng để các tham chiếu FR giữ ổn định và nhất quán giữa các tài liệu.

### FR-07: Cập nhật Realtime - Tối quan trọng

Các file Java thay đổi sẽ cập nhật graph thông qua WebSocket/STOMP. Realtime đã hoàn tất cho cả CREATE/MODIFY/DELETE bằng incremental re-parse (re-parse đúng file thay đổi, tính delta, broadcast `INCREMENTAL`). Realtime thật chỉ áp dụng cho project import bằng local-folder (đọc tại chỗ); GitHub/archive theo dõi bản copy trên server (snapshot).

Tiêu chí chấp nhận:

- WebSocket endpoint: `/ws/graph-updates`.
- Topic: `/topic/projects/{projectId}/updates` và `/topic/projects/{projectId}/status`.
- Thời gian từ lúc lưu file đến lúc cập nhật graph: dưới 3 giây cho CREATE/MODIFY/DELETE (re-parse 1 file + debounce).
- Frontend subscribe `/topic/projects/{projectId}/updates` và xử lý `FULL_UPDATE`/`INCREMENTAL` payload mà không reload trang. Producer broadcast `INCREMENTAL` (added/removed) cho mọi thay đổi file; FE patch graph tại chỗ trên Sigma (không reset camera/zoom).

> **Trạng thái code sau audit mới nhất:** backend đã cấu hình STOMP endpoint `/ws/graph-updates`; status topic `/topic/projects/{projectId}/status` dùng cho import/analyze progress; graph update topic `/topic/projects/{projectId}/updates` có `broadcastFullUpdate`/`broadcastIncremental`; frontend `useGraphRealtime.ts` subscribe + patch state, `GraphCanvas.vue` patch Sigma tại chỗ. File watcher lifecycle đã wired (start sau import/analyze, stop khi delete project, re-watch khi khởi động). CREATE/MODIFY/DELETE đều incremental qua `FileChangeBroadcaster` (re-parse file đổi bằng `parserService.parseFile` → upsert/prune → broadcast delta). Đã test tay end-to-end: thêm/xóa file → graph cập nhật tại chỗ.

### FR-08: File Watcher phía Server - Tối quan trọng

Theo dõi các thư mục dự án local đã cấu hình bằng Java WatchService.

Tiêu chí chấp nhận:

- Theo dõi đệ quy các file `.java`.
- Gộp (debounce) các lần lưu liên tiếp với mặc định 500 ms.
- Bỏ qua `target`, `build`, `.git`, `.idea` và `node_modules`.
- Các sự kiện create, modify và delete kích hoạt phân tích tăng dần.
- Watcher chỉ theo dõi các thư mục dự án đã cấu hình; các đường dẫn được kiểm tra đối chiếu với root đã đăng ký.

> **Trạng thái code sau audit mới nhất:** `WatcherProperties`, `FileWatcherServiceImpl` và `DebouncedEventHandler` đã implemented cho recursive watch/debounce/lifecycle. Watcher giờ là bộ phát sự kiện thuần (detect + debounce + emit cho handler); `FileChangeBroadcaster` xử lý mọi loại thay đổi: CREATE/MODIFY re-parse file đổi → upsert; DELETE prune; rồi broadcast delta `INCREMENTAL`. Cả create, modify và delete đều kích hoạt cập nhật tăng dần.

### FR-09: REST API - Tối quan trọng

Các endpoint MVP (cột *Trạng thái* phản ánh code thực tế, không chỉ kế hoạch):

| Method | Path                                                 | Mô tả                                                                              | Trạng thái                                                                                         |
| ------ | ---------------------------------------------------- | ---------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| POST   | `/api/projects`                                      | Đăng ký một đường dẫn dự án local                                                  | ✅ implemented — dev/internal fallback, không còn là UX chính                                       |
| GET    | `/api/projects`                                      | Liệt kê dự án                                                                      | ✅ implemented                                                                                      |
| GET    | `/api/projects/{id}`                                 | Chi tiết một dự án                                                                 | ✅ implemented                                                                                      |
| DELETE | `/api/projects/{id}`                                 | Xóa một dự án                                                                      | ✅ implemented                                                                                      |
| POST   | `/api/projects/{id}/analyze`                         | Kích hoạt phân tích đầy đủ                                                         | ✅ implemented                                                                                      |
| GET    | `/api/projects/{id}/graph`                           | Trả về toàn bộ graph                                                               | ✅ implemented                                                                                      |
| POST   | `/api/projects/import-archive`                       | Upload file `.zip`/`.tar`/`.tar.gz` của project Java để backend parse và lưu graph | ✅ implemented — sync `200 OK`, async `202 Accepted` qua `?async=true`, status qua `/topic/projects/{id}/status` |
| GET    | `/api/projects/{id}/graph/neighbors/{nodeId}?hops=N` | Trả về neighborhood N-hop                                                          | ❌ removed — method `getNeighborhood` đã bị loại bỏ khỏi interface (commit `ab00104`); FE dùng lazy expand thay thế |
| GET    | `/api/projects/{id}/diagrams/usecase`                | Trả về Use Case Mermaid                                                            | ✅ implemented — `DiagramController` + `UseCaseDiagramServiceImpl`; FE render                       |
| GET    | `/api/projects/{id}/diagrams/class`                  | Trả về Class Mermaid                                                               | ✅ implemented — `DiagramController` + `ClassDiagramServiceImpl` (lọc package); FE render            |
| GET    | `/api/projects/{id}/graph/impact?nodeId=...&depth=...`| Trả về phạm vi ảnh hưởng (blast radius)                                            | ✅ implemented — `GraphController` `/graph/impact` → `GraphServiceImpl.getImpactAnalysis` (`Neo4jGraphRepository.getImpact`) với 3 profile: `dependency`/`structural`/`type-data-flow` |
| POST   | `/api/projects/import-github`                        | Import một repo GitHub công khai qua luồng tarball                                 | ✅ implemented — parse URL, pre-flight, download tarball, extract qua archive pipeline, analyze async; FE `GitHubImportForm` đã có |
| WS     | `/ws/graph-updates`                                  | Đẩy graph/status theo thời gian thực                                               | ✅ implemented — STOMP endpoint + broadcast `FULL_UPDATE`/`INCREMENTAL`; FE consumer patch graph tại chỗ |

> Lưu ý: lát cắt dọc Sprint 1 (đăng ký dự án local path → analyze → full graph) đã chạy thật. Từ quyết định product ngày 2026-05-31, UX chính của Sprint 2 chuyển sang **upload ZIP/TAR archive**; local-path registration giữ lại như dev/internal fallback. Từ Sprint 3, thêm **local folder import** (`POST /api/projects/import-local`) với realtime file-watching tại chỗ. ImpactController scaffold đã bị loại bỏ (impact gộp vào GraphController). `getNeighborhood` đã bị loại bỏ khỏi interface.

Tiêu chí chấp nhận:

- Các request DTO dùng validation annotation.
- Phản hồi dùng `ApiResponse<T>`.
- Lỗi được ánh xạ bởi `GlobalExceptionHandler`.
- Không trả về entity Neo4j thô từ controller.

### FR-10: MCP Server - Cao

Cung cấp **15 MCP tools** thông qua Spring AI MCP Streamable HTTP tại `/mcp` (đã ship — tăng từ 4 tool kế hoạch ban đầu lên 15 trong Sprint 3).

Tools đã ship (theo `src/main/java/com/vibegraph/mcp/MODULE-GUIDE.md`):

1. `get_project_architecture` — layers, counts, patterns, warnings
2. `get_class_context` — matched class, methods, fields, incoming/outgoing relations
3. `get_impact_analysis` — blast radius theo 3 profile (dependency/structural/type-data-flow)
4. `get_layer_pattern` — layer examples, conventions, dependency patterns
5. `trace_endpoint` — route handler downstream flow (STEP_IN_FLOW + CALLS fallback)
6. `find_references` — graph references to a symbol
7. `get_source_file` — bounded, redacted project-relative source file
8. `search_source` — source search mapped to graph symbols
9. `get_method_source` — method source theo id/query/signature
10. `get_method_cpg_context` — method signature, calls, flow steps, deep CPG groups
11. `find_related_tests` — related tests từ graph links + heuristics
12. `suggest_test_plan` — focused test plan cho change description
13. `plan_code_change` — conservative change plan với risks + tests
14. `explain_failure_path` — stacktrace → project mapping
15. `get_project_conventions` — durable conventions từ `ai-memory.md`

Tool hoãn lại: `get_usecase_context`, `get_coding_rules`.

> **Trạng thái code (verified 2026-07-02):** 15 tools đã register qua `common/config/McpServerConfig`. Chi tiết integration + workflow tại `docs/mcp-integration.md`.

### FR-NEW: GitHub Import - Cao

Cho phép người dùng dán URL của một repository GitHub công khai. Backend tải xuống tarball, phân tích các file Java trong bộ nhớ và lưu trữ dữ liệu graph.

> **Trạng thái code sau audit 2026-06-08:** backend GitHub import đã implemented: `ImportController`, `GitHubUrlParser`, `GitHubPreFlightService`, `GitHubTarballClient` và `TarballImportServiceImpl` đã có. Frontend import UI đã ship: `GitHubImportForm.vue` (351 LOC) + `useGitHubImport.ts` (225 LOC) + `importApi.importGithub`, kèm tests (`GitHubImportForm.spec.ts`, `useGitHubImport.spec.ts`, `importApi.spec.ts`). E2E smoke tested (import-github 202 → ANALYZED).

Tiêu chí chấp nhận:

- Chỉ hỗ trợ repository công khai cho MVP.
- Pre-flight dùng `GET https://api.github.com/repos/{owner}/{repo}` để kiểm tra `private`, `size` và `default_branch`.
- Từ chối các repository lớn hơn 100 MB.
- Việc tải tarball dùng GitHub API và `commons-compress`.
- Triển khai hiện tại download tarball vào workspace server, extract/materialize các file `.java`, rồi analyze async; mục tiêu stream-only không lưu source xuống đĩa là tối ưu hóa tương lai nếu cần.
- Dùng `GITHUB_TOKEN` khi có sẵn để tránh giới hạn rate thấp khi không xác thực.
- Mục tiêu timeout: 60 giây cho pre-flight cộng với việc import các repo nhỏ/vừa.
- Endpoint: `POST /api/projects/import-github` với body `{"url":"https://github.com/owner/repo"}`.

### FR-NEW-2: Project Archive Upload - Tối quan trọng

Cho phép người dùng chọn một file `.zip`, `.tar`, hoặc `.tar.gz` chứa project Java từ máy của họ, upload lên backend, backend đọc archive, parse các file `.java`, lưu graph vào Neo4j, rồi frontend mở graph sau khi xử lý xong.

> **Quyết định product 2026-05-31:** Archive upload thay thế `POST /api/projects` local-path registration làm flow chính cho người dùng. Local-path registration vẫn được giữ cho dev/self-host/internal fallback vì code Sprint 1 đã có và hữu ích khi debug.

Tiêu chí chấp nhận:

- UI `Add Project` có lựa chọn upload archive.
- Chỉ nhận `.zip`, `.tar`, `.tar.gz` trong MVP.
- Giới hạn kích thước archive mặc định 100 MB.
- Backend chống path traversal khi extract/stream entry (`../`, absolute path, symlink nguy hiểm).
- Bỏ qua `target`, `build`, `.git`, `.idea`, `node_modules` và file không phải `.java`.
- Không cần user nhập `rootPath` thủ công.
- Giữ relative path trong archive để gán `filePath` cho nodes.
- Endpoint mục tiêu: `POST /api/projects/import-archive` với `multipart/form-data` gồm `name` và `file`.
- Response trả về `projectId`, trạng thái import/analyze, và frontend redirect sang graph khi xong.

> **Trạng thái code sau audit 2026-06-08:** backend `POST /api/projects/import-archive` đã có sync/async; frontend `AddProjectArchive.vue` và `useArchiveImport.ts` đã có. Mặc định `HomeView` dùng sync flow; async flow được hỗ trợ qua composable/prop và status topic.

### FR-NEW-3: Source Viewer — Cao (đã ship Sprint 3)

Cho phép người dùng và MCP client đọc source file redacted của một project đã import, hạn chế trong phạm vi source root đã đăng ký.

Tiêu chí chấp nhận:

- REST endpoint `SourceController` trả về snippet bounded (giới hạn dòng, redact sensitive property, project-relative path — không leak absolute path).
- MCP tools `get_source_file`, `get_method_source`, `search_source` dùng chung `mcp/source/*` helpers (`GraphView`, `SourceFileService`, `SourceGraphSupport`).
- FE `CodeViewerModal.vue` mở source từ Node Detail Panel.
- Reject path traversal, `.env`/keys/binaries; enforce allowed workspace/root guard.

> **Trạng thái code (verified 2026-07-02):** `SourceController` + 3 MCP source tool + `useSourceCode.ts` + `CodeViewerModal.vue` + `FilePath.vue` đã ship. Bảo mật path-traversal + redaction đã ở guard chung.

### FR-NEW-4: AI-Refined Use Case — Cao (đã ship Sprint 3)

Sử dụng Gemini LLM để refine sơ đồ Use Case sinh từ heuristic parser (Tier 2 refinement), có failover cấu hình và cache. Tier 1 (deterministic confidence calculus + eval harness) đã ship trước; Tier 2 là opt-in qua config.

Tiêu chí chấp nhận:

- Package `com.vibegraph.ai/*`: `GeminiChatClientConfig`, `GeminiFailoverChatClient`, `GeminiRotationProperties`, `ResilientChatClient`, cache `caffeine/`.
- Refiner interface: `LlmUseCaseRefiner` + `NoopUseCaseRefiner` fallback.
- Constraints: temp=0, schema-validated output, cache, validate ngược, fallback về Tier 1 khi LLM lỗi/không cấu hình.
- Không bao giờ tệ hơn engine tất định hiện tại (đảm bảo qua eval harness).

> **Trạng thái code (verified 2026-07-02):** 4 file trong `com/vibegraph/ai/` đã có; `LlmUseCaseRefiner` hoạt động qua UseCase pipeline v2 (9 helper class trong diagram module).

### FR-NEW-5: Deep CPG opt-in — Trung bình (đã ship Sprint 3)

Cho phép mở rộng graph với node `LocalVariable` và edge `READS`/`WRITES`/`CATCHES` để hỗ trợ data-flow analysis chi tiết, opt-in qua biến môi trường.

Tiêu chí chấp nhận:

- Env var `VIBEGRAPH_PARSER_DEEP_CPG=true` bật deep CPG; default `false`.
- Property Spring `vibegraph.parser.deep-cpg-enabled` (mapping trong `application.yaml`).
- `MethodVisitor(deepCpg)` gate emit `LocalVariable` nodes + `READS`/`WRITES`/`CATCHES` edges.
- Khi tắt: KHÔNG emit các node/edge trên; MCP tool `get_method_cpg_context` report empty data-flow groups với limitation note rõ ràng.

> **Trạng thái code (verified 2026-07-02):** `application.yaml:151` map env vào property; `ParserServiceImpl.java:50-51` @Value inject; `MethodVisitor.java:55-86` gate emission. Test bao phủ: `CpgSchemaCoverageTest`, `MethodVisitorTest.DeepCpg`.

### FR-NEW-6: Impact Analysis Profiles — Cao (đã ship Sprint 3)

Mở rộng blast-radius analysis với 3 profile để phù hợp use case khác nhau.

Tiêu chí chấp nhận:

- 3 profile: `dependency` (default, reverse CALLS|IMPORTS|EXTENDS|IMPLEMENTS|INJECTS), `structural` (containment/definition/route), `type-data-flow` (type edges + deep CPG).
- Endpoint `GET /api/projects/{id}/graph/impact?profile=...&nodeId=...&depth=...`.
- Depth groups: d=1 willBreak, d=2 likelyAffected, d>=3 mayNeedTesting.
- MCP tool `get_impact_analysis` cũng hỗ trợ profile param.
- FE `ImpactAnalysisPanel.vue` cho phép switch profile.

> **Trạng thái code (verified 2026-07-02):** `GraphController /graph/impact` + `GraphServiceImpl.getImpactAnalysis` + `Neo4jGraphRepository.getImpact` + `ImpactAnalysisTool` + FE `ImpactAnalysisPanel.vue` đã ship với 3 profile.

### FR-NEW-7: Local Folder Import với Realtime Watcher — Cao (đã ship Sprint 3)

Cho phép user (chế độ local/self-host) đăng ký một thư mục Java trên máy chủ backend và xem graph cập nhật realtime khi sửa file `.java` trong IDE.

Tiêu chí chấp nhận:

- Endpoint `POST /api/projects/import-local` với `LocalProjectController` + `LocalImportService`.
- Endpoint `GET /api/projects/browse` liệt kê filesystem root (chỉ dùng khi run cùng máy dev — xem `security-multiuser-roadmap.md` V3).
- FE `AddProjectLocal.vue` + `DirectoryBrowserModal.vue` + `useLocalImport.ts` + `ImportProjectPanel.vue`.
- File watcher `FileWatcherServiceImpl` + `FileChangeBroadcaster` re-parse file đổi → upsert/prune → broadcast `INCREMENTAL`.
- FE `useGraphRealtime.ts` patch Sigma tại chỗ (không reset camera/zoom/layout).

> **Trạng thái code (verified 2026-07-02):** Toàn bộ chain đã ship. Không dùng cho multi-user deploy đến khi có auth + sandbox theo user (`security-multiuser-roadmap.md`).

## Yêu cầu phi chức năng

| Chỉ số               | Mục tiêu                 |
| -------------------- | ------------------------ |
| Phân tích 500 file   | < 30s                    |
| Cập nhật tăng dần    | < 3s                     |
| Truy vấn 3-hop Neo4j | < 500 ms                 |
| Độ trễ WebSocket     | < 200 ms trên mạng local |
| Triển khai local     | `docker compose up -d`   |
| VPS tối thiểu        | 4 GB RAM, 2 CPU          |

## Ngoài phạm vi MVP 2 tháng

- Tách riêng các module `vibegraph-core`, `vibegraph-server`, `vibegraph-cli` hoặc `vibegraph-cli-npm`.
- Package wrapper Npm CLI.
- GitHub OAuth và import repository riêng tư.
- Xác thực, thanh toán, Stripe, gói dịch vụ, tài khoản người dùng.
- Phân tích đa ngôn ngữ.
- Sequence diagram.
- Tạo file steering và template pre-code hook.
- Native-image CLI.
- Chế độ Kuzu embedded.
- Chế độ SaaS Postgres+AGE.
- Plugin IntelliJ.
