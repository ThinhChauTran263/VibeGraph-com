# Module: `graph/` — Knowledge Graph (Neo4j)

> **Vai trò:** Lưu trữ và truy vấn knowledge graph trong Neo4j. Nhận `ParseResult` từ `parser/`, persist nodes + edges, expose REST API để frontend lấy graph data, tính impact analysis (blast radius).

> **Dev phụ trách:** Dev 2 (Backend).

> **Sprint:** Sprint 1 (Neo4j models + REST API), Sprint 2 (incremental update + WebSocket), Sprint 3 (impact analysis + query optimization).

> **Phụ thuộc:** `common/` (Neo4jConfig, ApiResponse, exceptions), `parser/` (ParseResult).

---

## Mục tiêu module

1. Map nodes/edges từ `ParseResult` → Neo4j `@Node` entities (FR-02)
2. Persist với batch transaction (1 file = 1 transaction)
3. Incremental update: xóa nodes/edges cũ của file thay đổi, insert mới (FR-07)
4. Query graph qua Cypher: full graph, neighborhood (N hops), filter
5. Impact analysis: blast radius — node nào bị ảnh hưởng nếu sửa target
6. Push update qua WebSocket khi graph thay đổi
7. Query 3 hops < 500ms (NFR-01)

---

## Cấu trúc thư mục

```
graph/
├── controller/           # REST + WebSocket controllers
├── service/              # Interface
│   └── impl/             # Implementations
├── repository/           # Spring Data Neo4j repositories
├── node/                 # @Node entities
├── websocket/            # @MessageMapping handlers
└── dto/
    ├── request/
    └── response/
```

---

## Files & Specs

### Neo4j Node Entities (`node/`)

#### `node/ProjectNode.java`
**Mục tiêu:** Đại diện 1 project được register.

**Phải làm:**
- `@Node("Project")`, extends `BaseNode`
- Field: `String rootPath`, `String name`, `String description`, `Instant lastAnalyzedAt`, `Long fileCount`, `Long nodeCount`, `Long edgeCount`
- `@Relationship(type="CONTAINS_FILE", direction=OUTGOING) Set<FileNode> files`

**Đạt được khi:**
- [ ] CRUD project qua Neo4jRepository
- [ ] `findByRootPath(String)` query nhanh (cần index)

---

#### `node/FileNode.java`
**Mục tiêu:** Đại diện 1 file `.java`.

**Phải làm:**
- `@Node("File")`, extends `BaseNode`
- Field: `String checksum` (SHA-256), `Instant lastModified`, `Long sizeBytes`
- `@Relationship(type="DEFINES", direction=OUTGOING) Set<ClassNode> classes`
- `@Relationship(type="DEFINES", direction=OUTGOING) Set<InterfaceNode> interfaces`
- Index trên `filePath` (unique trong scope project)

**Đạt được khi:**
- [ ] Save file → tự link với classes inside
- [ ] `findByFilePath` < 50ms (có index)
- [ ] Khi file bị xóa khỏi project, xóa luôn FileNode + cascade nodes con

**Tham chiếu:** `architecture.md` §3, `task-breakdown.md` 5.10

---

#### `node/PackageNode.java`
**Mục tiêu:** Đại diện 1 Java package.

**Phải làm:**
- `@Node("Package")`, extends BaseNode
- Field: `String fullName` (com.example.controller)
- `@Relationship(type="CONTAINS", direction=OUTGOING) Set<ClassNode> classes`
- `@Relationship(type="CONTAINS_PACKAGE", direction=OUTGOING) Set<PackageNode> subPackages` (parent → child)

**Đạt được khi:**
- [ ] Tree query: lấy package con của `com.example` (FR-03 ExplorerPanel)
- [ ] Index trên `fullName`

---

#### `node/ClassNode.java`
**Mục tiêu:** Class node — quan trọng nhất.

**Phải làm:**
- `@Node("Class")`, extends BaseNode
- Field: `String visibility`, `Boolean isAbstract`, `Boolean isFinal`, `Boolean isStatic`, `Boolean isRecord`, `String enclosingClass` (cho inner class)
- Spring fields: `String springLayer` (CONTROLLER/SERVICE/REPOSITORY/COMPONENT/CONFIG), `List<String> springAnnotations`
- Relationships:
  - `EXTENDS` → ClassNode
  - `IMPLEMENTS` → InterfaceNode (nhiều)
  - `HAS_METHOD` → MethodNode
  - `HAS_FIELD` → FieldNode
  - `IMPORTS` → ClassNode/InterfaceNode
  - `INJECTS {via: "constructor"|"field"|"setter"}` → ClassNode
  - `ANNOTATED_BY {annotation: "@Service"}` → ClassNode (annotation type)
- Index trên `fullName`, `springLayer`

**Đạt được khi:**
- [ ] Save 1 class với 10 methods, 5 fields, 3 imports → 1 transaction
- [ ] Query "list all @RestController" qua `springLayer=CONTROLLER` < 100ms
- [ ] Cascade delete: xóa class → xóa methods/fields/edges liên quan

**Tham chiếu:** `architecture.md` §3, `requirements.md` FR-02

---

#### `node/InterfaceNode.java`
**Mục tiêu:** Interface node.

**Phải làm:**
- `@Node("Interface")`, extends BaseNode
- Field: `String visibility`, `Boolean isFunctional` (single abstract method)
- Relationships: `EXTENDS` → InterfaceNode, `HAS_METHOD` → MethodNode

**Đạt được khi:**
- [ ] Functional interface detect được (cho lambda hint)

---

#### `node/EnumNode.java`
**Mục tiêu:** Enum node.

**Phải làm:**
- `@Node("Enum")`, extends BaseNode
- Field: `String visibility`, `List<String> constants`
- Relationships: `IMPLEMENTS` → InterfaceNode, `HAS_METHOD` → MethodNode

---

#### `node/MethodNode.java`
**Mục tiêu:** Method/Constructor node.

**Phải làm:**
- `@Node("Method")`, extends BaseNode
- Field: `String visibility`, `Boolean isAbstract`, `Boolean isStatic`, `Boolean isFinal`, `Boolean isSynchronized`
- `String returnType`, `List<String> parameterTypes`, `List<String> parameterNames`, `List<String> throwsTypes`
- HTTP fields (cho endpoint methods): `String httpMethod`, `String routePath`
- Boolean flags: `isScheduled`, `isEventListener`, `String eventSource`
- Relationships:
  - `CALLS {lineNumber, confidence}` → MethodNode
  - `RETURNS` → ClassNode/InterfaceNode
  - `PARAMETER_TYPE` → ClassNode/InterfaceNode (nhiều, có index property)
  - `HANDLES_ROUTE` → RouteNode

**Đạt được khi:**
- [ ] Lưu method với 5 params, 2 throws → đúng schema
- [ ] CALLS edge có property confidence (1.0 hoặc 0.5)
- [ ] Method overload: 2 nodes khác fullName, không xung đột

**Tham chiếu:** `architecture.md` §3, `task-breakdown.md` 1.9

---

#### `node/FieldNode.java`
**Mục tiêu:** Field node.

**Phải làm:**
- `@Node("Field")`, extends BaseNode
- Field: `String visibility`, `Boolean isStatic`, `Boolean isFinal`, `String declaredType`, `Boolean isInjected`
- Relationships: `TYPE_OF` → ClassNode/InterfaceNode

---

#### `node/RouteNode.java`
**Mục tiêu:** HTTP endpoint node (cho Use Case diagram).

**Phải làm:**
- `@Node("Route")`, extends BaseNode
- Field: `String httpMethod` (GET/POST/PUT/DELETE/PATCH), `String routePath` (`/api/users/{id}`)
- `String handlerMethodFullName`, `List<String> middleware` (nếu có @Aspect/Filter)
- Index trên `routePath`, `httpMethod`

**Đạt được khi:**
- [ ] Mỗi `@GetMapping` → 1 RouteNode
- [ ] Query "all routes of /api/*" qua regex hoặc startsWith

**Tham chiếu:** `architecture.md` §3, `requirements.md` FR-04

---

### Repositories (`repository/`)

#### `repository/ProjectNodeRepository.java`
**Phải làm:**
- Extends `Neo4jRepository<ProjectNode, String>`
- `Optional<ProjectNode> findByRootPath(String rootPath)`
- `@Query("MATCH (p:Project {id: $id})-[:CONTAINS_FILE]->(f) RETURN count(f)")` `long countFiles(...)`

---

#### `repository/ClassNodeRepository.java`
**Phải làm:**
- Extends `Neo4jRepository<ClassNode, String>`
- `List<ClassNode> findBySpringLayer(String layer)`
- `Optional<ClassNode> findByFullName(String fullName)`
- `@Query` để xóa cascade: methods/fields của 1 class

---

#### `repository/MethodNodeRepository.java`
**Phải làm:**
- `List<MethodNode> findByHttpMethodNotNull()` — cho diagram module
- `Optional<MethodNode> findByFullName(String fullName)`
- `@Query` Cypher trace CALLS chain (cho sequence diagram + impact)

---

#### `repository/FileNodeRepository.java`
**Phải làm:**
- `Optional<FileNode> findByFilePath(String filePath)`
- `@Query` xóa file + cascade tất cả nodes inside file (DEFINES relationship)
- `@Query` để incremental update: `MATCH (f:File {filePath: $path}) DETACH DELETE f`

**Đạt được khi:**
- [ ] DETACH DELETE 1 file < 200ms
- [ ] Không leak orphan nodes

**Tham chiếu:** `architecture.md` §4.2, `task-breakdown.md` 5.9

---

#### `repository/GraphRepository.java` (custom Cypher)
**Mục tiêu:** Query phức tạp không cover được bởi derived methods.

**Phải làm:**
- Custom interface, implement bằng `Neo4jClient` hoặc `Neo4jTemplate`
- Method `GraphData getFullGraph(String projectId, GraphFilter filter)` — paginated
- Method `GraphData getNeighbors(String nodeId, int hops)` — N-hop traversal
- Method `List<NodeDto> getIncomingEdges(String nodeId)` — cho NodeDetailPanel
- Method `List<NodeDto> getOutgoingEdges(String nodeId)`
- Method `List<NodeDto> impactAnalysis(String nodeId, int maxDepth)` — upstream callers

**Cypher examples:**
```cypher
// Neighbors 2 hops
MATCH (n {id: $nodeId})-[r*1..2]-(m) RETURN n, r, m

// Impact (upstream callers)
MATCH (n {id: $nodeId})<-[r:CALLS|HAS_METHOD|EXTENDS|IMPLEMENTS*1..3]-(caller)
RETURN caller, length(r) AS depth
```

**Đạt được khi:**
- [ ] `getFullGraph` paginated, support filter by node type / package / springLayer
- [ ] `getNeighbors` 3 hops < 500ms (NFR-01)
- [ ] `impactAnalysis` trả về node với depth (1=WILL BREAK, 2=LIKELY, 3=MAYBE)

**Tham chiếu:** `architecture.md` §5, `requirements.md` FR-09, NFR-01

---

### Services (`service/`)

#### `service/ProjectService.java` (interface)
**Phải có:**
- `ProjectNode createProject(CreateProjectRequest req)` — validate path tồn tại
- `ProjectNode findById(String id)` — throw `ProjectNotFoundException`
- `List<ProjectNode> findAll()`
- `void deleteProject(String id)` — cascade tất cả file/class/method...

#### `service/impl/ProjectServiceImpl.java`
**Phải làm:**
- `@Service`, `@Transactional`
- Validate: path tồn tại, là directory, đọc được
- Initialize symbol solver cho project (gọi `parser.configureSymbolSolver`)

**Đạt được khi:**
- [ ] `POST /api/projects` với path không tồn tại → 400
- [ ] Tạo project trùng `rootPath` → return existing thay vì duplicate

---

#### `service/AnalyzeService.java` (interface)
**Phải có:**
- `AnalysisResult analyzeProject(String projectId)` — full analysis
- `AnalysisResult analyzeFile(String projectId, Path filePath)` — incremental
- `AnalysisStatus getStatus(String projectId)` — for progress polling

#### `service/impl/AnalyzeServiceImpl.java`
**Phải làm:**
- `@Service`, `@Async` cho `analyzeProject` (chạy background, push status qua WebSocket)
- Logic full analysis:
  1. Scan tất cả `.java` files qua `FileUtils.findJavaFiles`
  2. Parallel parse qua `parser.parseProject`
  3. Map `ParseResult` → Neo4j entities (ClassNode, MethodNode...)
  4. Batch save trong transaction (1 file = 1 tx)
  5. Build CALLS edges (sau khi tất cả nodes có id) — cần 2-pass
  6. Update ProjectNode counters
  7. Push WebSocket: `{type: "FULL_UPDATE", projectId, stats}`
- Logic incremental:
  1. Compute checksum file mới
  2. So sánh với `FileNode.checksum` cũ → skip nếu trùng
  3. DETACH DELETE FileNode cũ + tất cả nodes inside
  4. Parse + insert mới
  5. Push `{type: "INCREMENTAL", affectedFiles: [...], diff}`

**Đạt được khi:**
- [ ] Full analysis 500 files < 30s (NFR-01)
- [ ] Incremental 1 file < 3s (FR-07)
- [ ] Không lost data khi crash giữa chừng (transaction rollback)
- [ ] Status endpoint trả progress: `{status: "ANALYZING", progress: 245, total: 500}`

**Tham chiếu:** `architecture.md` §4, `task-breakdown.md` 2.6, 2.11, 5.9

---

#### `service/GraphService.java` (interface)
**Phải có:**
- `GraphDataResponse getGraph(String projectId, GraphFilterRequest filter)`
- `GraphDataResponse getNeighbors(String projectId, String nodeId, int hops)`
- `NodeDetailResponse getNodeDetail(String nodeId)` — INCOMING + OUTGOING

#### `service/impl/GraphServiceImpl.java`
**Phải làm:**
- Wrap GraphRepository, convert entities → DTOs (NodeDto, EdgeDto)
- Apply filter: nodeTypes, edgeTypes, packages, springLayers
- Pagination qua `PaginationRequest`

**Đạt được khi:**
- [ ] Trả về `GraphDataResponse{nodes, edges}` đúng shape cho Sigma.js
- [ ] Filter by `springLayer=CONTROLLER` chỉ trả Controller classes
- [ ] Cache layer cho query lặp (Caffeine, optional)

**Tham chiếu:** `requirements.md` FR-03, FR-09

---

#### `service/ImpactService.java` (interface)
**Phải có:**
- `ImpactAnalysisResponse analyzeImpact(String nodeId, int maxDepth)`

#### `service/impl/ImpactServiceImpl.java`
**Phải làm:**
- Gọi `GraphRepository.impactAnalysis(nodeId, maxDepth)`
- Group result theo depth: `d=1` (WILL BREAK), `d=2` (LIKELY), `d=3` (MAYBE)
- Risk level: CRITICAL nếu d=1 > 10, HIGH nếu > 5, MEDIUM nếu > 0
- Affected processes: trace CALLS chain để tìm Controller endpoints bị ảnh hưởng

**Đạt được khi:**
- [ ] Trả về cấu trúc `{risk, summary, byDepth: {d=1: [...], d=2: [...]}, affectedRoutes: [...]}`
- [ ] MCP tool `get_impact_analysis` reuse service này

**Tham chiếu:** `requirements.md` FR-11, `architecture.md` §9.3 (MCP tool), CLAUDE.md (impact risk levels)

---

### Controllers (`controller/`)

#### `controller/ProjectController.java`
**Phải có endpoints:**
- `POST /api/projects` — body: `CreateProjectRequest{rootPath, name}`
- `GET /api/projects` — list all
- `GET /api/projects/{id}` — detail
- `DELETE /api/projects/{id}` — cascade delete
- `POST /api/projects/{id}/analyze` — trigger full analysis (async, return 202)
- `GET /api/projects/{id}/status` — analysis progress

**Đạt được khi:**
- [ ] Validation: `@Valid` request, return 400 với field errors
- [ ] Trả `ApiResponse<T>` chuẩn

---

#### `controller/GraphController.java`
**Phải có endpoints:**
- `GET /api/projects/{id}/graph` — full graph (paginated)
- `GET /api/projects/{id}/graph/nodes?type=Class&package=com.x` — filtered
- `GET /api/projects/{id}/graph/neighbors/{nodeId}?hops=2`
- `GET /api/projects/{id}/nodes/{nodeId}` — detail with INCOMING + OUTGOING

**Đạt được khi:**
- [ ] Response time < 500ms cho graph 5000 nodes (NFR-01)
- [ ] Frontend Sigma.js render được response trực tiếp

**Tham chiếu:** `requirements.md` FR-09

---

#### `controller/ImpactController.java`
**Phải có endpoint:**
- `GET /api/projects/{id}/impact/{nodeId}?maxDepth=3`

**Đạt được khi:**
- [ ] Response < 1s cho graph 50000 nodes (NFR-01, NFR-02)

---

### WebSocket (`websocket/`)

#### `websocket/GraphUpdateController.java`
**Phải làm:**
- `@Controller` + `@MessageMapping` cho client-to-server (subscription)
- Inject `SimpMessagingTemplate` để server push qua `/topic/projects/{id}/updates`
- Method `notifyGraphUpdate(String projectId, GraphUpdateEvent event)` được gọi từ AnalyzeService

**Topic schema:**
- `/topic/projects/{id}/updates` — payload `{type: "FULL_UPDATE"|"INCREMENTAL", affectedFiles, affectedNodes, affectedEdges, timestamp}`
- `/topic/projects/{id}/status` — payload `{status, progress, total, message}`

**Đạt được khi:**
- [ ] Frontend subscribe và nhận update < 200ms latency (NFR-01)
- [ ] Multiple clients cùng nhận được message

**Tham chiếu:** `architecture.md` §5, `requirements.md` FR-07

---

#### `websocket/WebSocketEventListener.java`
**Phải làm:**
- `@EventListener` cho `SessionConnectedEvent`, `SessionDisconnectEvent`
- Log connection count, dùng cho health/observability
- (Optional) Track per-project subscription count

---

### DTOs

#### `dto/request/CreateProjectRequest.java`
- Record `{String rootPath, String name, String description}`
- `@NotBlank` cho rootPath, name
- Custom validator: path tồn tại + là directory

#### `dto/request/AnalyzeRequest.java`
- Record `{Boolean force}` — force=true để bypass checksum cache

#### `dto/request/GraphFilterRequest.java`
- Record `{Set<String> nodeTypes, Set<String> edgeTypes, Set<String> packages, Set<String> springLayers, String search, Integer maxNodes}`
- Default `maxNodes=10000` (avoid frontend lag)

#### `dto/response/GraphDataResponse.java`
- Record `{List<NodeDto> nodes, List<EdgeDto> edges, GraphStats stats}`
- `GraphStats{totalNodes, totalEdges, nodeTypeCounts: Map<String,Long>}`

#### `dto/response/NodeDto.java`
- Record `{String id, String type, String name, String fullName, String filePath, Integer lineNumber, Map<String,Object> properties}`
- `properties` chứa springLayer, visibility, returnType...

#### `dto/response/EdgeDto.java`
- Record `{String id, String type, String source, String target, Map<String,Object> properties}`
- `type` ∈ {EXTENDS, IMPLEMENTS, CALLS, HAS_METHOD, ...}

#### `dto/response/NodeDetailResponse.java`
- Record `{NodeDto node, List<ConnectionDto> incoming, List<ConnectionDto> outgoing}`
- `ConnectionDto{NodeDto otherNode, EdgeDto edge, String direction}`

**Đạt được khi:**
- [ ] Frontend Side Panel (NodeDetailPanel) render được INCOMING + OUTGOING (FR-03)

#### `dto/response/ProjectResponse.java`
- Record `{String id, String name, String rootPath, Instant lastAnalyzedAt, Long fileCount, Long nodeCount, Long edgeCount, AnalysisStatus status}`

#### `dto/response/ImpactAnalysisResponse.java`
- Record `{String targetNodeId, String risk, ImpactSummary summary, Map<Integer, List<NodeDto>> byDepth, List<RouteDto> affectedRoutes}`

---

## Definition of Done cho module graph/

- [ ] Tất cả `@Node` map đúng schema architecture.md §3
- [ ] Index Cypher constraints được tạo (FullName UNIQUE cho Class/Method, FilePath UNIQUE cho File trong project)
- [ ] Full analysis Spring Boot demo project (~50 classes) chạy thành công
- [ ] Incremental update verified: sửa 1 file → đúng nodes/edges thay đổi (verify qua Cypher query)
- [ ] WebSocket push verified: frontend nhận được message khi analyze xong
- [ ] Coverage > 70% (testing.md) — tập trung vào service impl
- [ ] Query 3 hops < 500ms (NFR-01)
- [ ] Swagger/OpenAPI docs cho tất cả endpoints (task 2.19)

---

## Lưu ý cross-module

- KHÔNG expose Neo4j entity ra response — luôn convert sang DTO
- Cascade delete **DETACH DELETE** để xóa cả relationships, tránh constraint violation
- Khi incremental update, lock theo project (avoid race condition khi 2 file cùng được parse)
- WebSocket message format **MUST** đồng bộ với frontend `useWebSocket` composable — đổi schema phải coordinate với Dev 3
- MCP module gọi `ImpactService` và `GraphService` để build context — KHÔNG duplicate logic ở MCP
- Diagram module gọi `MethodNodeRepository` để query Routes/Controller methods — KHÔNG truy cập Neo4j trực tiếp
