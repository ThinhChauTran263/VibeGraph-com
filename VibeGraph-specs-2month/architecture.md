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
│       └── import/                  # GitHub clone service (mới)
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

### 1. User paste GitHub URL
```
Browser → POST /api/projects/import-github {url}
  ↓
GithubImportService.clone()
  → JGit shallow clone vào /tmp/vibegraph/{projectId}
  → projectService.register(path)
  → analyzeService.fullAnalyze(projectId)  [async, virtual thread]
  ↓
Return {projectId, status: "ANALYZING"}

Background:
  AnalyzeService scan .java files
  → ParserService parse mỗi file
  → GraphRepository.upsertNodes/Edges (batch)
  → SimpMessagingTemplate.convertAndSend("/topic/projects/{id}/status", {progress})
  ↓
Frontend nhận status update qua WebSocket
```

### 2. User chạy local, save file
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

### 3. AI tool gọi MCP
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
