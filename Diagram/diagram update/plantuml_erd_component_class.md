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
  password_hash : VARCHAR?
  display_name : VARCHAR?
  avatar_url : VARCHAR?
  email_verified : BOOLEAN
  role : VARCHAR
  quota_bytes : BIGINT
  used_bytes : BIGINT
  deactivated : BOOLEAN
  deactivated_at : TIMESTAMPTZ?
  deactivation_reason : VARCHAR?
  deactivation_reason_safe : VARCHAR?
  created_at : TIMESTAMPTZ
  updated_at : TIMESTAMPTZ
}
entity user_identities {
  * id : UUID <<PK>>
  user_id : UUID <<FK>>
  provider : VARCHAR
  provider_user_id : VARCHAR
  email : VARCHAR?
  created_at : TIMESTAMPTZ
}
entity projects {
  * project_id : VARCHAR <<PK>>
  owner_id : UUID <<FK>>
  name : VARCHAR
  source_type : VARCHAR
  size_bytes : BIGINT
  status : VARCHAR
  deleted_at : TIMESTAMPTZ?
  created_at : TIMESTAMPTZ
  updated_at : TIMESTAMPTZ
}
entity api_keys {
  * id : UUID <<PK>>
  user_id : UUID <<FK>>
  project_id : VARCHAR? <<FK>>
  key_hash : VARCHAR <<UNIQUE>>
  key_prefix : VARCHAR
  name : VARCHAR?
  created_at : TIMESTAMPTZ
  last_used_at : TIMESTAMPTZ?
  expires_at : TIMESTAMPTZ?
  disabled_at : TIMESTAMPTZ?
  deleted_at : TIMESTAMPTZ?
  disabled_by : VARCHAR?
  disabled_reason : VARCHAR?
  locked_by : VARCHAR?
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
  storage_quota_override_bytes : BIGINT?
  credit_quota_override : INTEGER?
  api_key_creation_disabled : BOOLEAN
  blocked_at : TIMESTAMPTZ?
  blocked_reason : VARCHAR?
  blocked_reason_safe : VARCHAR?
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
  project_id : VARCHAR? <<FK>>
  balance_id : UUID? <<FK>>
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
  description : VARCHAR?
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
  starts_at : TIMESTAMPTZ?
  ends_at : TIMESTAMPTZ?
  dismissible : BOOLEAN
  active : BOOLEAN
  created_by_user_id : UUID? <<FK>>
  created_at : TIMESTAMPTZ
  updated_at : TIMESTAMPTZ
}
entity user_notifications {
  * id : UUID <<PK>>
  user_id : UUID <<FK>>
  announcement_id : UUID <<FK>>
  read_at : TIMESTAMPTZ?
  dismissed_at : TIMESTAMPTZ?
  created_at : TIMESTAMPTZ
}
entity feedback_reports {
  * id : UUID <<PK>>
  user_id : UUID? <<FK>>
  status : VARCHAR
  category : VARCHAR
  title : VARCHAR
  delete_after : TIMESTAMPTZ?
  created_at : TIMESTAMPTZ
  closed_at : TIMESTAMPTZ?
}
entity feedback_messages {
  * id : UUID <<PK>>
  report_id : UUID <<FK>>
  sender_user_id : UUID? <<FK>>
  sender_role : VARCHAR
  body : TEXT
  created_at : TIMESTAMPTZ
}
entity security_events {
  * id : UUID <<PK>>
  event_type : VARCHAR
  severity : VARCHAR
  subject_user_id : UUID? <<FK>>
  api_key_ref : VARCHAR?
  source : VARCHAR?
  description : VARCHAR?
  created_at : TIMESTAMPTZ
}
entity request_events {
  * id : UUID <<PK>>
  user_id : UUID? <<FK>>
  api_key_ref : VARCHAR?
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
  expires_at : TIMESTAMPTZ?
  created_by : UUID? <<FK>>
  active : BOOLEAN
  created_at : TIMESTAMPTZ
  updated_at : TIMESTAMPTZ
}
entity audit_logs {
  * id : UUID <<PK>>
  action : VARCHAR
  actor_user_id : UUID? <<logical ref; no FK after V15>>
  target_user_id : UUID? <<logical ref; no FK after V15>>
  target_type : VARCHAR?
  target_id : VARCHAR?
  outcome : VARCHAR
  ip_address : VARCHAR?
  details : VARCHAR
  created_at : TIMESTAMPTZ
}
entity audit_retention_settings {
  * id : SMALLINT <<PK>>
  retention_days : INTEGER
  updated_by : UUID? <<FK>>
  updated_at : TIMESTAMPTZ
}
entity refresh_sessions {
  * id : UUID <<PK>>
  user_id : UUID <<FK>>
  family_id : UUID
  token_hash : VARCHAR <<UNIQUE>>
  expires_at : TIMESTAMPTZ
  last_used_at : TIMESTAMPTZ?
  revoked_at : TIMESTAMPTZ?
  revoke_reason : VARCHAR?
  replaced_by_id : UUID?
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
  createdAt : timestamp?
  lastAnalyzedAt : timestamp?
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
