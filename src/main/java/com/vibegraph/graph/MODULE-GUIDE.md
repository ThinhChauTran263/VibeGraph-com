# Module: graph

## Mục đích
Module quản lý knowledge graph trong Neo4j: lưu trữ nodes/edges, cung cấp REST API để query graph, xử lý WebSocket realtime updates.

## Cấu trúc

```
graph/
├── controller/
│   ├── ProjectController.java    — POST /api/projects, GET /api/projects
│   ├── GraphController.java      — GET /api/projects/{id}/graph, /graph/impact (3 profiles)
│   ├── ImportController.java     — POST /api/projects/import-archive, /import-github
│   └── LocalProjectController.java — POST /api/projects/import-local, GET /api/projects/browse
├── service/
│   ├── ProjectService.java       — Interface: project CRUD
│   ├── GraphService.java         — Interface: graph query operations (incl. impact analysis)
│   ├── AnalyzeService.java       — Interface: trigger analysis pipeline
│   ├── TarballImportService.java — Interface: GitHub tarball stream
│   └── impl/
│       ├── ProjectServiceImpl.java
│       ├── GraphServiceImpl.java
│       ├── AnalyzeServiceImpl.java
│       └── TarballImportServiceImpl.java
├── repository/
│   ├── GraphRepository.java        — INTERFACE (storage abstraction)
│   └── impl/
│       └── neo4j/
│           ├── Neo4jGraphRepository.java — raw Neo4j Java Driver impl of GraphRepository
│           └── GraphSchema.java          — allow-list label/edge-type + validate Cypher key
├── websocket/
│   └── GraphUpdateController.java  — @MessageMapping for STOMP
└── dto/
    ├── request/
    │   ├── CreateProjectRequest.java  — {name, path, description}
    │   ├── AnalyzeRequest.java        — {projectId, incremental}
    │   ├── GithubImportRequest.java   — {url} (MỚI)
    │   └── GraphFilterRequest.java    — {nodeTypes[], packages[], layers[]}
    └── response/
        ├── ProjectResponse.java
        ├── GraphDataResponse.java     — {nodes[], edges[], stats}
        ├── NodeDto.java               — Flattened node for frontend
        ├── EdgeDto.java               — {source, target, type, properties}
        ├── NodeDetailResponse.java    — {node, incoming[], outgoing[]}
        └── ImpactAnalysisResponse.java — {affectedNodes[], risk, depth}
```

## Trạng thái hiện thực (đối soát code thực tế)

- ✅ Implemented: `Neo4jGraphRepository.upsertProject` / `upsertNodes` / `upsertEdges` / `getFullGraph` / `searchNodes`; `ProjectController` (create/list/get/delete/analyze); `GraphController` (full graph + neighbors + impact); `LocalProjectController` (import-local/browse, fail-closed); `ImportController` (import-archive + import-github); `ArchiveImportServiceImpl`, `TarballImportServiceImpl`, `AnalyzeServiceImpl`, `GraphServiceImpl`.
- ✅ Impact analysis khả dụng qua `GET /api/projects/{id}/graph/impact` trên `GraphController` (delegate `GraphService.getImpactAnalysis`). Không có controller/service Impact riêng — đã gộp vào GraphController/GraphService.

> Các checkbox `[ ]` ở dưới là đặc tả mục tiêu MVP, không phải trạng thái đã xong.

## Yêu cầu chức năng

### ProjectController
- [ ] `POST /api/projects`: Register project {name, path} → ProjectResponse
- [ ] `GET /api/projects`: List all projects → List<ProjectResponse>
- [ ] `GET /api/projects/{id}`: Get project detail
- [ ] `DELETE /api/projects/{id}`: Remove project và tất cả nodes liên quan
- [ ] `POST /api/projects/{id}/analyze`: Trigger full/incremental analysis

### ImportController (MỚI — GitHub Tarball Stream)
- [ ] `POST /api/projects/import-github`: Import từ GitHub URL
  - Request: `{url: "https://github.com/owner/repo"}`
  - Response: `{projectId, status: "ANALYZING"}` (202 Accepted)
  - Pre-flight: validate public repo, size < 100MB
  - Stream tarball qua commons-compress (không lưu disk)
  - Parse in-memory, push progress qua WebSocket

### GraphController
- [ ] `GET /api/projects/{id}/graph`: Full graph (paginated) → GraphDataResponse
- [ ] `GET /api/projects/{id}/graph?filter=...`: Filtered graph (by nodeTypes, packages, layers)
- [ ] `GET /api/projects/{id}/search?q=...`: Full-text search nodes by name

### Impact analysis (trên GraphController/GraphService)
- [x] `GET /api/projects/{id}/graph/impact?nodeId=...&depth=...&profile=...`: Blast radius analysis
- [x] Return: affected nodes grouped by depth (d=1 WILL BREAK, d=2 LIKELY AFFECTED, d=3 MAY NEED TESTING)
- [x] Include risk level: LOW/MEDIUM/HIGH/CRITICAL

### Services
- [ ] `AnalyzeService.analyzeProject(projectId)`: Orchestrate full analysis pipeline
  1. Get project path
  2. Call ParserService.parseProject()
  3. Save nodes/edges to Neo4j (batch transaction)
  4. Notify WebSocket subscribers
- [ ] `AnalyzeService.analyzeIncremental(projectId, changedFiles)`: Re-parse only changed files
  1. Delete old nodes for changed files
  2. Parse changed files
  3. Insert new nodes/edges
  4. Push incremental update via WebSocket
- [ ] `GraphService.getGraph(projectId, filter)`: Query graph với optional filters
- [x] `GraphService.getImpactAnalysis(projectId, nodeId, depth, profile)`: Trace upstream dependencies (blast radius)
- [ ] `TarballImportService.importFromGithub(request)` (MỚI):
  1. Pre-flight check (GET https://api.github.com/repos/{owner}/{repo})
     - Validate: public, size < 100MB, default_branch
     - Reject với GithubImportException nếu private hoặc quá lớn
  2. Stream tarball (GET /tarball, Bearer ${GITHUB_TOKEN})
     - GzipCompressorInputStream + TarArchiveInputStream
  3. Parse in-memory (KHÔNG ghi disk)
     - Iterate tar entries, lọc *.java
     - ParserService.parseString(content, relPath)
     - GraphRepository.upsertNodes/Edges (batch)
  4. WebSocket push progress: `/topic/projects/{id}/status`
  5. Return {projectId, status: "ANALYZING"} sau pre-flight (parse async)

### Graph data model (raw Driver — KHÔNG dùng @Node entities)
Không có entity Neo4j `@Node` và không có `BaseNode`. Dữ liệu graph mang bởi parser:
- Parser xuất `NodeData` / `EdgeData` / `ParseResult` (parser-neutral, xem `parser/node/`).
- `GraphRepository` là storage abstraction; impl duy nhất `Neo4jGraphRepository` ghi xuống bằng **raw Neo4j Java Driver + parameterized Cypher**.
- Label node và relationship type được validate qua `GraphSchema` (allow-list), không phải class entity Java.
- Node labels: Project, Package, File, Class, Interface, Enum, Record, DBModel, Method, Constructor, Field, Annotation, LocalVariable, Route, APIEndpoint. `External` is legacy-only for persisted cleanup/migration; the parser no longer creates new unresolved-reference stubs.
- Relationship types: OWNS, CONTAINS, DEFINES, HAS_METHOD, HAS_FIELD, HAS_INNER, HAS_RELATION, EXTENDS, IMPLEMENTS, OVERRIDES, IMPORTS, TYPE_OF, RETURNS, PARAMETER_TYPE, THROWS, CALLS, INSTANTIATES, INJECTS, HANDLES_ROUTE, READS, WRITES, CATCHES, STEP_IN_FLOW, PUBLISHES_EVENT, LISTENS_EVENT, TRIGGERS, RESOLVES_TO, CALLS_DYNAMIC, DISPATCH_CANDIDATES. `ANNOTATED_BY` is legacy schema-only; annotation usages are stored in node `properties.annotations`.
- Edge properties: type, confidence, lineNumber, weight, occurrences, cardinality (where applicable).

### WebSocket
- [ ] Topic `/topic/projects/{id}/updates`: Push graph changes
- [ ] Payload: `{type: "FULL_UPDATE" | "INCREMENTAL", affectedNodes[], affectedEdges[]}`
- [ ] Topic `/topic/projects/{id}/status`: Push analysis progress
- [ ] Payload: `{status: "ANALYZING" | "COMPLETED" | "FAILED", progress: 0-100, message}`

## Quy tắc code

1. **Storage Abstraction (QUAN TRỌNG)**: Tất cả graph access qua `GraphRepository` interface
   - Impl duy nhất trong 2 tháng: `impl/neo4j/Neo4jGraphRepository.java`
   - ArchUnit test ép buộc: không class nào ngoài `repository/impl/neo4j/` import `org.neo4j.*` hoặc `org.springframework.data.neo4j.*` (ngoại trừ `common/config/Neo4jMigrationRunner.java`)
2. **Raw Driver only**: Không dùng Spring Data Neo4j OGM, không `@Node`/`@Relationship`, không `*NodeRepository`. Mọi Neo4j access qua `GraphRepository` → `Neo4jGraphRepository` (driver + Cypher).
3. **DTO Mapping**: DTOs (`NodeDto`/`EdgeDto`/`GraphDataResponse`) map từ kết quả query repository (raw Neo4j `Record`) hoặc từ `NodeData`/`EdgeData` — không có entity để expose.
4. **Transaction per file**: Batch write nodes/edges trong 1 transaction per file
5. **Pagination**: Tất cả list endpoints phải support pagination
6. **Indexes**: Đảm bảo indexes được tạo cho fullName, filePath, routePath
7. **Server-managed workspace for import**: GitHub tarball is extracted to a server-owned workspace directory before parsing; workspace is not user-accessible and is cleaned up after import

## Performance Targets

| Metric | Target |
|--------|--------|
| Graph query (3 hops) | < 500ms |
| Full graph (5000 nodes) | < 2 seconds |
| Save 500 files | < 10 seconds |
| WebSocket latency | < 200ms |

## Acceptance Criteria

- [ ] CRUD projects hoạt động đúng
- [ ] Full analysis pipeline: parse → save → notify
- [ ] Incremental update chỉ re-parse changed files
- [ ] Graph query với filters hoạt động
- [ ] N-hop neighbors query đúng
- [ ] Impact analysis trả về affected nodes theo depth
- [ ] WebSocket push updates realtime
- [ ] Integration tests với embedded Neo4j
- [ ] **GitHub Import (MỚI):**
  - [ ] POST /api/projects/import-github trả về 202 Accepted
  - [ ] Pre-flight reject private repo → GithubImportException → 400
  - [ ] Pre-flight reject repo > 100MB → GithubImportException → 400
  - [ ] Tarball stream không ghi file tạm xuống disk
  - [ ] Parse chỉ *.java files, skip build/target/.git/node_modules
  - [ ] WebSocket push progress /topic/projects/{id}/status
- [ ] **Storage Abstraction (MỚI):**
  - [ ] ArchUnit test pass: no Neo4j imports outside impl/neo4j/
  - [ ] Services depend on GraphRepository interface, not impl
