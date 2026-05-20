# VibeGraph — Architecture Design

**Version:** 1.0.0  
**Date:** 2026-05-20

---

## 1. System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Spring Boot Backend                               │
│                                                                       │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │  REST Controller  │  │  WebSocket Hub   │  │  File Watcher    │  │
│  │  /api/projects    │  │  /ws/graph       │  │  (WatchService)  │  │
│  └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘  │
│           │                      │                      │            │
│  ┌────────▼──────────────────────▼──────────────────────▼─────────┐ │
│  │                      Service Layer                              │ │
│  │  ┌─────────────┐  ┌──────────────┐  ┌────────────────────┐    │ │
│  │  │ AnalyzeServ │  │ DiagramServ  │  │ RealtimeService    │    │ │
│  │  └──────┬──────┘  └──────┬───────┘  └────────────────────┘    │ │
│  └─────────┼────────────────┼─────────────────────────────────────┘ │
│            │                │                                        │
│  ┌─────────▼────────────────▼─────────────────────────────────────┐ │
│  │                    Parser Engine                                 │ │
│  │  ┌──────────────────────────────────────────────────────────┐  │ │
│  │  │ JavaParser + Symbol Solver                                │  │ │
│  │  │ → ClassVisitor, MethodVisitor, FieldVisitor               │  │ │
│  │  │ → CallGraphBuilder, InheritanceResolver                   │  │ │
│  │  │ → SpringAnnotationDetector                                │  │ │
│  │  └──────────────────────────────────────────────────────────┘  │ │
│  └────────────────────────────────────────────────────────────────┘ │
│            │                                                         │
│  ┌─────────▼─────────────────────────────────────────────────────┐  │
│  │                   Graph Repository                             │  │
│  │  Spring Data Neo4j (OGM + Cypher queries)                     │  │
│  └─────────┬─────────────────────────────────────────────────────┘  │
└────────────┼────────────────────────────────────────────────────────┘
             │ Bolt protocol (port 7687)
┌────────────▼────────────────────────────────────────────────────────┐
│                         Neo4j 5.x                                    │
│  Nodes: Package, File, Class, Interface, Enum, Method, Field, Route │
│  Edges: EXTENDS, IMPLEMENTS, CALLS, HAS_METHOD, IMPORTS, ...        │
└─────────────────────────────────────────────────────────────────────┘

             │ WebSocket (STOMP)
┌────────────▼────────────────────────────────────────────────────────┐
│                      Vue 3 Frontend                                   │
│                                                                       │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │  Graph View       │  │  Diagram View    │  │  Detail Panel    │  │
│  │  (Sigma.js)       │  │  (Mermaid.js)    │  │  (Node info)     │  │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘  │
│                                                                       │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │  Filter Panel     │  │  Search Bar      │  │  WebSocket Client│  │
│  │  (type, package)  │  │  (node lookup)   │  │  (STOMP/SockJS)  │  │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 2. Module Structure (Maven Multi-Module)

```
vibegraph/
├── vibegraph-core/              # Parser engine + graph builder
│   ├── src/main/java/
│   │   └── com/vibegraph/core/
│   │       ├── parser/          # JavaParser visitors
│   │       ├── graph/           # Node/Edge models
│   │       ├── spring/          # Spring annotation detection
│   │       └── resolver/        # Symbol resolution + call graph
│   └── pom.xml
│
├── vibegraph-server/            # Spring Boot application
│   ├── src/main/java/
│   │   └── com/vibegraph/server/
│   │       ├── controller/      # REST + WebSocket controllers
│   │       ├── service/         # Service interfaces
│   │       │   └── impl/        # Service implementations
│   │       ├── repository/      # Neo4j repositories
│   │       ├── node/            # Neo4j node models (@Node)
│   │       ├── dto/
│   │       │   ├── request/     # Request DTOs
│   │       │   └── response/    # Response DTOs
│   │       ├── config/          # Spring config (Neo4j, WS, CORS)
│   │       ├── watcher/         # File watcher service (WatchService)
│   │       ├── mcp/             # MCP Server tools (AI context)
│   │       ├── steering/        # Steering file generators
│   │       └── diagram/         # Diagram generators
│   └── pom.xml
│
├── vibegraph-web/               # Vue 3 frontend
│   ├── src/
│   │   ├── components/          # Vue components
│   │   ├── composables/         # Vue composables (useSigma, useWebSocket)
│   │   ├── lib/                 # Graph adapter, diagram generators
│   │   ├── stores/              # Pinia stores
│   │   └── views/               # Page views
│   ├── package.json
│   └── vite.config.ts
│
├── docker-compose.yml
├── pom.xml                      # Parent POM (multi-module)
└── README.md
```

---

## 3. Neo4j Schema

### Node Labels & Properties

```cypher
// Package
(:Package {name, fullName, filePath})

// File
(:File {name, filePath, lastModified, checksum})

// Class
(:Class {
  name, fullName, filePath, lineNumber,
  visibility, isAbstract, isFinal, isStatic,
  springLayer, springAnnotations[]
})

// Interface
(:Interface {name, fullName, filePath, lineNumber, visibility})

// Enum
(:Enum {name, fullName, filePath, lineNumber})

// Method
(:Method {
  name, fullName, filePath, lineNumber,
  visibility, isAbstract, isStatic, isFinal,
  returnType, parameters[], throwsTypes[],
  httpMethod, routePath
})

// Field
(:Field {
  name, fullName, filePath, lineNumber,
  visibility, isStatic, isFinal,
  declaredType, isInjected
})

// Route (HTTP endpoint)
(:Route {
  httpMethod, routePath, handlerMethod,
  filePath, lineNumber, middleware[]
})
```

### Relationship Types

```cypher
// Structural
(:Package)-[:CONTAINS]->(:Class|:Interface|:Enum)
(:File)-[:DEFINES]->(:Class|:Interface|:Enum)
(:Class)-[:HAS_METHOD]->(:Method)
(:Class)-[:HAS_FIELD]->(:Field)

// Inheritance
(:Class)-[:EXTENDS]->(:Class)
(:Class)-[:IMPLEMENTS]->(:Interface)
(:Interface)-[:EXTENDS]->(:Interface)

// Dependencies
(:Class)-[:IMPORTS]->(:Class|:Interface)
(:Field)-[:TYPE_OF]->(:Class|:Interface)
(:Method)-[:RETURNS]->(:Class|:Interface)
(:Method)-[:PARAMETER_TYPE]->(:Class|:Interface)

// Call graph
(:Method)-[:CALLS {lineNumber, confidence}]->(:Method)

// Spring-specific
(:Class)-[:INJECTS {via: "constructor"|"field"|"setter"}]->(:Class)
(:Method)-[:HANDLES_ROUTE]->(:Route)
(:Class)-[:ANNOTATED_BY {annotation}]->(:Class)
```

### Indexes

```cypher
CREATE INDEX class_name FOR (c:Class) ON (c.fullName);
CREATE INDEX method_name FOR (m:Method) ON (m.fullName);
CREATE INDEX file_path FOR (f:File) ON (f.filePath);
CREATE INDEX route_path FOR (r:Route) ON (r.routePath);
CREATE FULLTEXT INDEX node_search FOR (n:Class|Interface|Method|Field) ON EACH [n.name];
```

---

## 4. Data Flow

### 4.1 Full Analysis Flow

```
1. Plugin/CLI triggers POST /api/projects/{id}/analyze
2. AnalyzeService scans project directory for .java files
3. For each file:
   a. Compute SHA-256 checksum
   b. Skip if checksum unchanged (incremental)
   c. Parse with JavaParser → CompilationUnit
   d. Visit AST → extract nodes (Class, Method, Field...)
   e. Resolve symbols → extract edges (CALLS, EXTENDS...)
   f. Detect Spring annotations → enrich nodes
4. Batch write to Neo4j (transaction per file)
5. Notify WebSocket subscribers: {type: "FULL_UPDATE", projectId}
6. Frontend receives → fetches new graph → re-renders Sigma.js
```

### 4.2 Incremental Update Flow

```
1. FileWatcher detects .java file change
2. Debounce 500ms (avoid rapid-fire on save)
3. Re-parse only changed file
4. Delete old nodes/edges for that file in Neo4j
5. Insert new nodes/edges
6. Notify WebSocket: {type: "INCREMENTAL", affectedFiles: [...], diff: {...}}
7. Frontend patches graph (add/remove/update nodes) without full reload
```

---

## 5. API Design

### REST Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/projects | Register a project |
| GET | /api/projects | List all projects |
| POST | /api/projects/{id}/analyze | Trigger analysis |
| GET | /api/projects/{id}/status | Analysis status |
| GET | /api/projects/{id}/graph | Full graph (paginated) |
| GET | /api/projects/{id}/graph/nodes?type=Class&package=com.example | Filtered nodes |
| GET | /api/projects/{id}/graph/neighbors/{nodeId}?hops=2 | Node neighborhood |
| GET | /api/projects/{id}/diagrams/usecase | Use case Mermaid |
| GET | /api/projects/{id}/diagrams/class?package=... | Class diagram Mermaid |
| GET | /api/projects/{id}/diagrams/sequence?entry=... | Sequence Mermaid |
| GET | /api/projects/{id}/impact/{nodeId} | Blast radius |

### WebSocket Topics (STOMP)

| Topic | Payload | When |
|-------|---------|------|
| /topic/projects/{id}/updates | `{type, affectedNodes[], affectedEdges[]}` | After re-analysis |
| /topic/projects/{id}/status | `{status, progress, message}` | During analysis |

---

## 6. Frontend Component Architecture

```
App.vue
├── HeaderBar.vue (project selector, search, settings)
├── MainLayout.vue
│   ├── SidePanel.vue (tabs: Filters | Explorer | Flows)
│   │   ├── FilterPanel.vue (node types, edge types, packages, layers)
│   │   ├── ExplorerPanel.vue (file tree, click → focus node on graph)
│   │   ├── FlowsPanel.vue (execution flows list)
│   │   ├── SearchResults.vue
│   │   └── NodeDetail.vue (selected node info)
│   ├── CodeInspector.vue (source code viewer, syntax highlighted, read-only)
│   ├── GraphCanvas.vue (Sigma.js container + Focus Mode reducers)
│   └── DiagramPanel.vue (tabs: Use Case | Class | Sequence)
│       ├── UseCaseDiagram.vue (Mermaid render)
│       ├── ClassDiagram.vue (Mermaid render)
│       └── SequenceDiagram.vue (Mermaid render)
├── NodeDetailPanel.vue (right side: INCOMING + OUTGOING connections)
└── StatusBar.vue (connection status, last update time)
```

### Key Composables

```typescript
// useSigma.ts — Sigma.js lifecycle, graph operations
// useWebSocket.ts — STOMP connection, auto-reconnect
// useGraphData.ts — fetch, cache, patch graph data
// useDiagrams.ts — fetch and render Mermaid diagrams
// useFilters.ts — reactive filter state
```

---

## 7. Deployment

### Docker Compose

```yaml
services:
  neo4j:
    image: neo4j:5-community
    ports: ["7474:7474", "7687:7687"]
    environment:
      NEO4J_AUTH: neo4j/vibegraph
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

---

## 8. Security Considerations (Phase 1 — Local/Team)

- CORS restricted to localhost + configured origins
- No auth in Phase 1 (local/team use only)
- File system access limited to registered project paths
- Neo4j credentials via environment variables
- No source code stored in DB (only metadata/structure)

---

## 9. AI Integration Architecture (MCP + Context)

### 9.1 MCP Server Module

```
vibegraph-server/
└── src/main/java/com/vibegraph/server/
    └── mcp/
        ├── VibeGraphMcpConfig.java        # @Configuration, MCP server setup
        ├── ArchitectureTool.java           # @Tool: get_project_architecture
        ├── ClassContextTool.java           # @Tool: get_class_context
        ├── LayerPatternTool.java           # @Tool: get_layer_pattern
        ├── ImpactAnalysisTool.java         # @Tool: get_impact_analysis
        ├── UseCaseContextTool.java         # @Tool: get_usecase_context
        └── CodingRulesTool.java            # @Tool: get_coding_rules
```

### 9.2 MCP Server Configuration

```java
@Configuration
public class VibeGraphMcpConfig {

    @Bean
    public McpServer mcpServer(AnalyzeService analyzeService,
                               DiagramService diagramService) {
        return McpServer.builder()
            .serverInfo("VibeGraph", "1.0.0")
            .tool(new ArchitectureTool(analyzeService))
            .tool(new ClassContextTool(analyzeService))
            .tool(new LayerPatternTool(analyzeService))
            .tool(new ImpactAnalysisTool(analyzeService))
            .tool(new CodingRulesTool(analyzeService, diagramService))
            .build();
    }
}
```

### 9.3 MCP Tool Example

```java
@Tool(name = "get_project_architecture",
      description = "Get the full architecture context of the Java project including layers, patterns, and naming conventions")
public ArchitectureContext getProjectArchitecture(
        @Param(description = "Project ID") String projectId) {

    return ArchitectureContext.builder()
        .layers(analyzeService.detectLayers(projectId))
        .patterns(analyzeService.detectPatterns(projectId))
        .namingConventions(analyzeService.detectNaming(projectId))
        .classDiagram(diagramService.generateClassDiagram(projectId, null))
        .warnings(analyzeService.detectWarnings(projectId))
        .doNot(analyzeService.generateAntiPatterns(projectId))
        .build();
}
```

### 9.4 AI Tool Connection

```
┌─────────────────────────────────────────────────────────────┐
│  AI Coding Tool (Cursor / Kiro / Claude Code)                │
│                                                               │
│  mcp.json config:                                            │
│  {                                                           │
│    "mcpServers": {                                           │
│      "vibegraph": {                                          │
│        "url": "http://localhost:8080/mcp",                   │
│        "transport": "streamable-http"                        │
│      }                                                       │
│    }                                                         │
│  }                                                           │
└──────────────────────────┬──────────────────────────────────┘
                           │ MCP Protocol (Streamable HTTP)
                           ▼
┌──────────────────────────────────────────────────────────────┐
│  VibeGraph Spring Boot (MCP Server)                           │
│                                                               │
│  Tools exposed:                                              │
│  • get_project_architecture → layers, patterns, rules        │
│  • get_class_context(name) → related classes, diagram        │
│  • get_layer_pattern(layer) → how to write in this layer     │
│  • get_impact_analysis(target) → what breaks if you change   │
│  • get_coding_rules → DO / DON'T based on current arch       │
└──────────────────────────────────────────────────────────────┘
```

### 9.5 Steering File Auto-Generation

```
vibegraph-server/
└── src/main/java/com/vibegraph/server/
    └── steering/
        ├── SteeringFileGenerator.java     # Core generation logic
        ├── KiroSteeringWriter.java        # .kiro/steering/vibegraph-context.md
        ├── CursorRulesWriter.java         # .cursor/rules/vibegraph.mdc
        └── ClaudeRulesWriter.java         # CLAUDE.md section
```

Generated steering file example (`.kiro/steering/vibegraph-context.md`):

```markdown
---
inclusion: always
---

# VibeGraph Project Context (auto-generated)
# Last updated: 2026-05-20T14:30:00Z
# DO NOT EDIT — this file is regenerated by VibeGraph

## Architecture Layers
- Controller (@RestController) → Service (@Service) → Repository (@Repository)

## Packages
- com.example.controller — HTTP endpoints
- com.example.service — Business logic
- com.example.repository — Data access
- com.example.dto — Request/Response objects
- com.example.entity — JPA entities

## Naming Conventions
- Controllers: {Entity}Controller
- Services: {Entity}Service
- Repositories: {Entity}Repository
- DTOs: Create{Entity}Dto, Update{Entity}Dto, {Entity}ResponseDto

## Patterns
- Dependency Injection: Constructor injection (no @Autowired on fields)
- Validation: @Valid on @RequestBody
- Error handling: GlobalExceptionHandler with @ControllerAdvice
- Pagination: Pageable parameter in Repository methods

## Rules
- DO: Follow existing layer pattern (Controller → Service → Repository)
- DO: Create DTO for request/response (never expose Entity directly)
- DO: Add @Transactional on Service methods that write data
- DON'T: Put business logic in Controller
- DON'T: Call Repository directly from Controller
- DON'T: Create new packages outside existing structure without discussion
- DON'T: Use field injection (@Autowired on fields)

## Current Warnings
- UserService has 18 methods — consider splitting
- No tests for PaymentController
```

### 9.6 Pre-Code Hook (Kiro)

Generated `.kiro/hooks/vibegraph-precheck.json`:

```json
{
  "name": "VibeGraph Architecture Check",
  "version": "1.0.0",
  "description": "Forces AI to read project architecture context before writing code",
  "when": {
    "type": "preToolUse",
    "toolTypes": ["write"]
  },
  "then": {
    "type": "askAgent",
    "prompt": "Before writing this file, verify it follows the project architecture. Check: 1) Correct layer (Controller/Service/Repository), 2) Correct package, 3) Correct naming convention, 4) No anti-patterns from VibeGraph rules. If you haven't read the VibeGraph context yet, call the get_project_architecture MCP tool first."
  }
}
```
