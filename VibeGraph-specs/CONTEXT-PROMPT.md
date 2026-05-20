# VibeGraph — Project Context Prompt

Dán prompt này vào đầu conversation mới để AI nắm được toàn bộ thông tin dự án.

---

## PROMPT BẮT ĐẦU TỪ ĐÂY:

---

# Dự án: VibeGraph

## Tổng quan
VibeGraph là web app phân tích mã nguồn Java realtime. Nó đọc source code Java, xây dựng knowledge graph (nodes + edges), hiển thị dưới dạng Force Graph tương tác và tự động generate UML diagrams (Use Case, Class, Sequence). Đặc biệt, nó cung cấp context cho AI coding tools (Cursor, Kiro, Claude Code) qua MCP Server, giúp AI hiểu architecture trước khi sinh code.

## Vấn đề giải quyết
- AI vibe coding thiếu context → sinh code sai architecture
- Developer mới mất thời gian hiểu codebase
- Tài liệu kiến trúc luôn lỗi thời
- Không có tool nào kết hợp visualization + AI context provider

## USP (Unique Selling Point)
VibeGraph là tool duy nhất biến code thành context mà AI bắt buộc phải đọc trước khi sinh code. Các tool khác cho người nhìn, VibeGraph cho cả người lẫn AI hiểu.

---

## Tech Stack (đã confirm)

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.3+ / Java 21 (virtual threads) |
| Parser | JavaParser 3.26+ (parse Java, type resolution, call graph) |
| Database | Neo4j 5.x Community (graph database, Cypher queries) |
| DB Driver | Spring Data Neo4j 7.x |
| MCP Server | Spring AI MCP Boot Starter (Streamable HTTP transport) |
| WebSocket | Spring WebSocket (STOMP over SockJS) |
| File Watcher | Java WatchService (JDK built-in, tự động detect file changes) |
| Frontend | Vue 3 (Composition API) + Vite 6.x |
| Force Graph | Sigma.js 3.x (WebGL renderer) + Graphology (data structure) |
| Layout | graphology-layout-forceatlas2 (Web Worker) |
| UML Diagrams | Mermaid.js 11.x |
| State | Pinia 2.x |
| Build (BE) | Maven 3.9+ |
| Container | Docker + Docker Compose |

---

## Kiến trúc hệ thống

```
Developer save file (bất kỳ IDE nào)
         │
         ▼
┌─────────────────────────────────────────────────────┐
│  Spring Boot Backend                                 │
│  ├── File Watcher (WatchService, auto-detect)       │
│  ├── Parser Engine (JavaParser + Symbol Solver)     │
│  ├── Graph Builder (nodes + edges → Neo4j)          │
│  ├── Diagram Generators (Use Case, Class, Sequence) │
│  ├── MCP Server (context cho AI tools)              │
│  ├── Steering File Generator (.cursorrules, .kiro/) │
│  ├── REST API                                        │
│  └── WebSocket Hub (push realtime updates)          │
└──────────────────────┬──────────────────────────────┘
                       │
              ┌────────▼────────┐
              │     Neo4j       │
              │  (Graph DB)     │
              └────────┬────────┘
                       │
         ┌─────────────┼─────────────┐
         ▼                           ▼
┌─────────────────┐       ┌──────────────────┐
│  Vue.js Frontend │       │  AI Coding Tools │
│  (Dashboard)     │       │  (via MCP)       │
│  - Sigma.js      │       │  Cursor, Kiro,   │
│  - Mermaid.js    │       │  Claude Code     │
└─────────────────┘       └──────────────────┘
```

---

## Module Structure

```
vibegraph/
├── vibegraph-core/          # Parser engine + graph builder
│   ├── pom.xml
│   └── src/main/java/
│       └── com/vibegraph/core/
│           ├── parser/          # JavaParser visitors (Class, Method, Field)
│           ├── graph/           # Node/Edge models
│           ├── spring/          # Spring annotation detection
│           └── resolver/        # Symbol resolution + call graph
│
├── vibegraph-server/        # Spring Boot application
│   ├── pom.xml
│   └── src/main/java/
│       └── com/vibegraph/server/
│           ├── controller/      # REST + WebSocket controllers
│           ├── service/         # Service interfaces
│           │   └── impl/        # Service implementations
│           ├── repository/      # Neo4j repositories
│           ├── node/            # Neo4j node models (@Node)
│           ├── dto/
│           │   ├── request/     # Request DTOs
│           │   └── response/    # Response DTOs
│           ├── config/          # Spring config (Neo4j, WS, CORS)
│           ├── watcher/         # File watcher service
│           ├── mcp/             # MCP Server tools
│           ├── steering/        # Steering file generators
│           └── diagram/         # Diagram generators
│
├── vibegraph-web/           # Vue 3 frontend
│   ├── package.json
│   └── src/
│       ├── components/      # Vue components
│       ├── composables/     # useSigma, useWebSocket, useGraphData
│       ├── lib/             # Graph adapter, constants
│       ├── stores/          # Pinia stores
│       └── views/           # Page views
│
├── docker-compose.yml
└── pom.xml                  # Parent POM (multi-module)
```

---

## Neo4j Schema

### Nodes
- Package, File, Class, Interface, Enum, Method, Field, Annotation, Route

### Edges
- EXTENDS, IMPLEMENTS, CALLS, HAS_METHOD, HAS_FIELD, IMPORTS, DEPENDS_ON, CONTAINS, ANNOTATED_BY, INJECTS, TYPE_OF, HANDLES_ROUTE

### Node Properties
- name, fullName, filePath, lineNumber, visibility, isAbstract, isStatic, returnType, parameters, springLayer, springAnnotations, httpMethod, routePath

---

## Tính năng chính (Phase 1)

1. **Java Source Code Parsing** — JavaParser + Symbol Solver, extract nodes/edges, >90% call graph accuracy
2. **Neo4j Knowledge Graph** — lưu trữ relationships, incremental update
3. **Force Graph Visualization** — Sigma.js WebGL, 5000+ nodes 60fps, zoom/pan/click/filter/search, Focus Mode (click node → highlight + dim unrelated), Explorer panel (file tree → click focus)
4. **Use Case Diagram** — auto-detect actors từ @Controller, @Scheduled, @KafkaListener
5. **Class Diagram** — classes + fields + methods + inheritance + dependencies
6. **Sequence Diagram** — trace call chain từ entry point (Controller → Service → Repository)
7. **Realtime Update** — File Watcher detect changes → re-parse < 3 giây → WebSocket push → frontend update
8. **MCP Server** — AI tools gọi get_project_architecture, get_class_context, get_coding_rules...
9. **Steering File Auto-Gen** — tự tạo .kiro/steering/, .cursorrules, CLAUDE.md từ code thật
10. **Pre-code Hook** — template hook bắt buộc AI đọc context trước khi write

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/projects | Register project (path to folder) |
| POST | /api/projects/{id}/analyze | Trigger full analysis |
| GET | /api/projects/{id}/graph | Full graph (nodes + edges) |
| GET | /api/projects/{id}/graph/neighbors/{nodeId}?hops=2 | Node neighborhood |
| GET | /api/projects/{id}/diagrams/usecase | Use Case (Mermaid syntax) |
| GET | /api/projects/{id}/diagrams/class?package=... | Class diagram |
| GET | /api/projects/{id}/diagrams/sequence?entry=... | Sequence diagram |
| GET | /api/projects/{id}/context | Architecture context (cho AI) |
| GET | /api/projects/{id}/impact/{nodeId} | Blast radius |
| WS | /ws/graph-updates | WebSocket realtime |

---

## MCP Tools (cho AI)

- `get_project_architecture` — layers, patterns, naming conventions
- `get_class_context(className)` — related classes, diagram, methods
- `get_layer_pattern(layer)` — how to write in Controller/Service/Repository
- `get_impact_analysis(target)` — what breaks if you change target
- `get_usecase_context(feature)` — related use cases
- `get_coding_rules` — DO/DON'T rules based on current architecture

---

## Quyết định đã confirm

- Tên: **VibeGraph**
- Ngôn ngữ target: **Java only** (Phase 1). Multi-language Phase 2.
- Parser: **JavaParser** (pure Java, không cần native bindings)
- Không có IntelliJ Plugin (Phase 1) — dùng File Watcher tự động, hoạt động với mọi IDE
- Không đọc pom.xml/build.gradle của project target (không cần)
- Đọc folder local (user tự clone repo, trỏ path)
- Frontend: Force Graph (Sigma.js) + UML (Mermaid.js)
- Realtime: tự động 100%, developer save file → graph update < 3 giây

---

## Team & Timeline

- **Team:** 5 developers
- **Timeline:** 6 tuần (trong deadline 2 tháng, còn 2 tuần buffer)
- **Dev 1:** Backend Lead — Parser Engine
- **Dev 2:** Backend — API + Neo4j + WebSocket + MCP
- **Dev 3:** Frontend Lead — Sigma.js Force Graph
- **Dev 4:** Frontend — Mermaid Diagrams + UI
- **Dev 5:** Integration — File Watcher + DevOps + Testing

### Milestones
- Week 2: Parse Java → Force Graph hiển thị trên browser
- Week 4: Realtime + Use Case + Class diagrams
- Week 6: MCP Server + Docker deploy + AI integration

---

## Phase 2 (sau MVP)

- Multi-language (TypeScript, Python, Kotlin, C#) via JTreeSitter
- IntelliJ Plugin (status bar, toolbar button)
- Git URL input (tự clone từ remote)
- Multi-user authentication
- Git history analysis
- Cloud deployment (SaaS)
- AI-powered refactoring suggestions

---

## Performance Targets

| Metric | Target |
|--------|--------|
| Parse 500 Java files | < 30 seconds |
| Incremental update (1 file) | < 3 seconds |
| Neo4j query (3 hops) | < 500ms |
| Frontend render 5000 nodes | 60fps |
| WebSocket latency | < 200ms |

---

## Deployment

```yaml
# docker-compose.yml
services:
  neo4j:
    image: neo4j:5-community
    ports: ["7474:7474", "7687:7687"]
  backend:
    build: ./vibegraph-server
    ports: ["8080:8080"]
  frontend:
    build: ./vibegraph-web
    ports: ["3000:80"]
```

Chạy: `docker compose up -d` — xong.

VPS tối thiểu: 4GB RAM, 2 CPU, 20GB SSD.

---

## Files tài liệu chi tiết

Nếu cần đọc thêm, các file spec nằm tại `VibeGraph-specs/`:
- `requirements.md` — 13 functional requirements + acceptance criteria
- `architecture.md` — system design, Neo4j schema, API, MCP, data flow
- `task-breakdown.md` — task cụ thể cho 5 dev × 6 tuần
- `presentation.md` — trình bày cho khách hàng (5W1H)
