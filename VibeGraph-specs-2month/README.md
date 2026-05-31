# VibeGraph - Kế hoạch thực tế 2 tháng

**Deadline:** 8 tuần<br>
**Trạng thái:** Kế hoạch đang triển khai<br>
**Nguồn chân lý (source of truth):** Thư mục này, với `architecture.md` làm chuẩn nền tảng kiến trúc<br>
**Bố cục triển khai:** backend single-module tại thư mục gốc `src/main/java/com/vibegraph/...` cùng với `vibegraph-web`

Thư mục này là nguồn chân lý duy nhất cho việc thực thi MVP. Nó đã thay thế bộ tài liệu dài hạn cũ `VibeGraph-specs/`, vốn đã bị gỡ khỏi repo. Các quyết định triển khai chỉ nên bám theo thư mục này.

## Mục tiêu sau 2 tháng

Người dùng mở `vibegraph.com` hoặc chạy Docker stack cục bộ, upload một file ZIP/TAR của project Java, và thấy một đồ thị mã nguồn tương tác. GitHub public-repo import vẫn nằm trong MVP, nhưng archive upload là flow chính Sprint 2 để thay cho việc nhập local path thủ công. Các công cụ AI như Cursor, Claude Code, và Kiro có thể kết nối qua MCP và lấy về ngữ cảnh kiến trúc.

Chế độ local/self-host cũng hỗ trợ theo dõi một thư mục Java và cập nhật đồ thị gần như theo thời gian thực khi các tệp `.java` được tạo mới, thay đổi, hoặc xóa.

> **Trạng thái sau audit 2026-05-30 và quyết định product 2026-05-31:** Sprint 1 hiện đã hoàn tất lát cắt local `register project -> analyze -> Neo4j -> GET graph -> Sigma render`. Từ Sprint 2, UX chính chuyển sang `upload ZIP/TAR archive -> parse -> Neo4j -> graph`; local path giữ lại như dev/internal fallback. GitHub import, watcher/realtime, diagrams, MCP tools, nhiều panel frontend, auth/rate-limit và hardening public demo vẫn là Sprint 2/3; xem `file-checklist.md` và `backlog.md` để biết trạng thái từng file.

## Quyết định kiến trúc hiện tại

Với MVP 2 tháng, giữ nguyên **backend single-module** hiện tại:

- Mã backend nằm dưới `src/main/java/com/vibegraph/{common,parser,graph,diagram,mcp,watcher}`.
- Mã frontend nằm dưới `vibegraph-web`.
- Không tạo `vibegraph-core`, `vibegraph-server`, `vibegraph-cli`, hay `vibegraph-cli-npm` trong Sprint 1.
- Package parser được giữ đủ sạch để có thể tách ra thành module `vibegraph-core` trong tương lai nếu cần đến một CLI.

Lý do: repo đã build được như một ứng dụng Spring Boot đơn lẻ, và đường găng là làm cho một lát cắt dọc (vertical slice) chạy được ở local. Công việc multi-module sẽ làm tăng chi phí build và phụ thuộc trước khi có một bên tiêu thụ parser thứ hai.

## Phạm vi giữ lại cho MVP

- FR-01 phân tích cú pháp Java
- FR-02 lưu trữ Neo4j thông qua `GraphRepository`
- FR-03 trực quan hóa force graph bằng Sigma.js
- FR-04 sơ đồ Use Case
- FR-05 sơ đồ Class
- FR-07 cập nhật realtime
- FR-08 file watcher phía server
- FR-09 REST API
- FR-10 MCP server với 4 tool
- FR-NEW nhập khẩu tarball từ GitHub
- FR-NEW-2 upload project bằng ZIP/TAR archive (flow chính Sprint 2 thay cho nhập local path thủ công)

## Hoãn lại sau MVP

- Sơ đồ Sequence
- Context REST API riêng biệt ngoài các endpoint graph/detail hiện có
- Sinh tệp steering
- Mẫu pre-code hook
- Phân tích cú pháp đa ngôn ngữ ngoài Java
- Auth, Stripe, gói dịch vụ, thanh toán
- GitHub OAuth và nhập khẩu repository riêng tư
- Module CLI chuyên dụng và npm wrapper
- GraalVM native-image
- Chế độ nhúng Kuzu
- Chế độ SaaS multi-tenant với Postgres+AGE
- Plugin IntelliJ

## Các hợp đồng runtime quan trọng

- Stack backend: Spring Boot 4.0.6, Java 21, Maven, Neo4j 5.x.
- Stack parser: JavaParser 3.28.0 cùng Symbol Solver.
- Stack frontend: Vue 3.5, Vite 8, TypeScript 6, Sigma.js 3, Mermaid 11.
- WebSocket endpoint: `/ws/graph-updates`.
- URL frontend khi dev cục bộ: `http://localhost:5173`.
- URL frontend trên Docker: `http://localhost:3000`.
- Khởi động Neo4j khi dev: `docker compose up -d neo4j` từ thư mục gốc repo.
- Build context Docker cho production/backend: thư mục gốc repo dùng `Dockerfile`, không phải `./vibegraph-server`.

## Bản đồ tài liệu

| File | Mục đích |
|---|---|
| `README.md` | Chuẩn nền tảng thực thi này |
| `requirements-trimmed.md` | Yêu cầu chức năng và phi chức năng của MVP |
| `architecture.md` | Các quyết định về kiến trúc và luồng dữ liệu |
| `task-breakdown-8week.md` | Thứ tự triển khai và các mốc kiểm tra theo sprint |
| `file-checklist.md` | Các tệp hiện có và checklist hoàn thành MVP |
| `deployment-plan.md` | Ghi chú triển khai Docker, domain, SSL, CI/CD |
| `presentation.html` | Bản trình bày phi kỹ thuật được sinh ra, không phải nguồn chân lý triển khai |
| `project-structure.html` | Bản đồ dự án trực quan được sinh ra, không phải nguồn chân lý triển khai |

## Đường găng (critical path)

Đường găng của Sprint 1 là một lát cắt dọc (vertical slice):

1. Phân tích cú pháp một thư mục Java cục bộ thành `NodeData` và `EdgeData`.
2. Lưu dữ liệu đồ thị thông qua `GraphRepository` vào Neo4j.
3. Sprint 1 đã mở các endpoint dev/internal `POST /api/projects`, `POST /api/projects/{id}/analyze`, và `GET /api/projects/{id}/graph`; Sprint 2 chuyển UX chính sang `POST /api/projects/import-archive` để user upload ZIP/TAR thay vì nhập local path thủ công.
4. Render đồ thị trả về trong `vibegraph-web` bằng Sigma.js.

Chỉ sau khi việc này chạy được ở local thì dự án mới nên chuyển sang nhập khẩu từ GitHub, vá realtime qua WebSocket, các sơ đồ, và tinh chỉnh MCP.
