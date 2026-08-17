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
