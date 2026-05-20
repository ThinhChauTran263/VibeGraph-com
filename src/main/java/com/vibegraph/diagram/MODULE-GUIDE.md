# Module: `diagram/` — UML Diagram Generators

> **Vai trò:** Sinh code Mermaid.js cho 3 loại diagram (Use Case / Class / Sequence) từ knowledge graph trong Neo4j. Frontend chỉ việc render Mermaid string trả về.

> **Dev phụ trách:** Dev 4 (Frontend) — phối hợp Dev 2 (Backend) cho Cypher query.

> **Sprint:** Sprint 2 (Use Case + Class diagram), Sprint 3 (Sequence diagram).

> **Phụ thuộc:** `common/`, `graph/` (Neo4j repository, không tự query Neo4j trực tiếp).

---

## Mục tiêu module

1. Use Case Diagram: detect actors (HTTP/Scheduler/MQ) + use cases (Controller methods) + relationships (FR-04)
2. Class Diagram: classes + fields + methods + EXTENDS/IMPLEMENTS/INJECTS theo package (FR-05)
3. Sequence Diagram: trace CALLS chain từ Controller method → Service → Repository (FR-06)
4. Output Mermaid syntax string, KHÔNG render hình ảnh server-side
5. Latency < 1s cho diagram trung bình
6. Auto-update qua WebSocket khi graph thay đổi (FR-07)

---

## Cấu trúc thư mục

```
diagram/
├── controller/           # REST endpoints
├── service/              # Interfaces
│   └── impl/             # Mermaid generators
├── repository/           # Custom Cypher cho diagram queries
├── node/                 # Internal model (DiagramData)
└── dto/
    ├── request/
    └── response/
```

---

## Files & Specs

### `repository/DiagramQueryRepository.java`
**Mục tiêu:** Cypher queries chuyên cho diagram, tách khỏi `GraphRepository` để tránh module phình to.

**Phải làm:**
- Custom interface, implement bằng `Neo4jClient`
- Method `List<ActorData> findActors(String projectId)`:
  - Cypher: tìm class có `springLayer=CONTROLLER` (HTTP Client actor)
  - Cypher: tìm method có `isScheduled=true` (System Scheduler actor)
  - Cypher: tìm method có `eventSource IS NOT NULL` (Message Queue actor)
- Method `List<UseCaseData> findUseCases(String projectId)`:
  - Cypher: `MATCH (m:Method)-[:HANDLES_ROUTE]->(r:Route) RETURN m, r`
- Method `List<IncludeRelation> findIncludes(String projectId)`:
  - Cypher: tìm Service được gọi bởi >= 2 Controller methods → `<<include>>`
- Method `List<ClassDiagramData> getClassDiagramData(String projectId, String packageFilter)`
- Method `List<SequenceStep> traceSequence(String entryMethodId, int maxDepth)`:
  - Recursive Cypher: `MATCH path = (entry {id: $id})-[:CALLS*1..$depth]->(target) RETURN path`

**Đạt được khi:**
- [ ] Query trả về data đủ cho generator, không phải gọi nhiều round-trip
- [ ] Sequence trace 5 hops < 500ms
- [ ] Class diagram filter by package trả đúng subset

**Tham chiếu:** `architecture.md` §3 (Cypher patterns), `requirements.md` FR-04, FR-05, FR-06

---

### `service/UseCaseDiagramService.java` (interface)
**Phải có:**
- `DiagramResponse generate(String projectId)`

### `service/impl/UseCaseDiagramServiceImpl.java`
**Mục tiêu:** Generate Mermaid `flowchart LR` cho Use Case diagram.

**Phải làm:**
- `@Service`, inject `DiagramQueryRepository`, `MermaidGeneratorService`
- Logic:
  1. Lấy actors từ `findActors`: gom thành 3 nhóm chuẩn (HTTPClient, SystemScheduler, MessageQueue)
  2. Lấy use cases từ `findUseCases`: mỗi route = 1 use case, name = `{HTTP_METHOD} {path}` (ví dụ `POST /api/users`)
  3. Phát hiện `<<include>>`: service method được gọi bởi >= 2 controllers
  4. Phát hiện `<<extend>>`: optional method gọi từ try/catch hoặc behind validation (heuristic: method có name chứa "validate", "notify", "log")
  5. Build Mermaid string qua `MermaidGeneratorService.useCase(...)`

**Mermaid output mẫu:**
```
flowchart LR
    HTTPClient((HTTP Client))
    Scheduler((System Scheduler))
    HTTPClient --> UC1[POST /api/users]
    HTTPClient --> UC2[GET /api/users/{id}]
    Scheduler --> UC3[Cleanup expired sessions]
    UC1 -.->|<<include>>| UC4[Validate user input]
```

**Đạt được khi:**
- [ ] Sample Spring Boot project: render đúng actors + use cases (FR-04 acceptance)
- [ ] No actor → fallback: 1 generic Actor "User"
- [ ] Coverage > 70%

**Tham chiếu:** `requirements.md` FR-04, `task-breakdown.md` 2.13, 4.7

---

### `service/ClassDiagramService.java` (interface)
**Phải có:**
- `DiagramResponse generate(String projectId, String packageFilter)`

### `service/impl/ClassDiagramServiceImpl.java`
**Mục tiêu:** Generate Mermaid `classDiagram`.

**Phải làm:**
- `@Service`
- Logic:
  1. `getClassDiagramData(projectId, packageFilter)`: trả classes + fields + methods + relationships trong package
  2. Nếu `packageFilter=null` → trả full project (cảnh báo nếu > 50 classes, frontend nên prompt user filter)
  3. Visibility marker: `+` public, `-` private, `#` protected, `~` package
  4. Methods: `+methodName(paramType): returnType`
  5. Fields: `-fieldName: declaredType`
  6. Relationships:
     - EXTENDS → `<|--` (inheritance)
     - IMPLEMENTS → `<|..` (realization)
     - INJECTS → `o--` (aggregation, có cardinality 1)
     - TYPE_OF (field) → `-->` (association)
  7. Build qua `MermaidGeneratorService.classDiagram(...)`

**Mermaid output mẫu:**
```
classDiagram
    class UserController {
        -UserService userService
        +createUser(CreateUserDto): UserResponseDto
        +getUserById(Long): UserResponseDto
    }
    class UserService {
        -UserRepository repo
        +save(User): User
    }
    UserController o-- UserService : injects
    UserService o-- UserRepository : injects
```

**Đạt được khi:**
- [ ] Render đúng cho Spring Boot demo project
- [ ] Filter package `com.example.controller` chỉ trả Controller classes + dependencies
- [ ] Long names truncated nếu > 80 chars (avoid Mermaid render issue)

**Tham chiếu:** `requirements.md` FR-05, `task-breakdown.md` 2.14, 4.8

---

### `service/SequenceDiagramService.java` (interface)
**Phải có:**
- `DiagramResponse generate(SequenceDiagramRequest req)`

### `service/impl/SequenceDiagramServiceImpl.java`
**Mục tiêu:** Generate Mermaid `sequenceDiagram` từ entry point method.

**Phải làm:**
- `@Service`
- Logic:
  1. Validate `entryMethodId` tồn tại, throw `NodeNotFoundException` nếu không
  2. `traceSequence(entryMethodId, maxDepth)` → list các step `{caller, callee, lineNumber, paramSnippet}`
  3. Group theo participant (class chứa method) — mỗi class = 1 participant
  4. Order theo DFS từ entry, tránh duplicate edge nếu cùng caller-callee đã xuất hiện
  5. Default `maxDepth=5` (FR-06)
  6. Build qua `MermaidGeneratorService.sequence(...)`

**Mermaid output mẫu:**
```
sequenceDiagram
    participant UserController
    participant UserService
    participant UserRepository
    UserController->>UserService: save(user)
    UserService->>UserRepository: insert(user)
    UserRepository-->>UserService: User
    UserService-->>UserController: UserResponseDto
```

**Đạt được khi:**
- [ ] Trace từ Controller method → đầy đủ chain Service → Repository
- [ ] Recursive call (cycle) bị break, không infinite loop
- [ ] Render < 1s cho trace 5 hops

**Tham chiếu:** `requirements.md` FR-06, `task-breakdown.md` 2.16, 4.13

---

### `service/MermaidGeneratorService.java` (interface)
**Phải có:**
- `String useCase(List<ActorData> actors, List<UseCaseData> useCases, List<IncludeRelation> includes)`
- `String classDiagram(List<ClassDiagramData> data)`
- `String sequence(List<SequenceStep> steps)`

### `service/impl/MermaidGeneratorServiceImpl.java`
**Mục tiêu:** Pure formatter — Java string builder cho Mermaid syntax.

**Phải làm:**
- `@Service`, KHÔNG inject repository (pure logic)
- Escape ký tự đặc biệt trong tên (Mermaid không cho phép `<`, `>`, `(`, `)` trong label trực tiếp)
- Sanitize node ID: thay `.` `$` bằng `_` (Mermaid không hỗ trợ dot trong ID)
- Wrap label trong `["..."]` nếu chứa space
- Build với `StringBuilder`, dùng `\n` linebreak
- Validate output trước khi return: count node count, throw nếu > 200 (Mermaid render lag)

**Đạt được khi:**
- [ ] Output luôn là valid Mermaid syntax (verify bằng test với Mermaid CLI)
- [ ] Các ký tự đặc biệt trong tên class/method được escape
- [ ] Coverage > 80% (logic thuần, dễ test)

**Tham chiếu:** `requirements.md` FR-04, FR-05, FR-06

---

### `controller/DiagramController.java`
**Phải có endpoints:**
- `GET /api/projects/{id}/diagrams/usecase` → `DiagramResponse`
- `GET /api/projects/{id}/diagrams/class?package=com.example.controller` → `DiagramResponse`
- `GET /api/projects/{id}/diagrams/sequence?entry={methodId}&maxDepth=5` → `DiagramResponse`
- (Optional) `GET /api/projects/{id}/diagrams/sequence/entries` — list các Controller methods (frontend dropdown)

**Đạt được khi:**
- [ ] Response time < 1s
- [ ] Cache layer (Caffeine, key = projectId + diagram type + params): TTL 30s
- [ ] Cache invalidate khi WebSocket nhận FULL_UPDATE/INCREMENTAL

**Tham chiếu:** `requirements.md` FR-04, FR-05, FR-06; `architecture.md` §5

---

### `node/DiagramData.java`
**Mục tiêu:** Internal models — sealed interface chứa các record cho từng diagram type.

**Phải làm:**
- Sealed interface `DiagramData permits ActorData, UseCaseData, IncludeRelation, ClassDiagramData, SequenceStep`
- `ActorData(String type, String name, String source)` — type: HTTP_CLIENT / SCHEDULER / MQ
- `UseCaseData(String id, String name, String httpMethod, String path)`
- `IncludeRelation(String fromUseCase, String toUseCase, String type)` — type: include / extend
- `ClassDiagramData(NodeDto classNode, List<NodeDto> fields, List<NodeDto> methods, List<EdgeDto> relations)`
- `SequenceStep(int order, String callerId, String callerName, String calleeId, String calleeName, String methodSignature, Integer lineNumber, String returnType)`

**Đạt được khi:**
- [ ] Không leak ra response (chỉ dùng internal trong service impl)
- [ ] Java records (immutable)

---

### DTOs

#### `dto/request/SequenceDiagramRequest.java`
- Record `{String projectId, String entryMethodId, Integer maxDepth, Boolean includeReturns}`
- Validation: `@NotBlank entryMethodId`, `@Min(1) @Max(10) maxDepth` (default 5)

#### `dto/response/DiagramResponse.java`
- Record `{String diagramType, String mermaidSyntax, DiagramMetadata metadata, Instant generatedAt}`
- `DiagramMetadata{Integer nodeCount, Integer edgeCount, List<String> warnings}`
- `warnings`: ví dụ "Diagram has 200+ nodes, consider filtering"

**Đạt được khi:**
- [ ] Frontend chỉ cần `mermaidSyntax` để render
- [ ] Wrap trong `ApiResponse<DiagramResponse>` chuẩn

#### `dto/response/UseCaseResponse.java`
- (Optional) Specialized response chứa thêm `actors[]`, `useCases[]` nếu frontend muốn hiển thị legend riêng. Có thể nhập vào DiagramResponse và bỏ file này nếu thấy thừa.

---

## Definition of Done cho module diagram/

- [ ] 3 endpoints (usecase, class, sequence) hoạt động trên Spring Boot demo project
- [ ] Mermaid output verified bằng test với Mermaid CLI hoặc render trên frontend
- [ ] Cache layer hoạt động: lần 2 gọi cùng params < 50ms
- [ ] WebSocket invalidate cache khi graph update
- [ ] Coverage > 70% (đặc biệt MermaidGeneratorService > 80%)
- [ ] Sequence diagram handle recursive call gracefully (cycle detection)
- [ ] Use Case diagram detect đúng ≥ 3 loại actor cho demo project có @Controller + @Scheduled

---

## Lưu ý cross-module

- KHÔNG gọi Neo4jRepository trực tiếp từ service — phải qua `DiagramQueryRepository` (tách concern)
- KHÔNG render hình ảnh server-side (PNG/SVG) — frontend Mermaid render. Server chỉ trả syntax string
- Cache invalidate listener phải subscribe `GraphUpdateEvent` từ graph module (Spring `@EventListener`)
- MCP module có thể reuse `DiagramService` để embed Mermaid vào context response (`ArchitectureContextResponse.classDiagram`)
- Khi user filter package không tồn tại → trả empty Mermaid `classDiagram` với warning, không 404
- Sequence diagram entry point bắt buộc là method có `springLayer=CONTROLLER` hoặc `isScheduled=true` — validate từ controller, return 400 nếu sai
