# VibeGraph - Phân rã công việc (8 tuần)

Kế hoạch này được sắp xếp theo thứ tự phụ thuộc, không phải theo ranh giới module tưởng tượng. Codebase hiện tại là một backend Spring Boot đơn lẻ cộng với `vibegraph-web`.

## Kế hoạch Sprint

| Sprint | Tuần | Mục tiêu |
|---|---|---|
| 1 | 1-2 | Lát cắt dọc cục bộ: parse folder -> Neo4j -> REST -> đồ thị Sigma |
| 2 | 3-4 | Realtime, import GitHub, và diagram |
| 3 | 5-6 | MCP, độ bền vững, hiệu năng, hoàn thiện UI |
| 4 | 7-8 | Triển khai, tài liệu, sửa lỗi, hoàn thiện demo |

## Sprint 1 - Lát cắt dọc nền tảng

Cột mốc: người dùng có thể đăng ký một project Java cục bộ, phân tích nó, gọi `GET /api/projects/{id}/graph`, và thấy một đồ thị trên trình duyệt.

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
| 1.9 | Hiện thực structural edges | Phát ra `CONTAINS`, `DEFINES`, `HAS_METHOD`, `HAS_FIELD`, `EXTENDS`, `IMPLEMENTS` ở những nơi áp dụng được |
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
| 1.23 | Cấu hình CORS cho môi trường dev | `http://localhost:5173` và origin của frontend Docker được cho phép ở dev |

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
| 2.13 | CALLS edges đã resolve qua `JavaSymbolSolver` | ✅ ĐÃ XONG sớm trong Sprint 1 — 0→54 CALLS edges qua project-wide type solver. Chưa có stub-on-failure và chưa log tỉ lệ resolved. |
| 2.12 | Gộp Spring layer detection vào `SpringAnnotationVisitor` | 🟡 MỘT PHẦN — đã thêm xử lý `CONFIG`/`ENTITY`/`@ControllerAdvice` nhưng đặt trong `ClassVisitor.springLayer()` thay vì `SpringAnnotationVisitor`; việc gộp CHƯA hoàn tất (xem nợ D1). |

## Sprint 2 - Tính năng cốt lõi

Cột mốc: người dùng có thể import một repo GitHub công khai, xem tiến độ qua WebSocket, và xem các diagram Use Case/Class.

| # | Task | Tiêu chí chấp nhận |
|---|---|---|
| 2.1 | Cấu hình STOMP endpoint `/ws/graph-updates` | Frontend có thể kết nối và subscribe các topic của project |
| 2.2 | Hiện thực các method broadcast cập nhật đồ thị | Các topic cập nhật toàn phần và tăng dần publish payload có kiểu |
| 2.3 | Hiện thực pipeline tăng dần cho file watcher | CREATE/MODIFY/DELETE re-parse một file và cập nhật đồ thị < 3s |
| 2.4 | Hiện thực pre-flight cho import GitHub | Dùng `GET /repos/{owner}/{repo}`, từ chối repo private/quá lớn |
| 2.5 | Hiện thực parse stream tarball | Parse các file `.java` từ tar stream mà không ghi mã nguồn xuống đĩa |
| 2.6 | Hiện thực `POST /api/projects/import-github` end-to-end | Trả về response project được chấp nhận và gửi cập nhật tiến độ |
| 2.7 | Hiện thực endpoint lân cận đồ thị | `GET /graph/neighbors/{nodeId}?hops=N` hoạt động với giới hạn |
| 2.8 | Hiện thực service/API/frontend cho diagram Use Case | Mermaid hợp lệ render trong UI |
| 2.9 | Hiện thực service/API/frontend cho diagram Class | Bộ lọc package hoạt động |
| 2.10 | Hiện thực focus mode và các bộ lọc | Toggle node/edge và focus theo độ sâu hoạt động trong Sigma |
| 2.11 | Hiện thực phát hiện injection qua constructor và Lombok | Các Spring bean dùng constructor tường minh hoặc `@RequiredArgsConstructor`/`@AllArgsConstructor` trên các field `final` phát ra `INJECTS` edges tới type phụ thuộc; có test bao phủ — ✅ Đã làm sớm ở Sprint 1 (xem mục 'Đã hoàn thành sớm'). |
| 2.12 | Gộp Spring layer detection vào `SpringAnnotationVisitor` | Một nguồn sự thật duy nhất cho việc phát hiện layer; thêm xử lý `CONFIG`, `ENTITY`, và `@ControllerAdvice`; loại bỏ logic layer trùng lặp khỏi `ClassVisitor`; `SpringAnnotationVisitorTest` được bật và pass — 🟡 Một phần (xem nợ D1). |
| 2.13 | Nối `JavaSymbolSolver` để có `CALLS` edges đã resolve | Parser cấu hình Symbol Solver; các call đã resolve tạo ra target chuẩn `owner.method(paramTypes)` khớp với các `Method` node thực; stub node chỉ được tạo khi resolve thất bại; tỉ lệ phần trăm resolve được log — ✅ Đã làm sớm ở Sprint 1 (xem mục 'Đã hoàn thành sớm'). |

### Sprint 1 Carry-over / Nợ kỹ thuật

Những mục này phát sinh khi hoàn thiện lát cắt dọc Sprint 1. Chúng được theo dõi ở đây để Sprint 2 bắt đầu với hiểu biết rõ ràng về những gì đã bị hoãn lại. Không chặn lát cắt — nhưng mỗi mục là một rủi ro thực sự nếu để lại.

| # | Nợ | Ở đâu | Vì sao quan trọng | Cách sửa đề xuất |
|---|---|---|---|---|
| D1 | `springLayer()` bị trùng lặp ở `ClassVisitor` và `SpringAnnotationVisitor` | `ClassVisitor.java:175` (giữ logic + mở rộng với CONFIG/ENTITY/ControllerAdvice), `SpringAnnotationVisitor.java` (đã có phần đọc annotation riêng) | Mâu thuẫn trực tiếp với tiêu chí chấp nhận của task 2.12 ("loại bỏ logic layer trùng lặp khỏi `ClassVisitor`"). Hai nguồn sự thật = lệch nhau khi thêm annotation mới. | Chuyển toàn bộ `springLayer()` vào `SpringAnnotationVisitor`; để `ClassVisitor` ủy quyền hoặc ngừng phát ra property này; đóng 2.12 đúng cách. |
| D2 | `resolveTypeName()` bị copy-paste ở 3 visitor | `ClassVisitor.java:83`, `MethodVisitor.java:144`, `SpringAnnotationVisitor.java:127` | Độ chính xác của symbol resolution là đòn bẩy lớn nhất cho chất lượng đồ thị (target của CALLS, INJECTS, EXTENDS). Ba bản hiện thực nghĩa là ba lỗi phải sửa khi nó thay đổi. Lần thử trước với tiện ích `TypeNames` đã bị xóa vì chưa bao giờ được nối vào — phần việc đó cần làm lại, nhưng lần này phải thực sự được áp dụng. | Tách ra một tiện ích dùng chung (`TypeNames.resolveFqn(typeName, node)`); refactor cả 3 visitor để gọi nó; thêm test ghim hành vi chuẩn. |
| D3 | Method signature được dựng inline ở nhiều nơi | `MethodVisitor.java:248` (`fullName()`), `MethodVisitor.java:122-126` (target của CALLS), `SpringAnnotationVisitor.java:57-61` (source của HANDLES_ROUTE) | Việc upsert edge làm `MATCH (a {fullName}) MATCH (b {fullName}) MERGE`. Lệch một ký tự giữa bên ghi và bên đọc = edge bị âm thầm bỏ qua, không lỗi, không log. Đây là loại bug tệ nhất để debug — liên quan trực tiếp đến tiêu chuẩn chất lượng của 2.13. | Một builder chuẩn duy nhất: `Signatures.method(ownerFqcn, name, paramTypes)`. Mọi nơi gọi đều dùng nó. Thêm unit test ghim cố định format string. |

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

Tính đến trạng thái repo hiện tại, hãy bắt đầu với việc tích hợp parser service sau khi các test visitor đã xanh. Phụ thuộc tiếp theo là `ParserServiceImpl.parseFile()` tạo ra `ParseResult` với `NodeData` và `EdgeData`.
