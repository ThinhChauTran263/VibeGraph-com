# VibeGraph - Verified Use Case Diagrams

These diagrams describe capabilities evidenced by the current source/configuration. They do
not reproduce unsupported claims from the previous report. Evidence references use the
repository-relative `path:line` form.

## 2.1. Current system boundary

```plantuml
@startuml
left to right direction
skinparam actorStyle hollow
skinparam packageStyle rectangle

actor "Guest" as Guest
actor "Authenticated User" as User
actor "Admin" as Admin
actor "AI Coding Client" as AI
actor "CLI / API-key client" as CLI
actor "File system / background workers" as System

rectangle "VibeGraph" {
  usecase "Authenticate and maintain session" as Auth
  usecase "Manage account and API keys" as Account
  usecase "Import and manage projects" as Projects
  usecase "Analyze source" as Analyze
  usecase "Explore graph, source and impact" as Explore
  usecase "Broadcast graph updates" as Broadcast
  usecase "Generate UML use-case view" as Diagram
  usecase "Call MCP tools" as MCP
  usecase "Operate admin/security console" as AdminOps
}

Guest --> Auth
User --> Auth
User --> Account
User --> Projects
User --> Analyze : manual request
User --> Explore
User --> Diagram
Admin --> AdminOps
AI --> MCP
CLI --> Projects : import / patch
System --> Analyze
System --> Broadcast : watcher/STOMP deltas

note right of Auth
SOURCE: src/main/java/com/vibegraph/auth/web/AuthController.java:46-127
OAuth handlers are wired in src/main/java/com/vibegraph/auth/config/SecurityConfig.java:193-202.
end note
note bottom of Account
SOURCE: src/main/java/com/vibegraph/auth/web/AccountController.java
SOURCE: src/main/java/com/vibegraph/auth/web/AccountApiKeyController.java
SOURCE: src/main/java/com/vibegraph/auth/web/AccountNotificationController.java
SOURCE: src/main/java/com/vibegraph/auth/web/AccountReportController.java
end note
@enduml
```

## 2.2. Authentication and account

```plantuml
@startuml
left to right direction
actor "Guest" as Guest
actor "User" as User
rectangle "Authentication / Account" {
  usecase "Register" as Register
  usecase "Login" as Login
  usecase "Refresh rotating session" as Refresh
  usecase "Logout / revoke refresh session" as Logout
  usecase "Read current user" as Me
  usecase "OAuth2 login (Google/GitHub wiring)" as OAuth
  usecase "Update profile/password" as Profile
  usecase "Create/list/enable/disable/delete API keys" as Keys
  usecase "Read usage, ledger, projects, reports, notifications" as SelfService
}
Guest --> Register
Guest --> Login
Guest --> OAuth
User --> Refresh
User --> Logout
User --> Me
User --> Profile
User --> Keys
User --> SelfService
Register .> Refresh : creates session
Login .> Refresh : creates session
OAuth .> Refresh : creates session
@enduml
```

## 2.3. Project lifecycle and import

```plantuml
@startuml
left to right direction
actor "User" as User
actor "Background analysis executor" as Worker
rectangle "Project lifecycle" {
  usecase "Create/list/get project" as CRUD
  usecase "Import archive\n200 by default; optional async=true" as Archive
  usecase "Import GitHub tarball\n202 Accepted" as GitHub
  usecase "Import local path / browse\n202 Accepted" as Local
  usecase "Create CLI setup" as Cli
  usecase "Request manual analysis\n202 Accepted" as Start
  usecase "Move to trash" as Trash
  usecase "List trash" as ListTrash
  usecase "Restore" as Restore
  usecase "Permanently purge" as Purge
}
User --> CRUD
User --> Archive
User --> GitHub
User --> Local
User --> Cli
User --> Start
User --> Trash
User --> ListTrash
User --> Restore
User --> Purge
Archive --> Worker : only when async=true
GitHub --> Worker : service submits analysis
Local --> Worker : service submits analysis
Start --> Worker : manual analyze queued by ProjectAnalysisScheduler
note right of Archive
SOURCE: src/main/java/com/vibegraph/graph/controller/ImportController.java:41-76
SOURCE: src/main/java/com/vibegraph/graph/controller/LocalProjectController.java:31-50
SOURCE: src/main/java/com/vibegraph/graph/service/impl/ArchiveImportServiceImpl.java:132-149
The import services submit asynchronous work before their controllers return 202.
end note
@enduml
```

## 2.4. Source analysis and realtime

```plantuml
@startuml
left to right direction
actor "User" as User
actor "OS file events" as OS
rectangle "Analysis / realtime" {
  usecase "Parse Java source and infer graph" as Parse
  usecase "Persist nodes and edges" as Persist
  usecase "Publish ANALYZING/ANALYZED/FAILED status" as Status
  usecase "Watch local project files" as Watch
  usecase "Apply incremental file delta" as Delta
  usecase "Broadcast STOMP graph update" as Broadcast
}
User --> Parse : analyze request
Parse --> Persist
Parse --> Status
OS --> Watch
Watch --> Delta
Delta --> Broadcast
Status --> Broadcast
note bottom of Delta
The watcher path is a delete + node upsert + edge upsert sequence;
the source does not prove one rollback transaction for the full delta.
SOURCE: src/main/java/com/vibegraph/graph/websocket/FileChangeBroadcaster.java:92-123
end note
@enduml
```

## 2.5. Graph, source, patch and UML

```plantuml
@startuml
left to right direction
actor "Authenticated user" as User
actor "CLI / project-bound API-key client" as CLI
rectangle "Project exploration" {
  usecase "Get full graph" as FullGraph
  usecase "Filter/cap graph payload" as Filter
  usecase "Inspect neighbors" as Neighbors
  usecase "Run impact analysis" as Impact
  usecase "Read bounded source slice" as Source
  usecase "Apply local patch" as Patch
  usecase "Render UML use-case response" as UML
}
User --> FullGraph
User --> Neighbors
User --> Impact
User --> Source
CLI --> Patch
User --> UML
FullGraph --> Filter
note right of Patch
POST /api/projects/{projectId}/patch and /api/projects/current/patch
write the local project tree and schedule a full background re-analysis.
SOURCE: src/main/java/com/vibegraph/patch/controller/LocalPatchController.java:25-77
SOURCE: src/main/java/com/vibegraph/patch/service/impl/LocalPatchServiceImpl.java:399-428
end note
note right of UML
Only GET /api/projects/{projectId}/diagrams/usecase is evidenced.
No class/sequence diagram endpoint is drawn.
The response contains actors/use cases with source and confidence,
relations, inference warnings, PlantUML/Mermaid syntax and projected views.
SOURCE: src/main/java/com/vibegraph/diagram/controller/DiagramController.java:39-79
SOURCE: src/main/java/com/vibegraph/diagram/dto/response/UmlUseCaseResponse.java:20-130
end note
@enduml
```

## 2.6. MCP and AI client

```plantuml
@startuml
left to right direction
actor "AI coding client" as AI
rectangle "MCP streamable HTTP /mcp" {
  usecase "List projects" as List
  usecase "Get architecture/class/layer context" as Context
  usecase "Trace endpoint / references" as Trace
  usecase "Search/read source" as Source
  usecase "Impact analysis" as Impact
  usecase "Method CPG/source" as CPG
  usecase "Find tests / suggest test plan" as Tests
  usecase "Plan/verify code change" as Change
  usecase "Explain failure/compile error" as Explain
  usecase "Get project conventions" as Conventions
}
AI --> List
AI --> Context
AI --> Trace
AI --> Source
AI --> Impact
AI --> CPG
AI --> Tests
AI --> Change
AI --> Explain
AI --> Conventions
note bottom of Context
18 tool objects are registered in src/main/java/com/vibegraph/common/config/McpServerConfig.java:49-108
and documented in src/main/java/com/vibegraph/mcp/MODULE-GUIDE.md:8-42.
Each tool resolves its own bounded data source; the diagram does not claim that every tool
directly reads source files or Neo4j.
end note
@enduml
```

## 2.7. Admin and security operations

```plantuml
@startuml
left to right direction
actor "Admin" as Admin
rectangle "Admin control plane" {
  usecase "Manage users and account state" as Users
  usecase "Manage plans and feature flags" as Plans
  usecase "Read/adjust user credit balances" as Credits
  usecase "Create/update/deactivate pricing rules" as Pricing
  usecase "Read/stream audit logs" as Audit
  usecase "Read/stream security telemetry" as Security
  usecase "Inspect request events, top users/IPs\nand suspicious networks" as Abuse
  usecase "List/create/update/delete IP blocks" as IpBlocks
  usecase "Manage announcements and support reports" as Support
  usecase "Read storage overview" as Storage
  usecase "Manage API-key locks" as KeyLock
}
Admin --> Users
Admin --> Plans
Admin --> Credits
Admin --> Pricing
Admin --> Audit
Admin --> Security
Admin --> Abuse
Admin --> IpBlocks
Admin --> Support
Admin --> Storage
Admin --> KeyLock
note right of Storage
The current storage controller is read-only; no destructive storage
management endpoint is evidenced.
SOURCE: src/main/java/com/vibegraph/auth/web/AdminStorageController.java:15-22
end note
note bottom of Credits
SOURCE: src/main/java/com/vibegraph/auth/web/AdminCreditController.java:21-42
SOURCE: src/main/java/com/vibegraph/auth/web/AdminPricingController.java:24-52
SOURCE: src/main/java/com/vibegraph/abuse/AdminAbuseController.java:23-84
end note
@enduml
```

## Not drawn as current behavior

The old diagrams mention forgot-password, email verification, GitLab import, class/sequence
diagram generation, broad PNG/SVG export, and a 3D graph. Repository search found no current
endpoint/service contract proving those features, so they are documented as stale in
`changes/usecase.changes.md` rather than represented as live use cases.
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
# VibeGraph - Verified ERD, Component and Class Diagrams

## PART 1: PostgreSQL control-plane ERD

```plantuml
@startuml
hide circle
skinparam linetype ortho

entity users {
  * id : UUID <<PK>>
  --
  * email : VARCHAR
  password_hash : VARCHAR
  display_name : VARCHAR
  avatar_url : VARCHAR
  email_verified : BOOLEAN
  role : VARCHAR
  quota_bytes : BIGINT
  used_bytes : BIGINT
  deactivated : BOOLEAN
  deactivated_at : TIMESTAMPTZ
  deactivation_reason : VARCHAR
  deactivation_reason_safe : VARCHAR
  created_at : TIMESTAMPTZ
  updated_at : TIMESTAMPTZ
}
entity user_identities {
  * id : UUID <<PK>>
  user_id : UUID <<FK>>
  provider : VARCHAR
  provider_user_id : VARCHAR
  email : VARCHAR
  created_at : TIMESTAMPTZ
}
entity projects {
  * project_id : VARCHAR <<PK>>
  owner_id : UUID <<FK>>
  name : VARCHAR
  source_type : VARCHAR
  size_bytes : BIGINT
  status : VARCHAR
  deleted_at : TIMESTAMPTZ
  created_at : TIMESTAMPTZ
  updated_at : TIMESTAMPTZ
}
entity api_keys {
  * id : UUID <<PK>>
  user_id : UUID <<FK>>
  project_id : VARCHAR <<FK>>
  key_hash : VARCHAR <<UNIQUE>>
  key_prefix : VARCHAR
  name : VARCHAR
  created_at : TIMESTAMPTZ
  last_used_at : TIMESTAMPTZ
  expires_at : TIMESTAMPTZ
  disabled_at : TIMESTAMPTZ
  deleted_at : TIMESTAMPTZ
  disabled_by : VARCHAR
  disabled_reason : VARCHAR
  locked_by : VARCHAR
}
entity plans {
  * id : UUID <<PK>>
  code : VARCHAR <<UNIQUE>>
  name : VARCHAR
  storage_limit_bytes : BIGINT
  api_key_limit : INTEGER
  monthly_credit_limit : INTEGER
  contact_sales_required : BOOLEAN
  is_active : BOOLEAN
  sort_order : INTEGER
  created_at : TIMESTAMPTZ
  updated_at : TIMESTAMPTZ
}
entity user_account_settings {
  * user_id : UUID <<PK/FK>>
  plan_id : UUID <<FK>>
  storage_quota_override_bytes : BIGINT
  credit_quota_override : INTEGER
  api_key_creation_disabled : BOOLEAN
  blocked_at : TIMESTAMPTZ
  blocked_reason : VARCHAR
  blocked_reason_safe : VARCHAR
  created_at : TIMESTAMPTZ
  updated_at : TIMESTAMPTZ
}
entity project_usage {
  * project_id : VARCHAR <<PK/FK>>
  owner_id : UUID <<FK>>
  storage_bytes : BIGINT
  updated_at : TIMESTAMPTZ
}
entity user_credit_balances {
  * id : UUID <<PK>>
  user_id : UUID <<FK>>
  period_start : DATE
  period_end : DATE
  credits_limit_snapshot : INTEGER
  credits_used : INTEGER
  credits_adjustment : INTEGER
  created_at : TIMESTAMPTZ
  updated_at : TIMESTAMPTZ
}
entity credit_pricing_rules {
  * id : UUID <<PK>>
  operation_code : VARCHAR <<UNIQUE>>
  display_name : VARCHAR
  base_credits : NUMERIC
  per_file_credits : NUMERIC
  per_mb_credits : NUMERIC
  per_1k_nodes_credits : NUMERIC
  minimum_credits : INTEGER
  is_active : BOOLEAN
  created_at : TIMESTAMPTZ
  updated_at : TIMESTAMPTZ
}
entity credit_ledger {
  * id : UUID <<PK>>
  user_id : UUID <<FK>>
  project_id : VARCHAR <<FK>>
  balance_id : UUID <<FK>>
  source : VARCHAR
  operation_code : VARCHAR
  credits_delta : INTEGER
  metadata : JSONB
  created_at : TIMESTAMPTZ
}
entity feature_flags {
  * id : UUID <<PK>>
  flag_key : VARCHAR <<UNIQUE>>
  scope : VARCHAR
  display_name : VARCHAR
  description : VARCHAR
  enabled : BOOLEAN
  created_at : TIMESTAMPTZ
  updated_at : TIMESTAMPTZ
}
entity announcements {
  * id : UUID <<PK>>
  type : VARCHAR
  severity : VARCHAR
  target : VARCHAR
  title : VARCHAR
  body : VARCHAR
  starts_at : TIMESTAMPTZ
  ends_at : TIMESTAMPTZ
  dismissible : BOOLEAN
  active : BOOLEAN
  created_by_user_id : UUID <<FK>>
  created_at : TIMESTAMPTZ
  updated_at : TIMESTAMPTZ
}
entity user_notifications {
  * id : UUID <<PK>>
  user_id : UUID <<FK>>
  announcement_id : UUID <<FK>>
  read_at : TIMESTAMPTZ
  dismissed_at : TIMESTAMPTZ
  created_at : TIMESTAMPTZ
}
entity feedback_reports {
  * id : UUID <<PK>>
  user_id : UUID <<FK>>
  status : VARCHAR
  category : VARCHAR
  title : VARCHAR
  delete_after : TIMESTAMPTZ
  created_at : TIMESTAMPTZ
  closed_at : TIMESTAMPTZ
}
entity feedback_messages {
  * id : UUID <<PK>>
  report_id : UUID <<FK>>
  sender_user_id : UUID <<FK>>
  sender_role : VARCHAR
  body : TEXT
  created_at : TIMESTAMPTZ
}
entity security_events {
  * id : UUID <<PK>>
  event_type : VARCHAR
  severity : VARCHAR
  subject_user_id : UUID <<FK>>
  api_key_ref : VARCHAR
  source : VARCHAR
  description : VARCHAR
  created_at : TIMESTAMPTZ
}
entity request_events {
  * id : UUID <<PK>>
  user_id : UUID <<FK>>
  api_key_ref : VARCHAR
  ip_address : VARCHAR
  route : VARCHAR
  http_method : VARCHAR
  status : INTEGER
  event_type : VARCHAR
  occurred_at : TIMESTAMPTZ
}
entity ip_blocks {
  * id : UUID <<PK>>
  ip_address : VARCHAR <<UNIQUE>>
  safe_reason : VARCHAR
  expires_at : TIMESTAMPTZ
  created_by : UUID <<FK>>
  active : BOOLEAN
  created_at : TIMESTAMPTZ
  updated_at : TIMESTAMPTZ
}
entity audit_logs {
  * id : UUID <<PK>>
  action : VARCHAR
  actor_user_id : UUID <<logical ref; no FK after V15>>
  target_user_id : UUID <<logical ref; no FK after V15>>
  target_type : VARCHAR
  target_id : VARCHAR
  outcome : VARCHAR
  ip_address : VARCHAR
  details : VARCHAR
  created_at : TIMESTAMPTZ
}
entity audit_retention_settings {
  * id : SMALLINT <<PK>>
  retention_days : INTEGER
  updated_by : UUID <<FK>>
  updated_at : TIMESTAMPTZ
}
entity refresh_sessions {
  * id : UUID <<PK>>
  user_id : UUID <<FK>>
  family_id : UUID
  token_hash : VARCHAR <<UNIQUE>>
  expires_at : TIMESTAMPTZ
  last_used_at : TIMESTAMPTZ
  revoked_at : TIMESTAMPTZ
  revoke_reason : VARCHAR
  replaced_by_id : UUID
  created_at : TIMESTAMPTZ
}

users ||--o{ user_identities
users ||--o{ projects
users ||--o{ api_keys
projects |o--o{ api_keys
users ||--o| user_account_settings
plans ||--o{ user_account_settings
projects ||--o| project_usage
users ||--o{ project_usage
users ||--o{ user_credit_balances
users ||--o{ credit_ledger
projects |o--o{ credit_ledger
user_credit_balances |o--o{ credit_ledger
users |o--o{ announcements : created_by
users ||--o{ user_notifications
announcements ||--o{ user_notifications
users |o--o{ feedback_reports
feedback_reports ||--o{ feedback_messages
users |o--o{ feedback_messages
users |o--o{ security_events
users |o--o{ request_events
users |o--o{ ip_blocks : created_by
users |o--o{ audit_retention_settings : updated_by
users ||--o{ refresh_sessions

note bottom
MIGRATION: src/main/resources/db/migration/V1..V20__*.sql
RUNTIME 2026-08-14T10:12:42+07:00: 21 domain tables, 23 FK relations,
66 domain-table indexes (68 public-schema indexes including 2 Flyway indexes),
and 19 successful Flyway migrations.
Important unique indexes: uq_users_email_lower, uq_identity_provider_uid,
uq_credit_balance_user_period, uq_user_notifications_user_announcement,
and partial uq_api_keys_live_user_project.
end note
@enduml
```

## PART 2: Neo4j graph schema and observed runtime vocabulary

```plantuml
@startuml
left to right direction
skinparam classAttributeIconSize 0

class ":Project" as Project <<Node>> {
  id : String (unique)
  projectId : String
  fullName : String
  name : String
  path : String
  createdAt : timestamp
  lastAnalyzedAt : timestamp
}
class ":Package" as Package <<Node>> {
  projectId : String
  fullName : String (unique per project)
  name : String
}
class ":File" as File <<Node>> {
  projectId : String
  filePath : String (unique per project)
  name : String
}
class ":Class / :Interface / :Enum / :Record / :DBModel" as Type <<Node>> {
  projectId : String
  fullName : String
  name : String
  springLayer : optional
}
class ":Method / :Constructor" as Method <<Node>> {
  projectId : String
  fullName : String
  name : String
  paramTypes : String[]
}
class ":Field / :LocalVariable" as Member <<Node>> {
  projectId : String
  fullName : String
  name : String
}
class ":Annotation" as Annotation <<Node>> {
  projectId : String
  fullName : String
  name : String
}
class ":APIEndpoint" as Endpoint <<Node>> {
  projectId : String
  httpMethod : String
  routePath : String
}
class ":Route" as Route <<Schema-only label>> {
  projectId : String
  httpMethod : String
  routePath : String
  runtime count : 0
}
class ":External" as External <<Node>> {
  projectId : String
  fullName : String
  name : String
}

Project --> Package : CONTAINS
Package --> File : CONTAINS
File --> Type : DEFINES
Type --> Method : HAS_METHOD
Type --> Member : HAS_FIELD
Type --> Type : HAS_INNER
Method --> Method : CALLS / RESOLVES_TO / OVERRIDES / STEP_IN_FLOW
Method --> Type : RETURNS / THROWS / PARAMETER_TYPE
Method --> Member : READS / WRITES
Method --> Type : INSTANTIATES / CATCHES
Type --> Type : EXTENDS / IMPLEMENTS / HAS_RELATION / INJECTS
Type --> Type : IMPORTS
Type --> External : IMPORTS / INJECTS
Type --> Package : IMPORTS
Member --> Type : TYPE_OF
Method --> Endpoint : HANDLES_ROUTE
Type ..> Annotation : ANNOTATED_BY (legacy persisted)

note bottom
MIGRATION: V1 uniqueness constraints = project_id_unique, package_unique, file_unique,
class_unique, interface_unique, enum_unique, annotation_unique, method_unique,
field_unique and route_unique. V1 indexes = class_proj_name, interface_proj_name,
method_proj_name, field_proj_name, class_spring_layer, route_path, file_path,
method_stub and node_search. V2 adds symbol_project and symbol_project_fullname.
Route is constrained/indexed by V1 but has zero runtime nodes; the current parser emits
APIEndpoint nodes (620), which are not covered by Route's constraint/index.
RUNTIME 2026-08-14T10:12:42+07:00: 56,724 nodes and 116,987 relationships.
ANNOTATED_BY=1,712 is persisted legacy data, not a current parser emission.
Current DEFINES emission is File -> Class/Interface/Enum/Record/DBModel; runtime also
contains older persisted endpoints. OWNS and event/dynamic allow-list types had zero rows.
end note
@enduml
```

## PART 3: Component and deployment

```plantuml
@startuml
skinparam componentStyle rectangle
node "Docker host" {
  node "Frontend container\nnginx:1.27-alpine\nhost :3000 -> :80" as FE {
    component "Vue 3 + Vite + Sigma.js" as Web
  }
  node "Backend container\nSpring Boot 4.0.6 / Java 21\nhost configured SERVER_PORT -> :8080" as BE {
    component "REST controllers" as REST
    component "Auth / OAuth / refresh sessions" as Auth
    component "Parser + async analysis scheduler" as Parser
    component "Graph + watcher + STOMP" as Graph
    component "Use-case diagram service" as UML
    component "MCP streamable HTTP /mcp" as MCP
    component "GraphRepository facade\nNeo4jGraphRepository owns Driver" as Repo
  }
  database "PostgreSQL 16.11\n127.0.0.1:5433 -> :5432" as PG
  database "Neo4j 5.26\n127.0.0.1:7687 / :7474" as N4J
  folder "Writable host mounts\n./projects -> /app/projects\n./uploads -> /app/uploads" as Mounts
}
cloud "Browser / CLI / AI client" as Clients
cloud "Optional external realtime/high-volume\nPostgreSQL-compatible storage" as Supa

Clients --> FE : HTTP
FE --> REST : REST / JSON
FE --> Graph : STOMP SockJS\n/ws/graph-updates
Clients --> REST : API
Clients --> MCP : streamable HTTP\nX-API-Key
REST --> Auth
REST --> Parser
REST --> Graph
REST --> UML
Parser --> Repo : graph persistence
Graph --> Repo : graph queries/updates
MCP --> Graph : tool-specific service calls
MCP --> Repo : only tools that require graph data
Auth --> PG : JDBC / Flyway
Repo --> N4J : raw Driver isolated here
Graph --> PG : ownership/runtime status
Mounts --> BE : writable bind mounts
BE ..> PG : depends_on healthy
BE ..> N4J : depends_on healthy
FE ..> BE : depends_on healthy
BE ..> Supa : optional when\nvibegraph.supabase.enabled=true

note bottom of BE
SOURCE: docker-compose.yml:1-190
SOURCE: WebSocketConfig.java:51-61
SOURCE: application.yaml:102-155
end note
@enduml
```

## PART 4: Auth/control-plane class view

```plantuml
@startuml
skinparam classAttributeIconSize 0
class AuthController {
  +register()
  +login()
  +refresh()
  +logout()
  +me()
}
class AuthService {
  +registerSession()
  +loginSession()
  +oauthLoginSession()
  +refreshSession()
}
class JwtService
class AuthCookieService
class RefreshSessionService {
  +issue()
  +rotate()
  +revoke()
  +purgeExpiredSessions()
}
class User <<JPA entity>>
class RefreshSession <<JPA entity>>
class ApiKey <<JPA entity>>
class ProjectOwnership <<JPA entity>>
class ProjectTrashService {
  +listTrash()
  +restore()
  +purge()
}
class AdminUserController
class AdminService
class AdminAuditController
class AuditService
class AuditLogEventStream
class AdminSecurityMonitorController
class AdminSecurityMonitorService
class AdminSecurityRequestEventStream

AuthController --> AuthService
AuthController --> AuthCookieService
AuthService --> JwtService
AuthService --> RefreshSessionService
RefreshSessionService --> RefreshSession
RefreshSessionService --> User
User ..> ApiKey : logical DB FK via userId
User ..> ProjectOwnership : logical DB FK via ownerId
ProjectTrashService --> ProjectOwnership
AdminUserController --> AdminService
AdminAuditController --> AuditService
AdminAuditController --> AuditLogEventStream
AdminSecurityMonitorController --> AdminSecurityMonitorService
AdminSecurityMonitorController --> AdminSecurityRequestEventStream
@enduml
```

## PART 5: Graph/parser/diagram/MCP class view

```plantuml
@startuml
skinparam classAttributeIconSize 0
class NodeData <<record>>
class EdgeData <<record>>
class ParseResult
interface ParserService
class ParserServiceImpl
interface GraphRepository
class Neo4jGraphRepository
class CachingGraphRepository
interface AnalyzeService
class AnalyzeServiceImpl
class ProjectAnalysisScheduler
interface FileWatcherService
class FileWatcherServiceImpl
class FileChangeBroadcaster
class GraphUpdateController
class GraphResponseFilter
class GraphPayloadGuard
class GraphController
class UseCaseDiagramServiceImpl
class UseCaseInferenceEngine
class UseCaseActorGuesser
class UseCaseDomainGuesser
class UseCaseEndpointRules
class McpServerConfig
interface ToolCallback
class MeteredToolCallback

ParseResult *-- NodeData
ParseResult *-- EdgeData
ParserService <|.. ParserServiceImpl
AnalyzeService <|.. AnalyzeServiceImpl
AnalyzeServiceImpl --> ParserService
AnalyzeServiceImpl --> GraphRepository
GraphRepository <|.. Neo4jGraphRepository
GraphRepository <|.. CachingGraphRepository
CachingGraphRepository --> Neo4jGraphRepository
ProjectAnalysisScheduler --> AnalyzeService
ProjectAnalysisScheduler --> GraphUpdateController
FileWatcherService <|.. FileWatcherServiceImpl
FileChangeBroadcaster --> FileWatcherService
FileChangeBroadcaster --> GraphRepository
FileChangeBroadcaster --> GraphUpdateController
GraphUpdateController --> GraphPayloadGuard
GraphController --> GraphResponseFilter
GraphController --> GraphPayloadGuard
UseCaseDiagramServiceImpl --> UseCaseInferenceEngine
UseCaseInferenceEngine --> UseCaseActorGuesser
UseCaseInferenceEngine --> UseCaseDomainGuesser
UseCaseInferenceEngine --> UseCaseEndpointRules
McpServerConfig --> MeteredToolCallback
ToolCallback <|.. MeteredToolCallback
MeteredToolCallback --> ToolCallback : delegate
@enduml
```

The class views are verified compact module views, not an exhaustive rendering of all 17,907
indexed symbols. `Plan`, `UserCreditBalance`, `CreditLedger`, `AuditLog`, `FeatureFlag`, `Role`,
`GraphService` and `ProjectService` remain in production code but are omitted from these compact
slices. Exact source locations and old-versus-current decisions are in the change notes.
