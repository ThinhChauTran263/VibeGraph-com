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
