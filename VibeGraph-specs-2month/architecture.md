# VibeGraph — Architecture (2-Month Scope)

## High-level

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

## Module Structure (Maven Multi-Module)

```
vibegraph/
├── pom.xml                          # Parent
├── docker-compose.yml
├── README.md
│
├── vibegraph-core/                  # Parser engine
│   └── src/main/java/com/vibegraph/core/
│       ├── parser/
│       │   ├── visitor/             # ClassVisitor, MethodVisitor, FieldVisitor
│       │   ├── service/             # ParserService + impl
│       │   └── resolver/            # Symbol resolution + call graph
│       ├── graph/                   # Domain models (Node, Edge POJOs)
│       └── spring/                  # Spring annotation detection
│
├── vibegraph-server/                # Spring Boot app
│   └── src/main/java/com/vibegraph/server/
│       ├── VibeGraphApplication.java
│       ├── common/                  # config, exception, dto, util
│       ├── parser/                  # Service wrappers (call core)
│       ├── graph/
│       │   ├── controller/
│       │   ├── service/
│       │   ├── repository/
│       │   │   ├── GraphRepository.java          # INTERFACE (mới)
│       │   │   ├── NodeRepository.java           # INTERFACE
│       │   │   └── impl/
│       │   │       └── neo4j/                    # Neo4j impl
│       │   ├── node/                # @Node classes (Neo4j-specific, dùng nội bộ trong impl)
│       │   ├── websocket/
│       │   └── dto/
│       ├── diagram/                 # Use Case + Class generators
│       ├── mcp/                     # 4 MCP tools
│       ├── watcher/                 # File watcher service
│       └── import/                  # GitHub tarball stream service (mới)
│
├── vibegraph-cli/                   # CLI for real-time local watch (MỚI)
│   ├── pom.xml                      # Java module, reuse vibegraph-core
│   └── src/main/java/com/vibegraph/cli/
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
├── vibegraph-cli-npm/               # npm wrapper (MỚI)
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

## Neo4j Schema

Theo `hungry-liskov/neo4j-schema.md` đầy đủ — không thay đổi.

Tóm tắt:
- **Nodes:** Project, Package, File, Class, Interface, Enum, Method, Field, Annotation, Route
- **Edges:** OWNS, CONTAINS, DEFINES, HAS_METHOD, HAS_FIELD, EXTENDS, IMPLEMENTS, IMPORTS, TYPE_OF, RETURNS, PARAMETER_TYPE, CALLS, OVERRIDES, INJECTS, HANDLES_ROUTE, ANNOTATED_BY
- Mọi node có `projectId` property
- Composite key cho Method: `(projectId, fullName, paramTypes)` để handle overloading
- Stub method khi CALLS edge gặp method chưa parse, sau đó enrich khi parse class chứa nó

## Storage Abstraction

```java
// graph/repository/GraphRepository.java
public interface GraphRepository {
    void upsertProject(ProjectData project);
    void upsertNodes(String projectId, List<NodeData> nodes);
    void upsertEdges(String projectId, List<EdgeData> edges);
    void deleteFile(String projectId, String filePath);
    GraphData getFullGraph(String projectId);
    GraphData getNeighborhood(String projectId, String nodeId, int hops);
    List<NodeData> searchNodes(String projectId, String query);
    List<NodeData> getImpact(String projectId, String targetFullName, int maxDepth);
}
```

Impl duy nhất trong 2 tháng: `impl/neo4j/Neo4jGraphRepository.java`.

ArchUnit test ép buộc:
- Không class nào ngoài `repository/impl/neo4j/` import `org.neo4j.*` hoặc `org.springframework.data.neo4j.*` (ngoại trừ `common/config/Neo4jConfig.java`).

## Data Flow — 3 Use Cases

### 1. User paste GitHub URL (Tarball stream — không clone, không lưu disk)
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
    → ParserService.parseString(content, relPath) cho mỗi file
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

### 2. User chạy local với CLI (real-time, privacy mức 1)
```
User chạy: vibegraph watch (trong project folder)
  ↓
CLI (vibegraph-cli.jar):
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
Source code KHÔNG bao giờ rời máy user.
```

### 3. User chạy local, server-side watcher (legacy/self-host mode)
```
Java WatchService phát hiện UserService.java MODIFY
  ↓
DebouncedEventHandler chờ 500ms (gộp nhiều save liên tiếp)
  ↓
FileWatcherServiceImpl.onChange(filePath)
  → ParserService.parseFile(filePath)
  → GraphRepository.deleteFile(projectId, filePath)  [xóa nodes/edges cũ]
  → GraphRepository.upsertNodes/Edges(new)
  → WebSocket push {type: "INCREMENTAL", added: [...], removed: [...]}
  ↓
Frontend Sigma.js patch graph (không full reload)
```

### 4. AI tool gọi MCP
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

## Deployment Topology

### Dev local (mỗi dev)
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
    build: ./vibegraph-server
    ports: ["8080:8080"]
    depends_on: [neo4j]
    environment:
      SPRING_NEO4J_URI: bolt://neo4j:7687
      SPRING_NEO4J_AUTHENTICATION_USERNAME: neo4j
      SPRING_NEO4J_AUTHENTICATION_PASSWORD: vibegraph
      VIBEGRAPH_GITHUB_TOKEN: ${GITHUB_TOKEN}

  frontend:
    build: ./vibegraph-web
    ports: ["3000:80"]
    depends_on: [backend]

volumes:
  neo4j-data:
```

### Production single-tenant demo
- VPS Hetzner CX22 ($5-7/tháng): 4GB RAM, 2 vCPU, 40GB SSD
- Domain: `vibegraph.com` → DNS A record → VPS IP
- nginx reverse proxy + Let's Encrypt SSL auto
- Cùng docker-compose.yml + env file riêng (production credentials)
