# VibeGraph — Kiến trúc (Phạm vi 2 tháng)

## Tổng quan

```
┌──────────────────────────────────────────────────────────┐
│  Browser (Vue 3 + Sigma.js)                              │
│  - Force Graph                                            │
│  - Use Case + Class diagrams (Mermaid)                    │
│  - Explorer + Filter + Node Detail panels                 │
└────────────────────┬─────────────────────────────────────┘
                     │ HTTPS / WebSocket
┌────────────────────▼─────────────────────────────────────┐
│  Spring Boot Backend (Java 21)                            │
│                                                            │
│  ┌────────────┐  ┌────────────┐  ┌────────────────────┐  │
│  │REST + WS   │  │MCP Server  │  │File Watcher        │  │
│  │Controllers │  │(Streamable │  │(Java WatchService) │  │
│  │            │  │ HTTP)      │  │                    │  │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────────────┘  │
│        │                │                │                │
│  ┌─────▼────────────────▼────────────────▼──────────┐    │
│  │ Service Layer (Parser, Analyze, Diagram, Import) │    │
│  └───────────────────┬──────────────────────────────┘    │
│                      │                                    │
│  ┌───────────────────▼──────────────────────────────┐    │
│  │ GraphRepository INTERFACE                         │    │
│  │ → Neo4jGraphRepository impl                       │    │
│  └───────────────────┬──────────────────────────────┘    │
└──────────────────────┼───────────────────────────────────┘
                       │ Bolt
                ┌──────▼──────┐
                │  Neo4j 5.x  │
                │  (Docker)   │
                └─────────────┘

         ┌──────────────────────┐
         │  AI Tools (Cursor,   │  ← MCP Streamable HTTP
         │  Claude Code, Kiro)  │
         └──────────────────────┘
```

## Cấu trúc module

> **Quyết định (2026-05-29): giữ SINGLE-MODULE trong scope 2 tháng.**
>
> Code thực tế là một module Spring Boot duy nhất (`pom.xml` ở root, parent =
> `spring-boot-starter-parent`), package phẳng dưới `com.vibegraph.{common,
> parser, graph, mcp, diagram, watcher}`. Đây là trạng thái CHÍNH THỨC cho M1–M2.
>
> Lý do:
> - Deadline 2 tháng — multi-module thêm overhead (parent POM, dependency
>   management, build order) mà chưa có consumer thứ 2 của `vibegraph-core`.
> - `vibegraph-cli` chưa khởi động (xem file-checklist). Khi nào cần CLI thì mới
>   promote `parser/` thành module `vibegraph-core` — cost thấp vì boundary đã rõ
>   (`com.vibegraph.parser.*` tách bạch `com.vibegraph.graph.*`).
> - ArchUnit rule (cấm Neo4j leak ngoài `repository/impl/neo4j/`) chạy được trên
>   single-module.
>
> Layout multi-module bên dưới là **mục tiêu M3+ (tương lai)**, không phải hiện tại.

### Bố cục mục tiêu (Maven Multi-Module — M3+, chưa áp dụng)

```
vibegraph/
├── pom.xml                          # Parent
├── docker-compose.yml
├── README.md
│
├── vibegraph-core/                  # Parser engine
│   └── src/main/java/com/vibegraph/parser/
│       ├── parser/
│       │   ├── visitor/             # ClassVisitor, MethodVisitor, FieldVisitor
│       │   ├── service/             # ParserService + impl
│       │   └── resolver/            # Symbol resolution + call graph
│       ├── graph/                   # Domain models (Node, Edge POJOs)
│       └── spring/                  # Spring annotation detection
│
├── vibegraph-server/                # Spring Boot app
│   └── src/main/java/com/vibegraph/
│       ├── VibeGraphApplication.java
│       ├── common/                  # config, exception, dto, util
│       ├── parser/                  # Service wrappers (call core)
│       ├── graph/
│       │   ├── controller/
│       │   ├── service/
│       │   ├── repository/
│       │   │   ├── GraphRepository.java          # INTERFACE
│       │   │   └── impl/
│       │   │       └── neo4j/                    # Neo4j impl
│       │   ├── websocket/
│       │   └── dto/
│       ├── diagram/                 # Use Case + Class generators
│       ├── mcp/                     # 4 MCP tools
│       ├── watcher/                 # File watcher service
│       └── import/                  # GitHub tarball stream service (mới)
│
├── vibegraph-cli/                   # Future CLI module (post-MVP)
│   ├── pom.xml                      # Java module, reuse vibegraph-core
│   └── # deferred CLI module; no current source folder
│       ├── VibeGraphCli.java        # Main entry point
│       ├── watcher/
│       │   └── LocalWatcher.java    # directory-watcher, detect changes
│       ├── parser/
│       │   └── DiffExtractor.java   # JavaParser → extract metadata diff
│       ├── client/
│       │   ├── WsClient.java        # WebSocket client to server
│       │   └── ApiKeyAuth.java      # API key authentication
│       └── command/
│           ├── LoginCommand.java    # vibegraph login --api-key=xxx
│           ├── WatchCommand.java    # vibegraph watch
│           └── SyncCommand.java     # vibegraph sync (full re-sync)
│
├── vibegraph-cli-npm/               # Future npm wrapper (post-MVP)
│   ├── package.json                 # npm package: vibegraph
│   ├── bin/vibegraph.js            # Entry point, gọi java -jar
│   ├── postinstall.js              # Check Java 21 version
│   └── README.md
│
└── vibegraph-web/                   # Vue 3 frontend
    └── src/
        ├── views/
        ├── components/
        │   ├── layout/
        │   ├── panels/              # Filter, Explorer, NodeDetail, Legend
        │   ├── graph/               # GraphCanvas (Sigma.js), Controls, SearchBar
        │   ├── diagram/             # UseCase, Class (Mermaid)
        │   └── ui/
        ├── composables/
        ├── lib/
        ├── stores/
        └── types/
```

### Bố cục hiện tại (Single-module — M1–M2, đang dùng)

```
vibegraph/
├── pom.xml                          # Single Spring Boot module
├── docker-compose.yml               # Neo4j + backend + frontend
├── Dockerfile
├── .env / .env.example
│
├── src/main/java/com/vibegraph/
│   ├── VibeGraphApplication.java
│   ├── common/                      # config, exception, dto, util
│   ├── parser/                      # visitor, service, node (NodeData/EdgeData/ParseResult)
│   ├── graph/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   │   ├── GraphRepository.java         # INTERFACE
│   │   │   └── impl/neo4j/                  # Neo4j impl (chỉ chỗ này import org.neo4j.*)
│   │   ├── websocket/
│   │   └── dto/
│   ├── diagram/                     # Use Case + Class generators
│   ├── mcp/                         # MCP tools
│   └── watcher/                     # File watcher
│
├── src/main/resources/db/migration/ # Cypher migrations (V1__init_schema.cypher)
│
└── vibegraph-web/                   # Vue 3 frontend (Vite, độc lập build)
```

CLI (`vibegraph-cli`, `vibegraph-cli-npm`) thuộc giai đoạn post-MVP. Server-side
watcher là hướng triển khai trong phạm vi 2 tháng. Nếu sau M2 cần một CLI riêng,
hãy promote `com.vibegraph.parser.*` thành `vibegraph-core` trước.

## Schema Neo4j

Nguồn schema: `src/main/resources/db/migration/V1__init_schema.cypher` và
`VibeGraph-specs-2month/neo4j-schema.md`.

Tóm tắt:
- **Nodes:** Project, Package, File, Class, Interface, Enum, Method, Field, Annotation, Route, External
- **Edges:** OWNS, CONTAINS, DEFINES, HAS_METHOD, HAS_FIELD, HAS_INNER, EXTENDS, IMPLEMENTS, IMPORTS, TYPE_OF, RETURNS, PARAMETER_TYPE, THROWS, CALLS, OVERRIDES, INJECTS, HANDLES_ROUTE, ANNOTATED_BY
- Mọi node có `projectId` property
- Composite key cho Method: `(projectId, fullName, paramTypes)` để xử lý overloading
- Stub method khi CALLS edge gặp method chưa parse, sau đó enrich khi parse class chứa nó

> **Trạng thái implementation sau audit 2026-05-30:** schema file cho phép đầy đủ các label/edge trên, nhưng parser Sprint 1 chưa phát ra `Package`/`File` nodes và chưa phát ra `OWNS`/`CONTAINS`/`DEFINES`/`ANNOTATED_BY`/`OVERRIDES`. `Neo4jGraphRepository.upsertEdges` hiện tạo `External` stub cho endpoint bị thiếu; `MethodVisitor` bỏ qua call unresolved/library thay vì tạo method stub. Vì vậy phần “stub method┐ là design target, chưa phải behavior hiện tại.

## Trừu tượng hóa lưu trữ

```java
// graph/repository/GraphRepository.java
public interface GraphRepository {
    void upsertProject(String projectId, String name, String path);
    void upsertNodes(String projectId, List<NodeData> nodes);
    int upsertEdges(String projectId, List<EdgeData> edges);
    void deleteFile(String projectId, String filePath);
    GraphDataResponse getFullGraph(String projectId);
    GraphDataResponse getNeighborhood(String projectId, String nodeId, int hops);
    List<NodeDto> searchNodes(String projectId, String query);
    List<NodeDto> getImpact(String projectId, String targetFullName, int maxDepth);
}
```

Impl duy nhất trong 2 tháng: `impl/neo4j/Neo4jGraphRepository.java`.

Migration cho MVP: `common/config/Neo4jMigrationRunner` (ApplicationRunner) áp dụng
`src/main/resources/db/migration/V1__init_schema.cypher` một cách idempotent lúc khởi
động. Runner hiện CHƯA theo dõi version/metadata đã áp dụng — version tracking (ví dụ
`:Migration` node) là kế hoạch sau. Neo4j không tự áp dụng file này theo tên file.

ArchUnit test ép buộc:
- Không class nào ngoài `repository/impl/neo4j/` import `org.neo4j.*` hoặc `org.springframework.data.neo4j.*` (ngoại trừ package `common/config`, ví dụ `Neo4jMigrationRunner.java` cần Driver để chạy schema migration lúc startup).

## Luồng dữ liệu — các use case

### 1. Người dùng upload ZIP/TAR project (flow chính Sprint 2)

> **Luồng mục tiêu Sprint 2.** Quyết định product ngày 2026-05-31: user không nhập local path thủ công trong UX chính. User chọn archive từ file explorer, backend nhận multipart, parse các file `.java`, lưu graph rồi frontend mở graph. Local-path registration của Sprint 1 giữ lại cho dev/internal fallback.

```
Browser → POST /api/projects/import-archive multipart {name, file=.zip|.tar|.tar.gz}
  ↓
ArchiveImportService.importArchive(...)
  Step 1: Validate request
    → Reject archive > 100MB
    → Reject unsupported extension/MIME
  Step 2: Stream archive entries
    → ZIP via ZipInputStream, TAR/TAR.GZ via commons-compress
    → Reject path traversal, absolute path, unsafe symlink, archive bomb patterns
    → Skip target/build/.git/.idea/node_modules and non-.java files
  Step 3: Parse Java entries
    → Preserve relative path as filePath
    → Materialize safe `.java` entries into a server-owned workspace
    → ParserService.parseProject(workspacePath)  # dùng API hiện có; parseString là target API tương lai, chưa có trong code hiện tại
    → GraphRepository.upsertProject/upsertNodes/upsertEdges
  Step 4: WebSocket progress
    → /topic/projects/{id}/status
  ↓
Frontend redirects to /projects/{id}/graph when READY
```

### 2. Người dùng dán URL GitHub (Tarball stream — không clone, không lưu disk)

> **Luồng mục tiêu Sprint 2.** Code hiện có `ImportController` và route `POST /api/projects/import-github`, nhưng `TarballImportServiceImpl` vẫn ném `FeatureNotImplementedException`. `ParserService.parseString(content, relPath)` trong flow dưới đây là API cần bổ sung hoặc thay bằng cơ chế parse stream tương đương; hiện chỉ có `parseFile`/`parseProject` và `parseFileWithCache` chưa implement.

```
Browser → POST /api/projects/import-github {url}
  ↓
TarballImportService.import()
  Step 1: Pre-flight check
    → GET https://api.github.com/repos/{owner}/{repo}
    → Validate: public, size < 100MB, default_branch
    → Reject nếu private hoặc quá lớn
  Step 2: Stream tarball
    → GET https://api.github.com/repos/{owner}/{repo}/tarball
    → Auth: Bearer ${GITHUB_TOKEN}
    → Stream qua GzipCompressorInputStream + TarArchiveInputStream
  Step 3: Parse in-memory (KHÔNG ghi disk)
    → Iterate tar entries, lọc *.java
    → ParserService.parseString(content, relPath) cho mỗi file  # target API, chưa có trong code hiện tại
    → GraphRepository.upsertNodes/Edges (batch)
  Step 4: WebSocket push progress
    → SimpMessagingTemplate.convertAndSend("/topic/projects/{id}/status", {progress})
  ↓
Return {projectId, status: "ANALYZING"} ngay sau pre-flight (async parse)
  ↓
Frontend nhận status update qua WebSocket, hiển thị progress bar
```

**Ưu điểm so với JGit clone:**
- 1 request HTTP duy nhất cho cả repo (thay vì git protocol nhiều round-trip)
- Không lưu file xuống disk → không cần cleanup job, không tốn space VPS
- Nhanh hơn 3x với repo nhỏ-vừa (~80 file)
- Không cần dependency JGit (~10MB), thay bằng commons-compress (~700KB)

### 3. CLI watch cục bộ (Post-MVP, hoãn lại)
```
User runs: vibegraph watch (inside project folder)
  ↓
CLI (vibegraph-cli.jar, post-MVP):
  Step 1: Initial scan
    → directory-watcher scan tất cả .java files
    → JavaParser parse mỗi file → extract metadata (nodes + edges)
    → Gửi full graph metadata lên server qua WebSocket
    → Server upsert vào Neo4j, trả về projectId
  Step 2: Auto-open browser (mặc định, --no-open để tắt)
    → java.awt.Desktop.browse("https://vibegraph.com/project/{projectId}")
    → Fallback: Runtime.exec với xdg-open (Linux), open (macOS), start (Windows)
    → Nếu không mở được: in URL ra console
  Step 3: Watch loop (chạy mãi)
    → directory-watcher detect CREATE/MODIFY/DELETE
    → Debounce 500ms
    → JavaParser parse file thay đổi → extract diff
    → Gửi diff metadata lên server qua WebSocket:
      {type: "INCREMENTAL", added: [...], removed: [...], modified: [...]}
    → Server update Neo4j
    → Server push graph diff về browser
  ↓
Browser Sigma.js patch graph (không full reload)

**Privacy:** CLI chỉ gửi metadata (class name, method signature, edges).
Source code KHÔNG bao giờ r┐i máy user.
```

### 4. Người dùng chạy chế độ self-host cục bộ với server-side watcher (MVP)

> **Luồng mục tiêu Sprint 2.** `WatcherProperties`, `FileWatcherServiceImpl` và `DebouncedEventHandler` đã có file, nhưng start/stop watch, debounce và incremental reparse/update còn TODO; WebSocket broadcast cũng chưa nối vào pipeline thật.

```
Java WatchService phát hiện UserService.java MODIFY
  ↓
DebouncedEventHandler ch┐ 500ms (gộp nhiều save liên tiếp)
  ↓
FileWatcherServiceImpl.onChange(filePath)
  → ParserService.parseFile(filePath)
  → GraphRepository.deleteFile(projectId, filePath)  [xóa nodes/edges cũ]
  → GraphRepository.upsertNodes/Edges(new)
  → WebSocket push {type: "INCREMENTAL", added: [...], removed: [...]}
  ↓
Frontend Sigma.js patch graph (không full reload)
```

### 5. AI tool gọi MCP

> **Luồng mục tiêu Sprint 3.** MCP packages/classes đã có scaffold, nhưng chưa có `@Tool` methods và `ArchitectureAnalyzer`/`McpToolService` còn TODO. Tên method `ArchitectureTool.getProjectArchitecture` dưới đây mô tả contract mong muốn, không phải method đang tồn tại.

```
Cursor/Claude Code → http://localhost:8080/mcp
  ↓
Spring AI MCP Streamable HTTP
  → ArchitectureTool.getProjectArchitecture(projectId)
  → AnalyzeService.detectLayers + detectPatterns + ...
  → GraphRepository.getFullGraph(projectId)
  ↓
Return ArchitectureContext JSON với Mermaid embedded
```

## Topology triển khai

### Dev cục bộ (mỗi dev)
```yaml
# docker-compose.yml
services:
  neo4j:
    image: neo4j:5-community
    environment:
      NEO4J_AUTH: neo4j/vibegraph
      NEO4J_PLUGINS: '["apoc"]'   # cho apoc.path.subgraphAll
    ports: ["7474:7474", "7687:7687"]
    volumes: [neo4j-data:/data]

  backend:
    build:
      context: .
      dockerfile: Dockerfile
    ports: ["8080:8080"]
    depends_on: [neo4j]
    environment:
      NEO4J_URI: bolt://neo4j:7687
      NEO4J_USERNAME: neo4j
      NEO4J_PASSWORD: vibegraph
      GITHUB_TOKEN: ${GITHUB_TOKEN}

  frontend:
    build: ./vibegraph-web
    ports: ["3000:80"]
    depends_on: [backend]

volumes:
  neo4j-data:
```

### Demo production single-tenant
- VPS Hetzner CX22 ($5-7/tháng): 4GB RAM, 2 vCPU, 40GB SSD
- Domain: `vibegraph.com` → DNS A record → VPS IP
- nginx reverse proxy + Let's Encrypt SSL tự động
- Cùng docker-compose.yml + file env riêng (production credentials)
