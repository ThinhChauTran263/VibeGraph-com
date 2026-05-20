# VibeGraph — Requirements Specification

**Version:** 1.0.0  
**Date:** 2026-05-20  
**Status:** Draft  
**Team:** 5 developers  
**Timeline:** 6 weeks  
**Target Language:** Java only (multi-language deferred to Phase 2)

---

## 1. Product Overview

### 1.1 Vision
VibeGraph là công cụ phân tích mã nguồn Java realtime, tự động hình thành knowledgex graph và generate các sơ đồ UML (Use Case, Class, Sequence) giúp developer và AI (vibe coding) hiểu cấu trúc dự án, tránh lỗi khi phát triển.

### 1.2 Problem Statement
- Vibe coding (AI-assisted development) thiếu context về cấu trúc dự án → sinh code sai architecture
- Developer mới join team mất nhiều thời gian hiểu codebase
- Không có tool nào auto-generate UML diagrams realtime từ Java source code
- Thay đổi code không được reflect ngay lên sơ đồ kiến trúc

### 1.3 Target Users
| User | Nhu cầu |
|------|---------|
| Java Developer | Hiểu cấu trúc dự án, navigate code relationships |
| Tech Lead / Architect | Review architecture, detect violations |
| AI Coding Assistant | Context về project structure để generate code chính xác |
| New Team Member | Onboarding nhanh qua visual diagrams |

---

## 2. Functional Requirements

### FR-01: Java Source Code Parsing
**Priority:** Critical  
**Description:** Hệ thống phải parse toàn bộ Java source files trong một project directory và extract ra knowledge graph.

**Acceptance Criteria:**
- [ ] Parse tất cả `.java` files trong project (recursive)
- [ ] Extract nodes: Package, Class, Interface, Enum, Method, Field, Annotation
- [ ] Extract edges: EXTENDS, IMPLEMENTS, CALLS, HAS_METHOD, HAS_FIELD, IMPORTS, DEPENDS_ON, ANNOTATED_BY
- [ ] Resolve method calls chính xác qua JavaParser Symbol Solver (>90% accuracy)
- [ ] Detect Spring Boot layers: @Controller, @Service, @Repository, @Component
- [ ] Detect Spring annotations: @Autowired, @RequestMapping, @Scheduled, @KafkaListener
- [ ] Handle inner classes, anonymous classes, lambda expressions
- [ ] Parse time < 30 seconds cho project 500 files

### FR-02: Knowledge Graph Storage (Neo4j)
**Priority:** Critical  
**Description:** Lưu trữ knowledge graph trong Neo4j với schema tối ưu cho traversal queries.

**Acceptance Criteria:**
- [ ] Node types: Package, File, Class, Interface, Enum, Method, Field, Annotation, Route
- [ ] Edge types: EXTENDS, IMPLEMENTS, CALLS, HAS_METHOD, HAS_FIELD, IMPORTS, DEPENDS_ON, CONTAINS, ANNOTATED_BY
- [ ] Node properties: name, filePath, lineNumber, visibility, isAbstract, isStatic, returnType, parameters
- [ ] Edge properties: type, confidence, lineNumber
- [ ] Incremental update: chỉ re-parse files thay đổi, không full rebuild
- [ ] Query response time < 500ms cho graph traversal 3 hops

### FR-03: Force-Directed Graph Visualization
**Priority:** Critical  
**Description:** Render knowledge graph dạng interactive force-directed graph (như GitNexus).

**Acceptance Criteria:**
- [ ] Render nodes với màu sắc theo type:
  - Method = xanh dương (blue)
  - File = đỏ (red/orange)
  - APIEndpoint = xanh lá sáng (bright green)
  - Class = vàng (yellow/amber)
  - DBModel = vàng đậm (dark yellow)
  - Interface = xanh lá (green)
  - Constructor = xanh dương nhạt (cyan)
  - Enum = tím (purple)
  - Record = cam (orange)
- [ ] Render edges với màu theo relationship type (DEFINES=xanh lá, CALLS=đỏ, IMPORTS=xanh dương, EXTENDS=cam, IMPLEMENTS=hồng, HAS_METHOD=xanh nhạt, HANDLES_ROUTE=xanh lá đậm)
- [ ] **Filter Panel (tab Filters):**
  - NODE TYPES: toggle visibility cho mỗi loại (highlight = bật, mờ = tắt) + count
  - EDGE TYPES: toggle visibility cho mỗi relationship type
  - Click toggle → ẩn/hiện nodes/edges theo type
- [ ] **Focus Depth:** Khi focus vào 1 node, chọn độ sâu hiển thị: All | 1 hop | 2 hops | 3 hops | 5 hops (show nodes within N hops of selection)
- [ ] **Legend Panel:** Hiển thị bảng màu node types ở góc dưới trái graph
- [ ] ForceAtlas2 layout algorithm (Web Worker, không block UI)
- [ ] Zoom in/out, pan, drag nodes
- [ ] Click node → hiển thị detail panel (properties, connections)
- [ ] Filter by node type, package, layer
- [ ] Search node by name
- [ ] Highlight execution path khi click một method
- [ ] **Focus Mode:** Click node (từ graph hoặc Explorer) → highlight node + direct connections, dim/mờ tất cả nodes không liên quan (opacity 0.1-0.2)
- [ ] **Clear Focus:** Click vùng trống hoặc nút "Clear" → trở lại full graph
- [ ] **Explorer Panel:** File tree hiển thị source code structure (folders + files), click file → focus node tương ứng trên graph
- [ ] **Code Inspector Panel:** Click file trong Explorer → hiển thị source code (read-only, syntax highlighted) ở giữa, graph focus bên phải. Cho phép xem code + relationships cùng lúc.
- [ ] **Node Detail Panel:** Bên phải hiển thị INCOMING connections (ai gọi tới) + OUTGOING connections (gọi đi đâu) với relationship type
- [ ] **Edge Style Toggle:** User chọn Curved (cong) hoặc Straight (thẳng) trong Settings
- [ ] **Edge Labels on Zoom:** Khi zoom gần (threshold configurable), hiển thị tên relationship type trên mỗi edge (CALLS, IMPORTS, DEFINES, HANDLES_ROUTE...). Zoom xa → ẩn labels để giảm clutter.
- [ ] **Node Size by Importance:** Node size lớn hơn khi có nhiều connections (File lớn hơn Method, hub nodes lớn nhất)
- [ ] Handle 5000+ nodes mượt (60fps)

### FR-04: Use Case Diagram Generation
**Priority:** High  
**Description:** Tự động generate UML Use Case diagram từ knowledge graph.

**Acceptance Criteria:**
- [ ] Detect actors từ: @RestController endpoints → HTTP Client, @Scheduled → System, @KafkaListener → Message Queue
- [ ] Detect use cases từ: Controller methods (public endpoints)
- [ ] Detect <<include>> relationships: shared service calls
- [ ] Detect <<extend>> relationships: optional flows (validation, notification)
- [ ] Render bằng Mermaid.js flowchart LR
- [ ] Export PlantUML syntax
- [ ] Auto-update khi code thay đổi

### FR-05: Class Diagram Generation
**Priority:** High  
**Description:** Tự động generate UML Class diagram từ knowledge graph.

**Acceptance Criteria:**
- [ ] Show classes với fields và methods (visibility indicators: +, -, #)
- [ ] Show inheritance (extends) và implementation (implements) relationships
- [ ] Show associations (field type references)
- [ ] Show dependencies (@Autowired injections)
- [ ] Filter by package hoặc layer
- [ ] Render bằng Mermaid.js classDiagram
- [ ] Support zoom vào specific package

### FR-06: Sequence Diagram Generation
**Priority:** Medium (có thể defer nếu hết thời gian)  
**Description:** Generate sequence diagram từ execution flow.

**Acceptance Criteria:**
- [ ] User chọn một entry point (Controller method)
- [ ] Trace call chain: Controller → Service → Repository → ...
- [ ] Render bằng Mermaid.js sequenceDiagram
- [ ] Show method parameters và return types
- [ ] Max depth configurable (default: 5)

### FR-07: Realtime Update
**Priority:** Critical  
**Description:** Khi developer accept code changes, graph và diagrams tự động update.

**Acceptance Criteria:**
- [ ] Watch project directory cho file changes
- [ ] Trigger re-parse chỉ files thay đổi (incremental)
- [ ] Push update qua WebSocket tới frontend
- [ ] Frontend re-render affected nodes/edges (không full reload)
- [ ] Latency từ file save → graph update < 3 seconds
- [ ] IntelliJ plugin trigger khi user accept code (không mỗi keystroke)

### FR-08: Auto File Watcher (Backend)
**Priority:** Critical  
**Description:** Backend tự động detect file thay đổi và re-analyze, không cần plugin hay thao tác thủ công.

**Acceptance Criteria:**
- [ ] Java WatchService monitor project directory (recursive)
- [ ] Detect .java file create/modify/delete events
- [ ] Debounce 500ms (tránh trigger liên tục khi save nhiều file)
- [ ] Chỉ re-parse files thay đổi (incremental, không full rebuild)
- [ ] Hoạt động với mọi IDE (IntelliJ, VS Code, Eclipse, Vim...)
- [ ] Configurable: ignored paths (build/, target/, .git/)
- [ ] Log: hiển thị file nào đang được re-analyze

### FR-09: REST API
**Priority:** Critical  
**Description:** Backend expose REST API cho frontend và plugin.

**Acceptance Criteria:**
- [ ] `POST /api/projects` — Register project directory
- [ ] `POST /api/projects/{id}/analyze` — Trigger full analysis
- [ ] `GET /api/projects/{id}/graph` — Get full knowledge graph (nodes + edges)
- [ ] `GET /api/projects/{id}/graph?filter=...` — Filtered graph
- [ ] `GET /api/projects/{id}/diagrams/usecase` — Get use case diagram (Mermaid syntax)
- [ ] `GET /api/projects/{id}/diagrams/class?package=...` — Get class diagram
- [ ] `GET /api/projects/{id}/diagrams/sequence?entryPoint=...` — Get sequence diagram
- [ ] `GET /api/projects/{id}/nodes/{nodeId}` — Node detail with connections
- [ ] `GET /api/projects/{id}/impact?target=...` — Blast radius analysis
- [ ] WebSocket endpoint: `/ws/graph-updates`

---

## 3. Non-Functional Requirements

### NFR-01: Performance
| Metric | Target |
|--------|--------|
| Parse 500 Java files | < 30 seconds |
| Incremental update (1 file change) | < 3 seconds |
| Graph query (3 hops) | < 500ms |
| Frontend render 5000 nodes | 60fps |
| WebSocket latency | < 200ms |

### NFR-02: Scalability
- Support projects up to 2000 Java files
- Support knowledge graphs up to 50,000 nodes
- Support concurrent 5 users (server mode)

### NFR-03: Reliability
- Graceful handling of parse errors (skip unparseable files, report warnings)
- Neo4j connection retry with exponential backoff
- WebSocket auto-reconnect

### NFR-04: Deployment
- Docker Compose for full stack (Spring Boot + Neo4j + Vue frontend)
- Single JAR for CLI mode (embedded Neo4j or file-based fallback)
- Không cần cài plugin — hoạt động với mọi IDE

---

## 4. Tech Stack (Confirmed)

| Layer | Technology | Version |
|-------|-----------|---------|
| Backend | Spring Boot | 3.3+ |
| Language | Java | 21+ (virtual threads) |
| Parser | JavaParser | 3.26+ |
| Database | Neo4j Community | 5.x |
| DB Driver | Spring Data Neo4j | 7.x |
| MCP Server | Spring AI MCP Boot Starter | 1.x |
| WebSocket | Spring WebSocket (STOMP) | — |
| File Watcher | Java WatchService (JDK built-in) | — |
| Frontend | Vue 3 (Composition API) | 3.4+ |
| Graph Render | Sigma.js | 3.x |
| Graph Data | Graphology | 0.25+ |
| Layout | graphology-layout-forceatlas2 | — |
| UML Render | Mermaid.js | 11.x |
| State | Pinia | 2.x |
| Build (FE) | Vite | 6.x |
| Build (BE) | Maven | 3.9+ |
| Container | Docker + Docker Compose | — |

---

## 5. AI Integration Requirements (Vibe Coding Support)

### FR-10: MCP Server (Model Context Protocol)
**Priority:** High  
**Description:** VibeGraph expose MCP tools để AI coding assistants (Cursor, Kiro, Claude Code) tự động đọc project context trước khi generate code.

**Acceptance Criteria:**
- [ ] MCP Server chạy cùng Spring Boot backend (Spring AI MCP Boot Starter)
- [ ] Tool: `get_project_architecture` — trả về layers, patterns, naming conventions
- [ ] Tool: `get_class_context(className)` — trả về class diagram + related classes + methods
- [ ] Tool: `get_layer_pattern(layer)` — trả về pattern của Controller/Service/Repository layer
- [ ] Tool: `get_impact_analysis(target)` — trả về blast radius khi sửa target
- [ ] Tool: `get_usecase_context(feature)` — trả về use case liên quan
- [ ] Tool: `get_coding_rules` — trả về DO/DON'T rules dựa trên architecture hiện tại
- [ ] Transport: Streamable HTTP (chuẩn MCP mới nhất, thay thế SSE)
- [ ] Compatible với: Cursor, Kiro, Claude Code, GitHub Copilot (MCP-enabled)

### FR-11: Context API
**Priority:** High  
**Description:** REST endpoint cung cấp structured context cho AI tools không hỗ trợ MCP.

**Acceptance Criteria:**
- [ ] `GET /api/projects/{id}/context` — full architecture summary
- [ ] `GET /api/projects/{id}/context?scope={className}` — scoped context
- [ ] Response bao gồm: architecture layers, related classes, patterns, warnings, class diagram (Mermaid)
- [ ] Response format: JSON với Markdown-embedded content (AI-friendly)
- [ ] Latency < 1 second

### FR-12: Auto-Generate Steering Files
**Priority:** High  
**Description:** VibeGraph tự động generate/update steering files cho AI tools mỗi khi project thay đổi.

**Acceptance Criteria:**
- [ ] Generate `.kiro/steering/vibegraph-context.md` (cho Kiro)
- [ ] Generate `.cursorrules` hoặc `.cursor/rules/vibegraph.mdc` (cho Cursor)
- [ ] Generate `CLAUDE.md` section (cho Claude Code)
- [ ] Nội dung bao gồm: project layers, class list, naming patterns, DO/DON'T rules
- [ ] Auto-update khi graph thay đổi (debounced, không mỗi file save)
- [ ] Configurable: user chọn generate cho tool nào
- [ ] Không overwrite user's custom rules — append/update VibeGraph section only

### FR-13: Pre-Code Hook Templates
**Priority:** Medium  
**Description:** Cung cấp hook templates để AI bắt buộc đọc VibeGraph context trước khi write code.

**Acceptance Criteria:**
- [ ] Generate hook file cho Kiro (`.kiro/hooks/vibegraph-precheck.json`)
- [ ] Generate hook config cho Cursor (rules-based)
- [ ] Hook logic: trước khi AI write file → gọi VibeGraph context API → inject vào prompt
- [ ] Configurable: strict mode (block nếu không đọc) vs advisory mode (warn only)
- [ ] Template cho Claude Code (CLAUDE.md rules section)

---

## 6. Out of Scope (Phase 2)

- Multi-language support (TypeScript, Python, Go, Kotlin, C#)
- Multi-user authentication & authorization
- Git history analysis (blame, evolution)
- AI-powered code suggestions based on graph
- IntelliJ Plugin (status bar, toolbar button trong IDE)
- Cloud deployment (SaaS mode)
- Export to Confluence/Notion
- Comparison between branches
- MCP Client (VibeGraph gọi AI) — Phase 2, chỉ làm MCP Server (AI gọi VibeGraph) trong Phase 1

---

## 6. Sprint Plan (6 Weeks)

### Sprint 1 (Week 1-2): Foundation
- Parser engine: JavaParser → nodes/edges extraction
- Neo4j schema design + Spring Data Neo4j setup
- Vue 3 + Sigma.js project scaffold
- Basic REST API (analyze + get graph)
- IntelliJ plugin skeleton

### Sprint 2 (Week 3-4): Core Features
- Full relationship extraction (CALLS, EXTENDS, IMPLEMENTS)
- Spring Boot annotation detection
- Force graph interactive UI (zoom, filter, search, click)
- Use Case diagram generator
- Class diagram generator
- WebSocket realtime pipeline
- File watcher service

### Sprint 3 (Week 5-6): Integration & Polish
- IntelliJ plugin ↔ Backend integration
- Sequence diagram (basic)
- Incremental update (only changed files)
- UI polish (dark theme, responsive, export)
- Docker Compose packaging
- Integration testing
- Demo preparation

---

## 7. Definition of Done

Một feature được coi là "Done" khi:
- [ ] Code reviewed bởi ít nhất 1 team member
- [ ] Unit tests pass (coverage > 70% cho business logic)
- [ ] Integration test pass
- [ ] Không có Critical/High bugs
- [ ] API documented (Swagger/OpenAPI)
- [ ] Chạy được trên Docker Compose
