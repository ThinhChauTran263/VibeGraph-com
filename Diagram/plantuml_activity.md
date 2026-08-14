# VibeGraph Activity Diagrams

Tài liệu này chứa các sơ đồ hoạt động (Activity Diagrams) mô tả các luồng nghiệp vụ chính của dự án VibeGraph.

## Diagram 3.1: Đăng ký → Đăng nhập → Xác thực JWT

```plantuml
@startuml
skinparam activity {
  BackgroundColor white
  BorderColor #333333
  ArrowColor #666666
}

|Khách/Người dùng|
start
:Gửi yêu cầu POST /api/auth/register hoặc /api/auth/login;

|Auth Filter (JwtAuthFilter)|
:Kiểm tra cookie JWT trong HTTP Request;
if (Có JWT hợp lệ?) then (Có)
  :Thiết lập SecurityContext;
  :Chuyển tiếp yêu cầu (Bỏ qua Login/Register);
  stop
else (Không)
  :Tiếp tục xử lý tới Controller;
endif

|AuthController|
:Nhận yêu cầu;
:Gọi AuthService xử lý;

|AuthService|
if (Đăng ký hay Đăng nhập?) then (Đăng ký)
  :Kiểm tra email/username tồn tại;
  :Mã hóa mật khẩu bằng BCrypt;
  :Lưu thông tin User vào PostgreSQL;
  note right: Lưu ý: Lưu User entity
else (Đăng nhập)
  :Xác thực thông tin đăng nhập (credentials);
endif

|JwtService|
:Tạo JWT token cho người dùng;

|AuthCookieService|
:Tạo HttpOnly cookie chứa JWT;
:Gắn cookie vào HTTP Response;

|Khách/Người dùng|
:Nhận thông tin người dùng và HttpOnly Cookie;
stop
@enduml
```

## Diagram 3.2: Import Project

```plantuml
@startuml
skinparam activity {
  BackgroundColor white
  BorderColor #333333
  ArrowColor #666666
}

|Người dùng|
start
:Yêu cầu Import Project;
split
   :Chọn thư mục cục bộ (Local);
   |LocalProjectController|
   :POST /api/project/local;
   |LocalImportService|
   :Quét và lập chỉ mục files từ thư mục;
split again
   |Người dùng|
   :Tải lên file lưu trữ (ZIP/Archive);
   |ImportController|
   :POST /api/project/import/archive;
   |ArchiveImportService|
   :Giải nén tệp lưu trữ vào thư mục tạm;
split again
   |Người dùng|
   :Cung cấp liên kết Tarball (NPM/GitHub);
   |ImportController|
   :POST /api/project/import/tarball;
   |TarballImportService|
   :Tải xuống và giải nén tarball;
end split

|Hệ thống Import|
:Tạo bản ghi Project trong Cơ sở dữ liệu;
:Lưu trữ mã nguồn vào thư mục Workspace;
:Trả về thông tin Project ID;

|Người dùng|
:Nhận thông báo Import thành công;
stop
@enduml
```

## Diagram 3.3: Phân tích mã nguồn (Analyze)

```plantuml
@startuml
skinparam activity {
  BackgroundColor white
  BorderColor #333333
  ArrowColor #666666
}

|Người dùng|
start
:Yêu cầu phân tích mã nguồn dự án;
:POST /api/project/{id}/analyze;

|ProjectController|
:Nhận yêu cầu;
:Chuyển Project ID cho AnalyzeService;

|AnalyzeService|
:Truy xuất thông tin dự án từ DB;
if (Dự án có tồn tại?) then (Không)
  :Trả về lỗi 404 Not Found;
  stop
else (Có)
endif

:Lấy danh sách các tệp mã nguồn trong dự án;
:Lọc các tệp mã nguồn được hỗ trợ (Java, JS, TS, Python...);

|ParserService|
fork
  :Phân tích cú pháp (Tạo cây AST);
fork again
  :Phân tích sự phụ thuộc (Dependencies);
fork again
  :Trích xuất các hàm, class và biến;
end fork

|AnalyzeService|
:Tổng hợp kết quả phân tích;
:Lưu dữ liệu cấu trúc đồ thị vào Neo4j;
:Cập nhật trạng thái dự án (Hoàn tất phân tích);

|ProjectController|
:Trả về kết quả thành công cho Client;
stop
@enduml
```

## Diagram 3.4: Realtime Update (File Watcher)

```plantuml
@startuml
skinparam activity {
  BackgroundColor white
  BorderColor #333333
  ArrowColor #666666
}

|Hệ thống/HĐH|
start
:Phát sinh sự kiện thay đổi file (Tạo, Sửa, Xóa);

|File Watcher Service|
:Bắt sự kiện từ hệ điều hành;
:Lọc bỏ các file không cần thiết (node_modules, .git);
:Đưa sự kiện vào hàng đợi xử lý;

|AnalyzeService|
:Lấy sự kiện từ hàng đợi;
if (Loại thay đổi?) then (Tạo/Sửa đổi)
  :Đọc nội dung file mới;
  :Thực hiện phân tích lại (ParserService);
  :Cập nhật Nodes và Edges mới trong Neo4j;
else (Xóa file)
  :Xóa các Nodes và Edges tương ứng trong Neo4j;
endif

|WebSocketService|
:Tạo sự kiện thông báo thay đổi đồ thị (Graph Update);
:Broadcast thông báo tới các Client đang kết nối;

|Client (Giao diện VibeGraph)|
:Nhận sự kiện qua WebSocket;
:Hiệu ứng cập nhật giao diện đồ thị;
stop
@enduml
```

## Diagram 3.5: AI Tool gọi MCP

```plantuml
@startuml
skinparam activity {
  BackgroundColor white
  BorderColor #333333
  ArrowColor #666666
}

|AI Tool|
start
:Gửi yêu cầu gọi MCP Tool qua HTTP;
:Kèm theo x-api-key header;

|ApiKeyAuthFilter|
:Trích xuất khóa x-api-key từ header;
if (API Key có hợp lệ?) then (Không)
  :Trả về lỗi 401 Unauthorized;
  stop
else (Có)
  :Thiết lập Authentication (API Key User) vào SecurityContext;
endif

|MCP Controller|
:Nhận yêu cầu thực thi Tool;
:Định tuyến (Router) tới công cụ tương ứng (GraphTool, ParseTool...);

|MeteredToolCallback|
:Bắt đầu đo lường thời gian thực thi công cụ;

|MCP Tool|
:Thực thi logic nghiệp vụ của công cụ;
:Tương tác với Graph Database hoặc Source Code;
:Chuẩn bị dữ liệu kết quả trả về;

|MeteredToolCallback|
:Kết thúc đo lường;
:Ghi log (Metric) về thời gian và tài nguyên;

|MCP Controller|
:Trả về kết quả cho AI Tool;

|AI Tool|
:Nhận kết quả thực thi;
stop
@enduml
```

## Diagram 3.6: Admin quản lý người dùng

```plantuml
@startuml
skinparam activity {
  BackgroundColor white
  BorderColor #333333
  ArrowColor #666666
}

|Admin|
start
:Gửi yêu cầu quản lý (Danh sách, Khóa/Mở khóa User);
:Gửi kèm JWT Token của Admin;

|JwtAuthFilter|
:Xác thực và phân giải JWT Token;
if (Token hợp lệ và có quyền ROLE_ADMIN?) then (Không)
  :Trả về lỗi 403 Forbidden;
  stop
else (Có)
endif

|AdminUserController|
:Nhận yêu cầu và chuyển cho AdminService;
if (Loại tác vụ?) then (Lấy danh sách)
  |AdminService|
  :Truy vấn danh sách User (Phân trang, Lọc);
else (Đổi trạng thái tài khoản)
  |AdminService|
  :Tìm kiếm User theo ID;
  :Cập nhật trạng thái (Active / Locked);
  :Lưu thay đổi vào CSDL PostgreSQL;
endif

|AdminUserController|
:Trả về kết quả (Danh sách hoặc thông báo thành công);

|Admin|
:Hiển thị kết quả trên giao diện Dashboard;
stop
@enduml
```
