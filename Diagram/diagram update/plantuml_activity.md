# VibeGraph - Activity Diagrams (Sơ Đồ Hoạt Động)

Tài liệu này chứa toàn bộ các biểu đồ Hoạt động mô tả luồng nghiệp vụ thực tế của hệ thống VibeGraph, tuân thủ đúng lý thuyết UML với hình thoi khởi đầu (Decision Node) và hình thoi hội tụ (Merge Node).

## 3.1. Đăng ký, Đăng nhập, OAuth2 và Refresh Session

```plantuml
@startuml
skinparam activity {
  BackgroundColor white
  BorderColor #333333
  ArrowColor #666666
}

|Khách / Người dùng|
start
:Gửi yêu cầu xác thực (Đăng ký, Đăng nhập hoặc OAuth);

|AuthController|
:Nhận HTTP Request;
:Throttle tần suất yêu cầu (Rate Limiting);

|AuthService|
switch (Phương thức xác thực?)
case ( Đăng ký )
  :Validate thông tin đăng ký;
  :Mã hóa mật khẩu bằng BCrypt;
  :Lưu User mới và AccountSettings vào PostgreSQL;
case ( Đăng nhập )
  :Xác thực Email và Mật khẩu trong PostgreSQL;
case ( OAuth Callback )
  :Xác thực Google/GitHub OAuth2 Token;
  :Liên kết hoặc tạo mới User Identity;
endswitch

|JwtService|
:Tạo JWT Access Token;

|RefreshSessionService|
:Tạo Hashed Refresh Session Token;
:Lưu phiên làm mới vào CSDL PostgreSQL;

|AuthCookieService|
:Tạo HttpOnly Cookies cho Access & Refresh Token;
:Gắn Cookies vào HTTP Response;

|Khách / Người dùng|
:Nhận phản hồi và lưu HttpOnly Cookie;

|Trình duyệt (Browser)|
:Gửi yêu cầu REST API kèm Cookie;
switch (Access Token hết hạn?)
case ( Có )
  :POST /api/auth/refresh;
  |RefreshSessionService|
  :Đọc Refresh Cookie, lock dòng và xoay vòng Token;
  |AuthCookieService|
  :Cấp Cookie thay thế hoặc xóa Cookie nếu không hợp lệ;
case ( Không )
  |AuthCookieService|
endswitch

|Khách / Người dùng|
:Gửi yêu cầu POST /api/auth/logout;
|RefreshSessionService|
:Thu hồi (Revoke) phiên Refresh Session;
|AuthCookieService|
:Xóa toàn bộ Authentication Cookies;
|Khách / Người dùng|
:Đăng xuất thành công;
stop
@enduml
```

## 3.2. Import Project (Archive, GitHub, Local) và Phân tích bất đồng bộ

```plantuml
@startuml
skinparam activity {
  BackgroundColor white
  BorderColor #333333
  ArrowColor #666666
}

|Người dùng|
start
:Yêu cầu Import dự án mã nguồn;

split
  :Chọn file nén (ZIP/Archive);
  |ImportController|
  :POST /api/projects/import-archive;
  |ArchiveImportService|
  :Giải nén file và kiểm tra cấu trúc;
split again
  |Người dùng|
  :Cung cấp liên kết GitHub Repository;
  |ImportController|
  :POST /api/projects/import-github;
  |TarballImportService|
  :Tải tarball từ GitHub và giải nén;
split again
  |Người dùng|
  :Chọn đường dẫn thư mục cục bộ (Local);
  |LocalProjectController|
  :POST /api/projects/import-local;
  |LocalImportService|
  :Xác thực đường dẫn và kiểm tra quyền đọc;
end split

|ImportService|
:Tạo bản ghi ProjectOwnership và metadata trong PostgreSQL;

switch (Phương thức xử lý phân tích?)
case ( Đồng bộ - Archive sync HTTP 200 )
  |AnalyzeService|
  :Phân tích các file Java và tạo CPG/Flow edges;
  :Ghi kết quả phân tích vào Neo4j (Atomic);
  |ImportController|
  :Trả về HTTP 200 kèm thông tin dự án;
case ( Bất đồng bộ - Async / GitHub / Local )
  |ImportService|
  :Đẩy tác vụ phân tích vào Executor chạy ngầm;
  |ImportController|
  :Trả về HTTP 202 (ANALYZING, progress = 0);
  |AnalyzeService|
  :Phân tích mã nguồn Java và ghi đồ thị vào Neo4j;
  |ImportService|
  :Cập nhật trạng thái ANALYZED và phát sóng qua WebSocket;
  |ImportController|
endswitch

|Người dùng|
:Nhận kết quả và theo dõi tiến trình trên giao diện;
stop
@enduml
```

## 3.3. File Watcher - Cập nhật tăng dần (Incremental Update)

```plantuml
@startuml
skinparam activity {
  BackgroundColor white
  BorderColor #333333
  ArrowColor #666666
}

|Hệ điều hành (OS)|
start
:Phát sinh sự kiện thay đổi file (CREATE / MODIFY / DELETE);

|FileWatcherService|
:WatchService bắt sự kiện từ OS;
:Bỏ qua các thư mục/extension bị ignore (.git, target);
:Debounce sự kiện và phát FileChangeEvent;

|FileChangeBroadcaster|
:Xác định đường dẫn tương đối của file;
:Lấy lát cắt đồ thị TRƯỚC thay đổi (getFileSlice);
:Xóa dữ liệu cũ của file trong Neo4j (deleteFile);

switch (Loại sự kiện thay đổi?)
case ( Tạo mới / Sửa đổi )
  |ParserService|
  :Phân tích lại nội dung file Java (parseFile);
  |GraphRepository|
  :Cập nhật Nodes và Edges mới vào Neo4j;
  |FileChangeBroadcaster|
case ( Xóa file )
  |FileChangeBroadcaster|
  :Ghi nhận thao tác xóa file;
endswitch

|FileChangeBroadcaster|
:Lấy lát cắt đồ thị SAU thay đổi (getFileSlice);
:Tính toán Delta (Added / Removed nodes);

|GraphUpdateController|
:Phát sóng sự kiện INCREMENTAL qua WebSocket STOMP;

|Client (Giao diện VibeGraph)|
:Nhận sự kiện WebSocket và cập nhật đồ thị realtime;
stop
@enduml
```

## 3.4. Khám phá Đồ thị / Mã nguồn & Cập nhật qua CLI Patch

```plantuml
@startuml
skinparam activity {
  BackgroundColor white
  BorderColor #333333
  ArrowColor #666666
}

|Người dùng / CLI|
start
switch (Loại yêu cầu?)
case ( Khám phá đồ thị từ Web )
  :Gửi yêu cầu GET Graph / Neighbors / Impact / Source;
  |GraphController|
  :Xác thực quyền truy cập dự án;
  |GraphService|
  :Truy vấn dữ liệu đồ thị từ Neo4j;
  |GraphPayloadGuard|
  :Lọc và giới hạn kích thước dữ liệu (Payload Cap);
  |Giao diện Web|
  :Render đồ thị tương tác bằng Sigma.js;
case ( Cập nhật mã nguồn qua CLI Patch )
  |Người dùng / CLI|
  :Gửi yêu cầu POST /api/projects/{id}/patch;
  |LocalPatchController|
  :Xác thực API Key và quyền sở hữu dự án;
  |LocalPatchService|
  :Kiểm tra hợp lệ, ghi/xóa file nguyên tử và Commit;
  |PatchAnalysisScheduler|
  :Gộp và lập lịch phân tích lại toàn bộ bất đồng bộ;
  |Người dùng / CLI|
  :Nhận phản hồi patch thành công (requiresAnalyze = true);
endswitch

stop
@enduml
```

## 3.5. Tự động sinh sơ đồ UML (Use Case Diagram)

```plantuml
@startuml
skinparam activity {
  BackgroundColor white
  BorderColor #333333
  ArrowColor #666666
}

|Người dùng|
start
:Yêu cầu sinh sơ đồ UML (GET /api/projects/{id}/diagrams/usecase);

|DiagramController|
:Xác thực quyền sở hữu và kiểm tra trạng thái dự án (ANALYZED);

|UseCaseDiagramServiceImpl|
:Tải cấu trúc đồ thị từ GraphService;

|UseCaseInferenceEngine|
:Phân tích các Node Controller, Route và Service;
:Suy luận danh sách Actors, Goals và Mối quan hệ;

|UseCaseDiagramServiceImpl|
:Tổng hợp kết quả suy luận kèm độ tin cậy (Confidence score);
:Sinh chuỗi mã sơ đồ (PlantUML và Mermaid syntax);

|Giao diện Web (DiagramPanel)|
:Render sơ đồ SVG an toàn (Sanitized SVG);
:Cung cấp các công cụ tương tác (Zoom, Fit, Download PNG);
stop
@enduml
```

## 3.6. AI Assistant gọi MCP Server & Giám sát Admin

```plantuml
@startuml
skinparam activity {
  BackgroundColor white
  BorderColor #333333
  ArrowColor #666666
}

|AI Coding Assistant|
start
:Gửi yêu cầu HTTP Streamable request tới /mcp;
:Kèm theo Header X-API-Key;

|ApiKeyAuthFilter|
:Xác thực X-API-Key và kiểm tra liên kết dự án;
switch (API Key có hợp lệ?)
case ( Không )
  :Trả về lỗi 401 Unauthorized;
  stop
case ( Có )
  |Spring AI MCP Server|
  :Xác định 1 trong 18 MCP Tools đã đăng ký;

  |MeteredToolCallback|
  :Kiểm tra quyền truy cập và trừ Credit sử dụng;

  |MCP Tool Handler|
  :Truy vấn dữ liệu từ Neo4j hoặc mã nguồn trong phạm vi cho phép;

  |AI Coding Assistant|
  :Nhận kết quả ngữ cảnh có cấu trúc (JSON Context);
  stop
endswitch

|Quản trị viên (Admin)|
start
:Kết nối SSE Stream (/api/admin/security/stream hoặc audit-logs/stream);

|AdminController|
:Xác thực quyền ADMIN;

|AdminService|
:Phát sóng các sự kiện bảo mật và nhật ký kiểm toán (Sanitized SSE);

|Quản trị viên (Admin)|
:Cập nhật màn hình giám sát Security Dashboard realtime;
stop
@enduml
```
