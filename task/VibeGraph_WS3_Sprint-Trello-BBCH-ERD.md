# VibeGraph WS3 Sprint Trello BBCH ERD - CODEX

## Product Backlog

| ID | As a/an<br>[User role] | I want to <br>[Goal]   | So that<br>[reason]  | Priority | Business Value | Acceptance Criteria<br>(tiêu chí chấp nhận) | State | Note |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PB01 | Lập trình viên | Upload file ZIP/TAR/TAR.GZ của project Java | hệ thống phân tích mà không cần khai báo local path | 1 | High | Thành công: nhận file .zip/.tar/.tar.gz ≤100MB, parse .java và mở graph; Thất bại: sai định dạng hoặc >100MB thì báo lỗi rõ ràng, không lưu graph | New |  |
| PB02 | Lập trình viên | Import repository GitHub công khai bằng URL | phân tích nhanh repo mà không cần tải thủ công | 2 | Medium | Thành công: dán URL public repo, pre-flight hợp lệ, parse tarball và mở graph; Thất bại: repo private/quá lớn/URL sai thì từ chối kèm thông báo | New |  |
| PB03 | Lập trình viên | Phân tích mã nguồn Java bằng JavaParser | trích xuất class/method/field và quan hệ code | 1 | High | Thành công: parse 500 file <30s, sinh node Class/Interface/Enum/Method/Field/Route; Thất bại: file lỗi cú pháp bị bỏ qua, ghi cảnh báo, không dừng cả tiến trình | New |  |
| PB04 | Lập trình viên | Lưu knowledge graph vào Neo4j | truy vấn và hiển thị quan hệ code bền vững | 1 | High | Thành công: node/edge lưu qua Neo4j Java Driver raw, có projectId, Cypher tham số hóa; Thất bại: lỗi kết nối Neo4j được báo và rollback, không lưu nửa vời | New |  |
| PB05 | Kiến trúc sư phần mềm | Trực quan hóa knowledge graph bằng Sigma.js | nhìn được kiến trúc tổng thể của dự án | 1 | High | Thành công: render node/edge với màu theo loại, layout không chặn UI; Thất bại: graph rỗng thì hiển thị trạng thái empty thay vì màn hình trắng | New |  |
| PB06 | Kiến trúc sư phần mềm | Tìm kiếm, lọc và focus node trên graph | khoanh vùng nhanh phần code cần xem | 2 | High | Thành công: search theo tên, lọc theo loại node/edge, focus N-hop (1/2/3/5/All); Thất bại: từ khóa không khớp hiển thị 'không có kết quả', không treo UI | New |  |
| PB07 | Kiến trúc sư phần mềm | Xem chi tiết một node | hiểu kết nối đến/đi của thành phần code | 2 | Medium | Thành công: click node mở panel hiển thị thuộc tính + cạnh in/out; Thất bại: node không tồn tại thì panel báo rõ, không lỗi JS | New |  |
| PB08 | Lập trình viên | Phân tích phạm vi ảnh hưởng (impact) của node | đánh giá rủi ro trước khi thay đổi code | 2 | Medium | Thành công: trả về blast radius theo độ sâu, giới hạn hop; Thất bại: node cô lập trả về tập rỗng kèm thông báo, query luôn có LIMIT | New |  |
| PB09 | Lập trình viên | Nhận cập nhật graph realtime qua WebSocket | thấy thay đổi code ngay không cần tải lại | 2 | Medium | Thành công: file .java thay đổi đẩy update qua /topic/projects/{id}/updates <3s, FE patch graph; Thất bại: mất kết nối WS thì tự thử lại và báo trạng thái | New |  |
| PB10 | Kiến trúc sư phần mềm | Sinh Use Case và Class Diagram bằng Mermaid | tài liệu hóa kiến trúc tự động | 2 | Medium | Thành công: GET /diagrams/usecase và /diagrams/class trả Mermaid hợp lệ render được; Thất bại: project chưa phân tích trả lỗi rõ, không sinh Mermaid rỗng sai cú pháp | New |  |
| PB11 | AI coding tool | Gọi MCP tools để lấy ngữ cảnh dự án | tích hợp AI vào quy trình hiểu code | 2 | Medium | Thành công: 4 tool MCP (architecture/class_context/impact/layer_pattern) trả JSON đúng schema qua streamable HTTP; Thất bại: projectId sai trả lỗi chuẩn MCP, không treo phiên | New |  |
| PB12 | DevOps | Đóng gói và triển khai bằng Docker + CI/CD | chạy hệ thống bằng một lệnh và kiểm thử tự động | 3 | Medium | Thành công: docker compose up -d chạy BE/FE/Neo4j, GitHub Actions xanh trên PR; Thất bại: build/test fail thì chặn merge và log rõ nguyên nhân | New |  |
| PB13 | Lập trình viên | Theo dõi thay đổi file bằng File Watcher | graph luôn đồng bộ với mã nguồn local | 3 | Low | Thành công: create/modify/delete .java kích hoạt phân tích tăng dần, bỏ qua target/.git/node_modules; Thất bại: đường dẫn ngoài root đăng ký bị từ chối | New |  |
| PB14 | Người dùng demo | Có tài liệu và bản demo cuối kỳ | hiểu và trình diễn được sản phẩm | 3 | Low | Thành công: README + tài liệu MCP/triển khai khớp code, demo end-to-end chạy với repo Java mẫu; Thất bại: thiếu bước nào trong demo phải được ghi nhận và sửa | New |  |

## Release Backlog

| Backlog ID | Backlog | As a/an<br>[User role] | I want to<br>[Goal] | So that<br>[reason]  | Story ID | Priority | Business Value | Sprint | State | Note |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PB01 | Import Archive | Lập trình viên | Upload archive ZIP/TAR/TAR.GZ project Java | phân tích không cần local path | RB01 | 1 | High | 1 | New |  |
| PB01 | Import Archive | Lập trình viên | Hệ thống kiểm tra loại và dung lượng archive (≤100MB) | tránh file sai định dạng hoặc quá lớn | RB02 | 1 | High | 1 | New |  |
| PB01 | Import Archive | Lập trình viên | Giải nén an toàn chống path traversal | bảo mật khi xử lý archive không tin cậy | RB03 | 1 | High | 1 | New |  |
| PB02 | GitHub Import | Lập trình viên | Dán URL public GitHub repo để import | phân tích repo từ xa nhanh chóng | RB04 | 2 | Medium | 2 | New |  |
| PB02 | GitHub Import | Lập trình viên | Pre-flight kiểm tra repo (private/size/branch) | từ chối sớm repo không hợp lệ | RB05 | 2 | Medium | 2 | New |  |
| PB03 | JavaParser Analysis | Lập trình viên | Parse file .java thành Class/Method/Field/Route | có dữ liệu cấu trúc code | RB06 | 1 | High | 1 | New |  |
| PB03 | JavaParser Analysis | Lập trình viên | Sinh structural edges (HAS_METHOD, EXTENDS, IMPLEMENTS...) | hiểu quan hệ cấu trúc giữa các thành phần | RB07 | 1 | High | 1 | New |  |
| PB03 | JavaParser Analysis | Lập trình viên | Phát hiện Spring annotation (@Service, @RestController...) | nhận diện layer và route | RB08 | 2 | Medium | 1 | New |  |
| PB03 | JavaParser Analysis | Lập trình viên | Resolve CALLS edge bằng JavaSymbolSolver | dựng call graph chính xác | RB09 | 2 | Medium | 2 | New |  |
| PB04 | Neo4j Storage | Lập trình viên | Lưu node/edge vào Neo4j qua Java Driver raw | persistence không phụ thuộc OGM | RB10 | 1 | High | 1 | New |  |
| PB04 | Neo4j Storage | Lập trình viên | Áp dụng Cypher schema migration (ràng buộc, index) | đảm bảo toàn vẹn dữ liệu graph | RB11 | 1 | High | 1 | New |  |
| PB04 | Neo4j Storage | Lập trình viên | Lấy full graph theo projectId | cung cấp dữ liệu cho UI | RB12 | 1 | High | 1 | New |  |
| PB05 | Graph Visualization | Kiến trúc sư phần mềm | Render graph bằng Sigma.js + Graphology | nhìn được kiến trúc dự án | RB13 | 1 | High | 1 | New |  |
| PB05 | Graph Visualization | Kiến trúc sư phần mềm | Tô màu ổn định theo loại node/edge | phân biệt các thành phần code | RB14 | 2 | Medium | 1 | New |  |
| PB06 | Search / Filter / Focus | Kiến trúc sư phần mềm | Tìm kiếm node theo tên | định vị nhanh thành phần cần xem | RB15 | 1 | High | 1 | New |  |
| PB06 | Search / Filter / Focus | Kiến trúc sư phần mềm | Lọc theo loại node và loại edge kèm số đếm | thu hẹp graph theo nhu cầu | RB16 | 2 | Medium | 2 | New |  |
| PB06 | Search / Filter / Focus | Kiến trúc sư phần mềm | Focus mode N-hop (1/2/3/5/All) | tập trung vào vùng liên quan | RB17 | 2 | Medium | 2 | New |  |
| PB07 | Node Detail | Kiến trúc sư phần mềm | Xem panel chi tiết node với cạnh in/out | hiểu sâu một thành phần code | RB18 | 2 | Medium | 2 | New |  |
| PB08 | Impact Analysis | Lập trình viên | Xem blast radius của một node | đánh giá ảnh hưởng trước khi sửa code | RB19 | 2 | Medium | 2 | New |  |
| PB09 | Realtime Update | Lập trình viên | Nhận cập nhật graph realtime qua WebSocket/STOMP | thấy thay đổi tức thì | RB20 | 2 | Medium | 2 | New |  |
| PB09 | Realtime Update | Lập trình viên | File Watcher phát hiện thay đổi .java và cập nhật tăng dần | graph đồng bộ với mã nguồn | RB21 | 3 | Low | 2 | New |  |
| PB10 | Mermaid Diagram | Kiến trúc sư phần mềm | Sinh Use Case Diagram (Mermaid) | xem luồng nghiệp vụ từ controller/job | RB22 | 2 | Medium | 2 | New |  |
| PB10 | Mermaid Diagram | Kiến trúc sư phần mềm | Sinh Class Diagram (Mermaid) lọc theo package | xem quan hệ class/interface | RB23 | 2 | Medium | 2 | New |  |
| PB11 | MCP Tools | AI coding tool | Gọi get_project_architecture | lấy ngữ cảnh kiến trúc tổng thể | RB24 | 2 | Medium | 2 | New |  |
| PB11 | MCP Tools | AI coding tool | Gọi get_class_context | lấy ngữ cảnh chi tiết một class | RB25 | 2 | Medium | 2 | New |  |
| PB11 | MCP Tools | AI coding tool | Gọi get_impact_analysis | lấy phân tích ảnh hưởng cho AI | RB26 | 2 | Medium | 2 | New |  |
| PB12 | Docker & CI/CD | DevOps | Chạy docker compose up cho BE/FE/Neo4j | triển khai toàn hệ thống bằng một lệnh | RB27 | 2 | Medium | 2 | New |  |
| PB12 | Docker & CI/CD | DevOps | Cấu hình GitHub Actions CI cho backend và frontend | kiểm thử tự động trên mỗi PR | RB28 | 2 | Medium | 2 | New |  |

## PPS

|  | Đặc điểm |  |  |  | Điểm UP | Hệ số nhân (C) | AP | ED | PPS |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
|  | Loại tương tác | Quy tắc nghiệp vụ | Thực thể | Loại thao tác dữ liệu |  |  |  |  |  |
| Sprint 1 |  |  |  |  |  |  |  |  |  |
| Upload & import archive ZIP/TAR | 3 | 3 | 3 | 3 | 12 | 0.5 | 6 | 30 | 5 |
| Kiểm tra loại & dung lượng archive | 2 | 3 | 1 | 1 | 7 | 0.5 | 3.5 | 30 | 2.917 |
| Giải nén an toàn chống path traversal | 3 | 3 | 2 | 2 | 10 | 0.5 | 5 | 30 | 4.167 |
| Parse file .java (visitors) | 3 | 2 | 3 | 2 | 10 | 0.5 | 5 | 30 | 4.167 |
| Sinh structural edges | 2 | 2 | 3 | 1 | 8 | 0.5 | 4 | 30 | 3.333 |
| Phát hiện Spring annotation/layer | 2 | 3 | 2 | 1 | 8 | 0.5 | 4 | 30 | 3.333 |
| Cấu hình Neo4j Java Driver raw | 2 | 2 | 2 | 2 | 8 | 0.5 | 4 | 30 | 3.333 |
| Cypher schema migration | 2 | 3 | 2 | 2 | 9 | 0.5 | 4.5 | 30 | 3.75 |
| Upsert node/edge vào Neo4j | 3 | 2 | 3 | 3 | 11 | 0.5 | 5.5 | 30 | 4.583 |
| Lấy full graph theo projectId | 2 | 2 | 3 | 1 | 8 | 0.5 | 4 | 30 | 3.333 |
| Render graph Sigma.js | 3 | 1 | 3 | 1 | 8 | 0.5 | 4 | 30 | 3.333 |
| Tô màu theo loại node/edge | 2 | 1 | 2 | 1 | 6 | 0.5 | 3 | 30 | 2.5 |
| Tìm kiếm node theo tên | 2 | 2 | 2 | 1 | 7 | 0.5 | 3.5 | 30 | 2.917 |
| REST API project/analyze/graph | 3 | 3 | 3 | 2 | 11 | 0.5 | 5.5 | 30 | 4.583 |
| Sprint 2 |  |  |  |  |  |  |  |  |  |
| Import GitHub public repo | 3 | 3 | 3 | 3 | 12 | 0.5 | 6 | 30 | 5 |
| Pre-flight kiểm tra repo | 2 | 3 | 2 | 1 | 8 | 0.5 | 4 | 30 | 3.333 |
| Resolve CALLS bằng SymbolSolver | 3 | 2 | 3 | 1 | 9 | 0.5 | 4.5 | 30 | 3.75 |
| Lọc theo loại node/edge | 2 | 2 | 3 | 1 | 8 | 0.5 | 4 | 30 | 3.333 |
| Focus mode N-hop | 3 | 2 | 3 | 1 | 9 | 0.5 | 4.5 | 30 | 3.75 |
| Node Detail API + panel | 2 | 2 | 3 | 2 | 9 | 0.5 | 4.5 | 30 | 3.75 |
| Impact analysis (blast radius) | 3 | 3 | 3 | 2 | 11 | 0.5 | 5.5 | 30 | 4.583 |
| WebSocket realtime update | 3 | 3 | 2 | 2 | 10 | 0.5 | 5 | 30 | 4.167 |
| File Watcher incremental | 3 | 3 | 2 | 3 | 11 | 0.5 | 5.5 | 30 | 4.583 |
| Use Case Diagram (Mermaid) | 2 | 3 | 3 | 1 | 9 | 0.5 | 4.5 | 30 | 3.75 |
| Class Diagram (Mermaid) | 2 | 3 | 3 | 1 | 9 | 0.5 | 4.5 | 30 | 3.75 |
| MCP tools (4 tool) | 3 | 3 | 3 | 2 | 11 | 0.5 | 5.5 | 30 | 4.583 |
| Docker compose BE/FE/Neo4j | 2 | 2 | 2 | 2 | 8 | 0.5 | 4 | 30 | 3.333 |
| GitHub Actions CI BE/FE | 2 | 2 | 1 | 1 | 6 | 0.5 | 3 | 30 | 2.5 |
| TỔNG |  |  |  |  |  |  |  | 840 | 105.414 |

## ED

| Khía cạnh | STT | Câu hỏi | Trả lời | Điểm (0/2) |
| --- | --- | --- | --- | --- |
| Tổ chức | 1 | Đã có nhóm/đội từng áp dụng Scrum thành công? | Có | 2 |
|  | 2 | Có chống đối mạnh với Scrum? (Có→0, Không→2) | Không | 2 |
|  | 3 | Có hỗ trợ tốt giữa các thành viên/bộ môn? | Có | 2 |
| Hạ tầng | 1 | Đã có CI/CD (GitHub Actions)? | Có | 2 |
|  | 2 | Kiểm thử tự động đã phổ biến? | Một phần | 1 |
|  | 3 | Môi trường Docker/Neo4j sẵn sàng? | Có | 2 |
| Nhóm | 1 | Scrum hoàn toàn mới với nhóm? (Có→0, Không→2) | Một phần | 1 |
|  | 2 | Thành viên từng làm việc cùng nhau? | Có | 2 |
|  | 3 | Hiểu và tôn trọng lẫn nhau? | Có | 2 |
| Công nghệ | 1 | Có kinh nghiệm Java 21 / Spring Boot? | Có | 2 |
|  | 2 | Có kinh nghiệm Vue 3 / TypeScript / Sigma.js? | Một phần | 1 |
|  | 3 | Có kinh nghiệm Neo4j / Cypher? | Một phần | 1 |
| Quy trình | 1 | Scrum được áp dụng chính thức cho dự án? | Có | 2 |
|  | 2 | Có vai trò PO/SM rõ ràng và hỗ trợ? | Một phần | 1 |
|  | 3 | Có phản đối quy trình? (Có→0, Không→2) | Không | 2 |
| Nghiệp vụ | 1 | PO sẵn sàng và gắn bó với dự án? | Có | 2 |
|  | 2 | PO thiếu kinh nghiệm Scrum? (Có→0, Không→2) | Không thiếu | 2 |
|  | 3 | Phạm vi nghiệp vụ MVP rõ ràng? | Một phần | 1 |
| Tổng cộng |  |  |  | 30 |

## Sprint Backlog

| Task ID | Task | Description | Story ID | Backlog ID | Sprint# | State | Estimate Time<br>(Hours) | Assign to | Note |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Import Archive (Backend) |  |  |  |  |  |  |  |  |  |
| T01 | Create ImportArchive request/response DTO | Tạo DTO request/response cho import archive | RB01 | PB01 | 1 | New | 3 | Khoa |  |
| T02 | Design ArchiveImportService interface | Thiết kế interface ArchiveImportService | RB01 | PB01 | 1 | New | 2 | Khoa |  |
| T03 | Implement ArchiveTypeDetector | Nhận diện loại archive zip/tar/tar.gz | RB02 | PB01 | 1 | New | 4 | Vinh |  |
| T04 | Implement safe ArchiveExtractor | Giải nén chống path traversal (zip-slip) | RB03 | PB01 | 1 | New | 6 | Vinh |  |
| T05 | Implement POST /api/projects/import-archive | Tạo endpoint upload archive multipart | RB01 | PB01 | 1 | New | 5 | Khoa |  |
| T06 | Unit test ArchiveExtractor | Test chống zip-slip và giới hạn dung lượng | RB03 | PB01 | 1 | New | 3 | Thịnh |  |
| GitHub Import (Backend) |  |  |  |  |  |  |  |  |  |
| T07 | Implement GitHubUrlParser | Tách owner/repo từ URL GitHub | RB04 | PB02 | 2 | New | 3 | Khoa |  |
| T08 | Implement GitHub pre-flight check | Kiểm tra private/size/default branch qua GitHub API | RB05 | PB02 | 2 | New | 4 | Khoa |  |
| T09 | Implement TarballImportService | Stream tarball và parse .java trong bộ nhớ | RB04 | PB02 | 2 | New | 6 | Vinh |  |
| T10 | Implement POST /api/projects/import-github | Tạo endpoint import repo GitHub | RB04 | PB02 | 2 | New | 4 | Vinh |  |
| T11 | Integration test GitHub import | Test luồng import repo công khai mẫu | RB04 | PB02 | 2 | New | 4 | Thịnh |  |
| JavaParser & Visitors (Backend) |  |  |  |  |  |  |  |  |  |
| T12 | Refine NodeData/EdgeData/ParseResult | Chuẩn hóa data contract trung lập parser | RB06 | PB03 | 1 | New | 3 | Thái |  |
| T13 | Complete ClassVisitor | Trích xuất class/interface/enum | RB06 | PB03 | 1 | New | 4 | Khoa |  |
| T14 | Complete MethodVisitor | Trích xuất method, param, return, throws | RB06 | PB03 | 1 | New | 4 | Vinh |  |
| T15 | Complete FieldVisitor | Trích xuất field, visibility, injection | RB06 | PB03 | 1 | New | 3 | Vinh |  |
| T16 | Complete ImportVisitor | Sinh IMPORTS edge từ import statement | RB07 | PB03 | 1 | New | 3 | Thái |  |
| T17 | Complete SpringAnnotationVisitor | Phát hiện annotation và layer Spring | RB08 | PB03 | 1 | New | 4 | Khoa |  |
| T18 | Emit structural edges | Sinh HAS_METHOD/HAS_FIELD/EXTENDS/IMPLEMENTS | RB07 | PB03 | 1 | New | 5 | Khoa |  |
| T19 | Wire JavaSymbolSolver for CALLS | Resolve CALLS edge bằng Symbol Solver | RB09 | PB03 | 2 | New | 6 | Vinh |  |
| T20 | Unit test visitors | Bật và pass test các visitor | RB06 | PB03 | 1 | New | 4 | Thịnh |  |
| Neo4j Storage (Backend) |  |  |  |  |  |  |  |  |  |
| T21 | Configure Neo4j Java Driver raw | Cấu hình Neo4jConfig dùng driver thuần | RB10 | PB04 | 1 | New | 4 | Vinh |  |
| T22 | Write Cypher schema migration | Viết V1__init_schema.cypher (constraint, index) | RB11 | PB04 | 1 | New | 4 | Vinh |  |
| T23 | Implement upsertProject/Nodes/Edges | Upsert bằng UNWIND + MERGE tham số hóa | RB10 | PB04 | 1 | New | 6 | Khoa |  |
| T24 | Implement getFullGraph | Truy vấn full graph cho UI | RB12 | PB04 | 1 | New | 5 | Khoa |  |
| T25 | Implement deleteFile (incremental) | Xóa/ghi đè node-edge theo file thay đổi | RB10 | PB04 | 2 | New | 4 | Thịnh |  |
| T26 | ArchUnit test storage boundary | Đảm bảo service không import Neo4j API | RB10 | PB04 | 1 | New | 3 | Thịnh |  |
| REST API (Backend) |  |  |  |  |  |  |  |  |  |
| T27 | Implement ProjectController | CRUD project + ApiResponse | RB12 | PB05 | 1 | New | 4 | Khoa |  |
| T28 | Implement AnalyzeService/Controller | Trigger parse và lưu graph | RB06 | PB05 | 1 | New | 4 | Khoa |  |
| T29 | Implement GraphController | GET /graph trả node/edge/stats | RB12 | PB05 | 1 | New | 3 | Vinh |  |
| T30 | Implement GlobalExceptionHandler | Map lỗi sang ApiResponse có kiểu | RB12 | PB05 | 1 | New | 3 | Thái |  |
| T31 | Implement Node Detail API | GET neighbors/{nodeId} có giới hạn hop | RB18 | PB07 | 2 | New | 5 | Vinh |  |
| T32 | Implement ImpactRepository.getImpact | Cypher duyệt blast radius có LIMIT | RB19 | PB08 | 2 | New | 6 | Khoa |  |
| T33 | Implement Impact Analysis API | GET /impact/{nodeId} | RB19 | PB08 | 2 | New | 4 | Khoa |  |
| T34 | Integration test REST API | Test project/analyze/graph end-to-end | RB12 | PB05 | 1 | New | 4 | Thịnh |  |
| WebSocket Realtime (Backend) |  |  |  |  |  |  |  |  |  |
| T35 | Configure STOMP + AsyncConfig | Cấu hình /ws/graph-updates và executor async | RB20 | PB09 | 2 | New | 4 | Vinh |  |
| T36 | Implement GraphUpdatePublisher | Broadcast topic updates/status có kiểu | RB20 | PB09 | 2 | New | 5 | Vinh |  |
| T37 | Implement FileWatcherService + debounce | WatchService đệ quy, debounce 500ms | RB21 | PB09 | 2 | New | 6 | Khoa |  |
| T38 | Integration test realtime update | Test luồng save→update <3s | RB20 | PB09 | 2 | New | 4 | Thịnh |  |
| Diagram Service (Backend) |  |  |  |  |  |  |  |  |  |
| T39 | Implement UseCaseDiagramService | Sinh Mermaid từ route/job/listener | RB22 | PB10 | 2 | New | 5 | Khoa |  |
| T40 | Implement ClassDiagramService | Sinh Mermaid class + lọc package | RB23 | PB10 | 2 | New | 5 | Thái |  |
| T41 | Implement DiagramController | Endpoint /diagrams/usecase và /class | RB22 | PB10 | 2 | New | 3 | Thái |  |
| T42 | Unit test diagram services | Test cú pháp Mermaid hợp lệ | RB23 | PB10 | 2 | New | 3 | Thịnh |  |
| MCP Tools (Backend) |  |  |  |  |  |  |  |  |  |
| T43 | Implement get_project_architecture | MCP tool trả layers/counts/patterns | RB24 | PB11 | 2 | New | 5 | Khoa |  |
| T44 | Implement get_class_context | MCP tool trả ngữ cảnh class | RB25 | PB11 | 2 | New | 5 | Vinh |  |
| T45 | Implement get_impact_analysis | MCP tool trả phân tích ảnh hưởng | RB26 | PB11 | 2 | New | 4 | Khoa |  |
| T46 | Implement get_layer_pattern | MCP tool trả ví dụ theo layer | RB24 | PB11 | 2 | New | 4 | Thái |  |
| T47 | Configure Spring AI MCP Server | Cấu hình streamable HTTP cho MCP | RB24 | PB11 | 2 | New | 3 | Thái |  |
| Frontend Core (Frontend) |  |  |  |  |  |  |  |  |  |
| T48 | Create Axios API client | projectApi/graphApi khớp ApiResponse | RB12 | PB05 | 1 | New | 3 | Thái |  |
| T49 | Create Pinia graph store | State quản lý graph và filter | RB13 | PB05 | 1 | New | 3 | Thái |  |
| T50 | Implement graphAdapter | Chuyển API response sang Graphology | RB13 | PB05 | 1 | New | 4 | Thái |  |
| T51 | Implement useSigma + GraphCanvas | Render Sigma.js, dọn dẹp đúng cách | RB13 | PB05 | 1 | New | 6 | Thái |  |
| T52 | Implement SearchBar | Tìm node theo tên trên graph | RB15 | PB06 | 1 | New | 4 | Thịnh |  |
| T53 | Loading/empty/error states | Xử lý trạng thái tải/rỗng/lỗi của graph | RB13 | PB05 | 1 | New | 3 | Thái |  |
| Frontend Panels (Frontend) |  |  |  |  |  |  |  |  |  |
| T54 | Create Import Archive UI | Form Add Project upload archive | RB01 | PB01 | 1 | New | 4 | Thịnh |  |
| T55 | Create GitHub Import Form | Form nhập URL repo GitHub | RB04 | PB02 | 2 | New | 4 | Thịnh |  |
| T56 | Implement FilterPanel | Toggle loại node/edge kèm số đếm | RB16 | PB06 | 2 | New | 5 | Thái |  |
| T57 | Implement Focus Mode | Focus N-hop và làm mờ node ngoài vùng | RB17 | PB06 | 2 | New | 5 | Thái |  |
| T58 | Implement NodeDetailPanel | Panel chi tiết node in/out | RB18 | PB07 | 2 | New | 4 | Thịnh |  |
| T59 | Implement ImpactAnalysis panel | Panel hiển thị blast radius | RB19 | PB08 | 2 | New | 4 | Thịnh |  |
| T60 | Implement useWebSocket composable | Kết nối WS và patch graph realtime | RB20 | PB09 | 2 | New | 5 | Thái |  |
| T61 | Implement DiagramPanel | Render Mermaid Use Case/Class | RB22 | PB10 | 2 | New | 4 | Thịnh |  |
| T62 | Unit test FE components | Test component bằng Vitest | RB13 | PB06 | 2 | New | 4 | Thịnh |  |
| DevOps |  |  |  |  |  |  |  |  |  |
| T63 | Write backend Dockerfile | Đóng gói Spring Boot backend | RB27 | PB12 | 2 | New | 3 | Vinh |  |
| T64 | Write frontend Dockerfile + nginx | Đóng gói Vue build + nginx.conf | RB27 | PB12 | 2 | New | 3 | Vinh |  |
| T65 | Write docker-compose | Compose BE/FE/Neo4j với network/volume | RB27 | PB12 | 2 | New | 4 | Vinh |  |
| T66 | Configure env profiles | Tách profile dev/prod và biến môi trường | RB27 | PB12 | 2 | New | 3 | Thái |  |
| T67 | GitHub Actions backend CI | Workflow build + test backend | RB28 | PB12 | 2 | New | 4 | Vinh |  |
| T68 | GitHub Actions frontend CI | Workflow lint/type-check/test frontend | RB28 | PB12 | 2 | New | 3 | Thịnh |  |
| T69 | Write Docker run guide | Tài liệu chạy docker compose | RB27 | PB12 | 2 | New | 2 | Thái |  |
| Final Integration & Testing (QA) |  |  |  |  |  |  |  |  |  |
| T70 | FileWatcher incremental e2e test | Test cập nhật tăng dần đầu-cuối | RB21 | PB13 | 2 | New | 4 | Thịnh |  |
| T71 | Final integration verification | Kiểm tra tích hợp BE+FE+Neo4j | RB28 | PB14 | 2 | New | 4 | Thịnh |  |
| T72 | Write MCP & demo documentation | Viết tài liệu MCP và kịch bản demo | RB24 | PB14 | 2 | New | 3 | Thái |  |
| TỔNG THỜI GIAN |  |  |  |  |  |  | 290 |  |  |
|  |  |  |  |  |  |  | số người | giờ/ngày | ngày/sprint |
|  |  |  |  |  |  |  | 4 | 8 | 12 |
|  |  |  |  |  |  |  | Năng lực/sprint (4 người x 8h x 12 ngày) | 384 |  |

## RULE

| Title | Mô tả ngắn gọn của PBI/User Story, thể hiện đúng ý định. Ví dụ: 'Import archive ZIP/TAR project Java'. |
| --- | --- |
| Iteration | Sprint mà PBI được thực hiện. WS3 dùng Sprint 1 (nền tảng) và Sprint 2 (tính năng còn lại). |
| Assigned To | Người phụ trách PBI: Khoa, Vinh, Thái, Thịnh. Khoa/Vinh thiên backend/Neo4j/parser/DevOps; Thái/Thịnh thiên frontend/test. |
| State | Trạng thái PBI: New → Approved → Committed → Done (hoặc Removed). Mặc định khởi tạo là 'New'. |
| Business Value | Giá trị nghiệp vụ: High / Medium / Low. Ưu tiên cao cho import, parser, Neo4j, graph visualization. |
| Acceptance Criteria | Tiêu chí chấp nhận gồm cả thành công và thất bại, dạng 'Thành công: ...; Thất bại: ...'. |
| Priority | Độ ưu tiên: 1 (cao) / 2 (trung bình) / 3 (thấp), quyết định thứ tự đưa vào sprint. |
|  | ---- RULE KỸ THUẬT VIBEGRAPH ---- |
| Database | Dùng Neo4j 5.x qua Neo4j Java Driver THUẦN (raw). Cypher chỉ dùng tham số, không nối chuỗi đầu vào. |
| Cấm | KHÔNG dùng JPA/Hibernate. KHÔNG dùng Spring Data Neo4j OGM hay entity @Node. |
| Ranh giới | Chỉ class dưới common/config và graph/repository/impl/neo4j được import API Neo4j driver; service không phụ thuộc trực tiếp Neo4j. |
| Ngoài scope MVP | KHÔNG dùng PostgreSQL, Redis, Kafka, authentication, billing trong MVP. |
| Stack | Backend: Java 21, Spring Boot, JavaParser, WebSocket/STOMP, Spring AI MCP Server, Maven. Frontend: Vue 3, Vite, TypeScript, Axios, Pinia, Vue Router, Sigma.js, Graphology, Mermaid. DevOps: Docker, Docker Compose, GitHub Actions. |
| Bảo mật import | Chống path traversal khi giải nén archive; giới hạn 100MB; chỉ nhận .zip/.tar/.tar.gz; chỉ import public GitHub repo. |

