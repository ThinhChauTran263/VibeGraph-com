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
