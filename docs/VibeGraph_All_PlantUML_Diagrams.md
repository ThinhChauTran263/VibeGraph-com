# VibeGraph - Use Case Diagrams

Tài liệu này chứa tất cả các biểu đồ Use Case (Use Case Diagrams) cho dự án VibeGraph, được viết bằng cú pháp PlantUML.

## 2.1. Use Case Tổng quát

```plantuml
@startuml
skinparam actorStyle hollow
skinparam usecase {
    BackgroundColor LightCyan
    BorderColor DarkCyan
    ArrowColor DimGrey
}
skinparam rectangle {
    BackgroundColor White
    BorderColor DarkCyan
}

actor "Guest\n(Khách vãng lai)" as Guest
actor "User\n(Người dùng)" as User
actor "Admin\n(Quản trị viên)" as Admin
actor "AI Coding Assistant" as AI
actor "System\n(Hệ thống)" as System

rectangle "VibeGraph System" {
    usecase "Xác thực & Tài khoản" as UC_Auth
    usecase "Quản lý Project" as UC_Project
    usecase "Tương tác Graph & Mã nguồn" as UC_Graph
    usecase "Phân tích Impact & UML" as UC_Analysis
    usecase "Quản trị hệ thống" as UC_Admin
    usecase "MCP Server Tools" as UC_MCP
}

Guest --> UC_Auth : Đăng ký/Đăng nhập
User --> UC_Auth : Quản lý tài khoản
User --> UC_Project
User --> UC_Graph
User --> UC_Analysis
Admin --> UC_Admin
Admin --|> User : Kế thừa quyền
AI --> UC_MCP : Tương tác qua API Key
System --> UC_Graph : Cập nhật trạng thái
System --> UC_Project : Xử lý nền
@enduml
```

## 2.2. UC: Đăng ký & Đăng nhập (Guest)

```plantuml
@startuml
skinparam actorStyle hollow
skinparam usecase {
    BackgroundColor LightCyan
    BorderColor DarkCyan
    ArrowColor DimGrey
}

actor "Guest\n(Khách vãng lai)" as Guest
actor "User\n(Người dùng)" as User

rectangle "Auth Subsystem" {
    usecase "Đăng nhập" as UC_Login
    usecase "Đăng ký tài khoản" as UC_Register
    usecase "Quên mật khẩu" as UC_ForgotPassword
    usecase "Xác thực Email" as UC_VerifyEmail
    usecase "Quản lý API Key" as UC_ManageAPIKey
    usecase "Cập nhật Profile" as UC_Profile
}

Guest --> UC_Login
Guest --> UC_Register
Guest --> UC_ForgotPassword

UC_Register .> UC_VerifyEmail : <<include>>
UC_Login <. UC_VerifyEmail : <<extend>> (nếu chưa xác thực)

User --> UC_ManageAPIKey
User --> UC_Profile
@enduml
```

## 2.3. UC: Quản lý Project (User)

```plantuml
@startuml
skinparam actorStyle hollow
skinparam usecase {
    BackgroundColor LightCyan
    BorderColor DarkCyan
    ArrowColor DimGrey
}

actor "User\n(Người dùng)" as User

rectangle "Project Management" {
    usecase "Tạo Project mới" as UC_Create
    usecase "Xem danh sách Project" as UC_List
    usecase "Xóa Project" as UC_Delete
    usecase "Quản lý Local Project" as UC_Local
    usecase "Chỉnh sửa thông tin Project" as UC_Edit
}

User --> UC_Create
User --> UC_List
User --> UC_Delete
User --> UC_Local
User --> UC_Edit

UC_Create .> UC_List : <<include>>
UC_Delete .> UC_List : <<include>>
@enduml
```

## 2.4. UC: Import Project - 3 luồng (User)

```plantuml
@startuml
skinparam actorStyle hollow
skinparam usecase {
    BackgroundColor LightCyan
    BorderColor DarkCyan
    ArrowColor DimGrey
}

actor "User\n(Người dùng)" as User
actor "System\n(Hệ thống)" as System

rectangle "Import Project Subsystem" {
    usecase "Import Project" as UC_Import
    usecase "Import từ GitHub/GitLab" as UC_Git
    usecase "Upload file ZIP" as UC_Zip
    usecase "Import Local Path" as UC_LocalPath
    usecase "Xác thực kho lưu trữ" as UC_AuthRepo
    usecase "Giải nén & Tiền xử lý" as UC_Preprocess
}

User --> UC_Import

UC_Import <|-- UC_Git
UC_Import <|-- UC_Zip
UC_Import <|-- UC_LocalPath

UC_Git .> UC_AuthRepo : <<include>>
UC_Zip .> UC_Preprocess : <<include>>
UC_LocalPath .> UC_Preprocess : <<include>>

System --> UC_Preprocess : Chạy ngầm
@enduml
```

## 2.5. UC: Phân tích mã nguồn (User, System)

```plantuml
@startuml
skinparam actorStyle hollow
skinparam usecase {
    BackgroundColor LightCyan
    BorderColor DarkCyan
    ArrowColor DimGrey
}

actor "User\n(Người dùng)" as User
actor "System\n(File Watcher, CPG)" as System

rectangle "Source Analysis" {
    usecase "Kích hoạt phân tích" as UC_Trigger
    usecase "Trích xuất AST & CPG" as UC_Extract
    usecase "Xem tiến trình phân tích" as UC_Status
    usecase "Theo dõi thay đổi (Watch)" as UC_Watch
    usecase "Cập nhật Graph" as UC_UpdateGraph
}

User --> UC_Trigger
User --> UC_Status

UC_Trigger .> UC_Extract : <<include>>
System --> UC_Extract : Thực thi
System --> UC_Watch : Lắng nghe file
UC_Watch .> UC_UpdateGraph : <<include>> (khi có thay đổi)
@enduml
```

## 2.6. UC: Xem & Tương tác Graph (User)

```plantuml
@startuml
skinparam actorStyle hollow
skinparam usecase {
    BackgroundColor LightCyan
    BorderColor DarkCyan
    ArrowColor DimGrey
}

actor "User\n(Người dùng)" as User

rectangle "Graph Interaction" {
    usecase "Xem Graph 2D/3D" as UC_ViewGraph
    usecase "Truy vấn Node/Edge" as UC_Query
    usecase "Lọc & Tìm kiếm Node" as UC_Filter
    usecase "Xem chi tiết Source Code" as UC_ViewSource
    usecase "Apply Code Patch" as UC_Patch
}

User --> UC_ViewGraph
User --> UC_Query
User --> UC_Filter
User --> UC_ViewSource

UC_ViewGraph .> UC_ViewSource : <<extend>> (khi click vào node)
User --> UC_Patch
UC_Patch .> UC_ViewSource : <<include>>
@enduml
```

## 2.7. UC: Xem UML Diagram (User)

```plantuml
@startuml
skinparam actorStyle hollow
skinparam usecase {
    BackgroundColor LightCyan
    BorderColor DarkCyan
    ArrowColor DimGrey
}

actor "User\n(Người dùng)" as User

rectangle "Diagram Subsystem" {
    usecase "Xem UML Class Diagram" as UC_ClassDiag
    usecase "Xem Sequence Diagram" as UC_SeqDiag
    usecase "Tùy chỉnh hiển thị Diagram" as UC_Customize
    usecase "Xuất file (PNG, SVG)" as UC_Export
}

User --> UC_ClassDiag
User --> UC_SeqDiag
User --> UC_Customize
User --> UC_Export

UC_ClassDiag .> UC_Customize : <<extend>>
UC_SeqDiag .> UC_Customize : <<extend>>
@enduml
```

## 2.8. UC: Impact Analysis (User)

```plantuml
@startuml
skinparam actorStyle hollow
skinparam usecase {
    BackgroundColor LightCyan
    BorderColor DarkCyan
    ArrowColor DimGrey
}

actor "User\n(Người dùng)" as User

rectangle "Impact Analysis Subsystem" {
    usecase "Phân tích ảnh hưởng thay đổi" as UC_Impact
    usecase "Xem danh sách file bị ảnh hưởng" as UC_ListAffected
    usecase "Xem mức độ rủi ro" as UC_Risk
    usecase "Đề xuất Test Case" as UC_TestPlan
}

User --> UC_Impact
UC_Impact .> UC_ListAffected : <<include>>
UC_Impact .> UC_Risk : <<include>>
UC_Impact .> UC_TestPlan : <<extend>>
@enduml
```

## 2.9. UC: MCP Server (AI Assistant)

```plantuml
@startuml
skinparam actorStyle hollow
skinparam usecase {
    BackgroundColor LightCyan
    BorderColor DarkCyan
    ArrowColor DimGrey
}

actor "AI Coding Assistant" as AI

rectangle "MCP Server (Tools)" {
    usecase "Lấy Architecture Context" as UC_Arch
    usecase "Lấy Class/Method Context" as UC_Context
    usecase "Tìm references" as UC_FindRef
    usecase "Phân tích Impact" as UC_AIImpact
    usecase "Đề xuất Test Plan" as UC_AITestPlan
    usecase "Đọc/Tìm kiếm Source" as UC_SearchSource
}

AI --> UC_Arch
AI --> UC_Context
AI --> UC_FindRef
AI --> UC_AIImpact
AI --> UC_AITestPlan
AI --> UC_SearchSource
@enduml
```

## 2.10. UC: Quản trị hệ thống (Admin)

```plantuml
@startuml
skinparam actorStyle hollow
skinparam usecase {
    BackgroundColor LightCyan
    BorderColor DarkCyan
    ArrowColor DimGrey
}

actor "Admin\n(Quản trị viên)" as Admin

rectangle "Admin Subsystem" {
    usecase "Quản lý Người dùng (Khóa/Mở, Đổi Role)" as UC_ManageUser
    usecase "Xem thống kê tổng quan (Overview)" as UC_Overview
    usecase "Quản lý Plan & Pricing" as UC_Plan
    usecase "Quản lý Feature Flag" as UC_FeatureFlag
    usecase "Xem Audit Log & Security Monitor" as UC_Audit
    usecase "Quản lý Storage" as UC_Storage
}

Admin --> UC_ManageUser
Admin --> UC_Overview
Admin --> UC_Plan
Admin --> UC_FeatureFlag
Admin --> UC_Audit
Admin --> UC_Storage
@enduml
```
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
# VibeGraph - Architecture & Database Diagrams

Tài liệu này chứa toàn bộ các biểu đồ ERD (PostgreSQL & Neo4j), Component/Deployment và Class Diagrams cho hệ thống VibeGraph, được định dạng bằng cú pháp PlantUML.

## PART 1: ERD PostgreSQL

```plantuml
@startuml
skinparam handwritten false
skinparam monochrome false
skinparam class {
  BackgroundColor White
  ArrowColor Black
  BorderColor Black
}
hide circle
hide empty members

entity "users" {
  * id : UUID <<PK>>
  --
  email : VARCHAR(255) <<UNIQUE>>
  password_hash : VARCHAR(255)
  display_name : VARCHAR(120)
  avatar_url : VARCHAR(512)
  email_verified : BOOLEAN
  role : VARCHAR(20)
  quota_bytes : BIGINT
  used_bytes : BIGINT
  deactivated : BOOLEAN
  deactivated_at : TIMESTAMPTZ
  deactivation_reason : VARCHAR(500)
  deactivation_reason_safe : VARCHAR(240)
  created_at : TIMESTAMPTZ
  updated_at : TIMESTAMPTZ
}

entity "user_identities" {
  * id : UUID <<PK>>
  --
  user_id : UUID <<FK>>
  provider : VARCHAR(20)
  provider_user_id : VARCHAR(255)
  email : VARCHAR(255)
  created_at : TIMESTAMPTZ
}

entity "projects" {
  * project_id : VARCHAR(64) <<PK>>
  --
  owner_id : UUID <<FK>>
  name : VARCHAR(255)
  source_type : VARCHAR(20)
  size_bytes : BIGINT
  status : VARCHAR(20)
  created_at : TIMESTAMPTZ
  updated_at : TIMESTAMPTZ
}

entity "api_keys" {
  * id : UUID <<PK>>
  --
  user_id : UUID <<FK>>
  key_hash : VARCHAR(255) <<UNIQUE>>
  key_prefix : VARCHAR(16)
  name : VARCHAR(120)
  created_at : TIMESTAMPTZ
  last_used_at : TIMESTAMPTZ
  expires_at : TIMESTAMPTZ
  disabled_at : TIMESTAMPTZ
}

entity "plans" {
  * id : UUID <<PK>>
  --
  code : VARCHAR(32) <<UNIQUE>>
  name : VARCHAR(120)
  storage_limit_bytes : BIGINT
  api_key_limit : INTEGER
  monthly_credit_limit : INTEGER
  contact_sales_required : BOOLEAN
  is_active : BOOLEAN
  sort_order : INTEGER
  created_at : TIMESTAMPTZ
  updated_at : TIMESTAMPTZ
}

entity "user_account_settings" {
  * user_id : UUID <<PK, FK>>
  --
  plan_id : UUID <<FK>>
  storage_quota_override_bytes : BIGINT
  api_key_creation_disabled : BOOLEAN
  blocked_at : TIMESTAMPTZ
  blocked_reason : VARCHAR(255)
  blocked_reason_safe : VARCHAR(255)
  created_at : TIMESTAMPTZ
  updated_at : TIMESTAMPTZ
}

entity "project_usage" {
  * project_id : VARCHAR(64) <<PK, FK>>
  --
  owner_id : UUID <<FK>>
  storage_bytes : BIGINT
  updated_at : TIMESTAMPTZ
}

entity "user_credit_balances" {
  * id : UUID <<PK>>
  --
  user_id : UUID <<FK>>
  period_start : DATE
  period_end : DATE
  credits_limit_snapshot : INTEGER
  credits_used : INTEGER
  credits_adjustment : INTEGER
  created_at : TIMESTAMPTZ
  updated_at : TIMESTAMPTZ
}

entity "credit_pricing_rules" {
  * id : UUID <<PK>>
  --
  operation_code : VARCHAR(64) <<UNIQUE>>
  display_name : VARCHAR(120)
  base_credits : NUMERIC(12,4)
  per_file_credits : NUMERIC(12,4)
  per_mb_credits : NUMERIC(12,4)
  per_1k_nodes_credits : NUMERIC(12,4)
  minimum_credits : INTEGER
  is_active : BOOLEAN
  created_at : TIMESTAMPTZ
  updated_at : TIMESTAMPTZ
}

entity "credit_ledger" {
  * id : UUID <<PK>>
  --
  user_id : UUID <<FK>>
  project_id : VARCHAR(64) <<FK>>
  balance_id : UUID <<FK>>
  source : VARCHAR(20)
  operation_code : VARCHAR(64)
  credits_delta : INTEGER
  metadata : JSONB
  created_at : TIMESTAMPTZ
}

users ||--o{ user_identities : "1 to N"
users ||--o{ projects : "1 to N"
users ||--o{ api_keys : "1 to N"
users ||--|| user_account_settings : "1 to 1"
plans ||--o{ user_account_settings : "1 to N"
projects ||--|| project_usage : "1 to 1"
users ||--o{ project_usage : "1 to N"
users ||--o{ user_credit_balances : "1 to N"
users ||--o{ credit_ledger : "1 to N"
projects ||--o{ credit_ledger : "1 to N"
user_credit_balances ||--o{ credit_ledger : "1 to N"

@enduml
```

## PART 2: ERD Neo4j

```plantuml
@startuml
skinparam handwritten false
skinparam monochrome false
skinparam class {
  BackgroundColor White
  ArrowColor Black
  BorderColor Black
}
hide circle

class ":Project" as Project <<Node>> {
  id : String
  name : String
}

class ":Package" as Package <<Node>> {
  projectId : String
  fullName : String
  name : String
}

class ":File" as File <<Node>> {
  projectId : String
  filePath : String
  name : String
}

class ":Class / :Interface / :Enum / :Annotation" as Type <<Node>> {
  projectId : String
  fullName : String
  name : String
  springLayer : String
}

class ":Method" as Method <<Node>> {
  projectId : String
  fullName : String
  name : String
  paramTypes : String[]
  isStub : Boolean
}

class ":Field" as Field <<Node>> {
  projectId : String
  fullName : String
  name : String
}

class ":Route" as Route <<Node>> {
  projectId : String
  httpMethod : String
  routePath : String
}

Project --> Package : CONTAINS
Package --> File : HAS_FILE
Package --> Package : CHILD_OF
File --> Type : DECLARES
Type --> Method : HAS_METHOD
Type --> Field : HAS_FIELD
Method --> Method : CALLS
Method --> Field : READS / WRITES
Type --> Route : HANDLES_ROUTE
Type --> Type : EXTENDS / IMPLEMENTS / DEPENDS_ON

note "Neo4j Graph Schema: \n- Quan hệ (Relationships) là các đường đi giữa các node\n- Thuộc tính 'projectId' tồn tại trên mọi domain node để multi-tenancy" as N1
@enduml
```

## PART 3: Component/Deployment Diagram

```plantuml
@startuml
skinparam componentStyle rectangle

node "Docker Host" {
  
  node "Nginx Proxy / Frontend Container" as frontend_container {
    component "VibeGraph Web (Vue.js + Vite)" as frontend
  }
  
  node "Backend Container (Spring Boot)" as backend_container {
    component "VibeGraph API" as backend {
      component "Auth Module" as auth
      component "Parser/Analyze Module" as parser
      component "Graph Module" as graph
      component "MCP Server" as mcp
    }
  }

  node "PostgreSQL Container" as postgres_container {
    database "Postgres DB" as postgres
  }

  node "Neo4j Container" as neo4j_container {
    database "Neo4j Graph" as neo4j
  }
}

cloud "External Clients" {
  [Browser / User] as user
  [CLI / MCP Client] as client
}

user --> frontend : HTTP (port 3000)
frontend --> backend : REST (port 8080)
frontend --> backend : WebSocket / STOMP
client --> backend : REST (API Key Auth)
client --> mcp : MCP Protocol (HTTP/SSE)

backend --> postgres : JDBC (port 5432)
backend --> neo4j : Bolt Protocol (port 7687)

note bottom of backend_container
  Spring Boot Application
  Profiles: docker
end note

@enduml
```

## PART 4: Class Diagram - Auth Module

```plantuml
@startuml
skinparam handwritten false
skinparam classAttributeIconSize 0

class User <<Entity>> {
  - id: UUID
  - email: String
  - passwordHash: String
  - displayName: String
  - role: Role
  - quotaBytes: long
  - usedBytes: long
  - deactivated: boolean
  + getId(): UUID
}

class ApiKey <<Entity>> {
  - id: UUID
  - user: User
  - keyHash: String
  - keyPrefix: String
  - name: String
  - expiresAt: Instant
  - disabledAt: Instant
  + isValid(): boolean
}

class Plan <<Entity>> {
  - id: UUID
  - code: String
  - name: String
  - storageLimitBytes: long
  - apiKeyLimit: int
  - monthlyCreditLimit: int
}

class UserCreditBalance <<Entity>> {
  - id: UUID
  - user: User
  - periodStart: LocalDate
  - periodEnd: LocalDate
  - creditsUsed: int
  - creditsAdjustment: int
  + hasAvailableCredits(): boolean
}

class CreditLedger <<Entity>> {
  - id: UUID
  - user: User
  - projectId: String
  - source: String
  - operationCode: String
  - creditsDelta: int
}

class ProjectOwnership <<Entity>> {
  - projectId: String
  - ownerId: UUID
  - name: String
  - sourceType: ProjectSourceType
  - status: ProjectOwnershipStatus
  - sizeBytes: long
}

class AuditLog <<Entity>> {
  - id: UUID
  - actorId: UUID
  - action: String
  - resourceType: String
  - resourceId: String
}

class FeatureFlag <<Entity>> {
  - id: UUID
  - flagKey: String
  - enabled: boolean
  - strategy: String
}

enum Role {
  USER
  ADMIN
}

User "1" *-- "N" ApiKey
User "1" *-- "N" UserCreditBalance
User "1" *-- "N" CreditLedger
User "1" *-- "N" ProjectOwnership

@enduml
```

## PART 5: Class Diagram - Graph/Parser Module

```plantuml
@startuml
skinparam handwritten false
skinparam classAttributeIconSize 0

package "com.vibegraph.parser.node" {
  class NodeData <<Record>> {
    + type: String
    + name: String
    + fullName: String
    + filePath: String
    + lineNumber: int
    + endLine: int
    + properties: Map<String, Object>
  }
  
  class EdgeData <<Record>> {
    + type: String
    + sourceFullName: String
    + targetFullName: String
    + properties: Map<String, Object>
  }
  
  class ParseResult <<Record>> {
    + nodes: List<NodeData>
    + edges: List<EdgeData>
  }
}

package "com.vibegraph.parser.service" {
  interface ParserService {
    + parse(projectPath: Path): ParseResult
  }
  
  class AnalyzeService {
    + analyzeProject(projectId: String, sourcePath: Path): void
  }
}

package "com.vibegraph.graph.repository" {
  interface GraphRepository {
    + saveGraph(projectId: String, nodes: List<NodeData>, edges: List<EdgeData>): void
    + deleteProjectGraph(projectId: String): void
    + queryNodes(query: String): List<NodeData>
  }
}

package "com.vibegraph.graph.service" {
  class GraphService {
    + getProjectGraph(projectId: String): GraphDto
    + getProjectMetrics(projectId: String): MetricsDto
  }
  
  class ProjectService {
    + importArchive(userId: UUID, file: MultipartFile): ProjectOwnership
    + updateProjectStatus(projectId: String, status: String): void
  }
}

AnalyzeService ..> ParserService : uses
AnalyzeService ..> GraphRepository : uses
ParseResult *-- NodeData
ParseResult *-- EdgeData
GraphService ..> GraphRepository : uses
ProjectService ..> AnalyzeService : triggers

@enduml
```
