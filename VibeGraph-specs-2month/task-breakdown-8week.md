# VibeGraph — Task Breakdown (8 Weeks, 5 Devs)

## Team
- **Dev 1:** Backend Lead — Parser Engine
- **Dev 2:** Backend — API + Neo4j + WebSocket + GitHub Import
- **Dev 3:** Frontend Lead — Sigma.js Force Graph
- **Dev 4:** Frontend — Mermaid + UI Shell
- **Dev 5:** Integration — File Watcher + MCP + DevOps + Deploy

## Sprint Plan

| Sprint | Weeks | Goal |
|---|---|---|
| 1 | 1-2 | Foundation: parse + render trống → có graph |
| 2 | 3-4 | Realtime + diagrams + GitHub import |
| 3 | 5-6 | MCP Server + polish |
| 4 (Buffer) | 7-8 | Deploy + landing page + bug fix |

---

## Sprint 1 — Foundation (Week 1-2)

### Dev 1 — Parser
| # | Task | Days |
|---|---|---|
| 1.1 | Setup `vibegraph-core` Maven module + JavaParser 3.26 | 0.5 |
| 1.2 | ClassVisitor → Class/Interface/Enum nodes | 2 |
| 1.3 | MethodVisitor → Method nodes (params, return, throws) | 1.5 |
| 1.4 | FieldVisitor → Field nodes | 1 |
| 1.5 | Structural edges: HAS_METHOD, HAS_FIELD, CONTAINS, DEFINES | 1 |
| 1.6 | EXTENDS, IMPLEMENTS edges | 1 |
| 1.7 | Symbol Solver setup + IMPORTS edges | 2 |

### Dev 2 — API + Neo4j (chú ý: tách interface từ đầu)
| # | Task | Days |
|---|---|---|
| 2.1 | Setup `vibegraph-server` Spring Boot 3.3 Java 21 | 0.5 |
| 2.2 | Neo4j Docker + Spring Data Neo4j config | 1 |
| 2.3 | Apply schema constraints + indexes từ neo4j-schema.md | 1 |
| 2.4 | **GraphRepository INTERFACE + transport DTOs (NodeData, EdgeData)** | 1 |
| 2.5 | **Neo4jGraphRepository impl trong `impl/neo4j/` subpackage** | 2 |
| 2.6 | **ArchUnit test forbid leak Neo4j imports** | 0.5 |
| 2.7 | REST: POST /api/projects, POST /analyze, GET /graph | 2 |
| 2.8 | GET /graph/neighbors/{nodeId}?hops=N (APOC subgraphAll) | 1 |

### Dev 3 — Graph Visualization
| # | Task | Days |
|---|---|---|
| 3.1 | Setup `vibegraph-web` Vue 3 + Vite + TS | 0.5 |
| 3.2 | Install sigma, graphology, forceatlas2, edge-curve, axios | 0.5 |
| 3.3 | `useSigma` composable: init/destroy/refresh | 2 |
| 3.4 | `graphAdapter.ts`: API response → Graphology graph | 1.5 |
| 3.5 | Node colors by type (9 types theo ảnh GitNexus) | 1 |
| 3.6 | Edge colors by relationship type (8 types) | 1 |
| 3.7 | ForceAtlas2 layout trong Web Worker | 1.5 |
| 3.8 | Interactions: zoom, pan, drag, click | 1.5 |
| 3.9 | Controls panel top-right (Left/Right click, Scroll) | 0.5 |

### Dev 4 — UI Shell + Diagrams base
| # | Task | Days |
|---|---|---|
| 4.1 | Install Mermaid.js + MermaidRenderer component | 1 |
| 4.2 | Layout: HeaderBar + SidePanel (Filters/Explorer/Flows tabs) + MainCanvas | 2 |
| 4.3 | Project selector dropdown | 0.5 |
| 4.4 | FilterPanel: NODE TYPES + EDGE TYPES toggle + count | 2 |
| 4.5 | LegendPanel (bottom-left, color legend) | 0.5 |
| 4.6 | NodeDetailPanel (right side, INCOMING/OUTGOING) | 2 |
| 4.7 | SearchBar (calls API, highlight result) | 1.5 |

### Dev 5 — DevOps + Watcher base
| # | Task | Days |
|---|---|---|
| 5.1 | Docker Compose (neo4j + backend + frontend) | 1.5 |
| 5.2 | File Watcher service (Java WatchService recursive, debounce 500ms) | 2 |
| 5.3 | Ignored paths config (build/, target/, .git/, node_modules/) | 0.5 |
| 5.4 | Watcher → trigger incremental re-parse pipeline | 1.5 |
| 5.5 | GitHub Actions CI (build + test on PR) | 1 |
| 5.6 | Auto-deploy `dev.vibegraph.com` on merge to main | 1.5 |
| 5.7 | README.md + quickstart guide | 1 |

**Milestone tuần 2:** Đọc folder Java local → parse → graph hiện trên `localhost:3000`. Deploy `dev.vibegraph.com` chạy được.

---

## Sprint 2 — Core Features (Week 3-4)

### Dev 1 — Advanced parsing
| # | Task | Days |
|---|---|---|
| 1.8 | CALLS edges với Symbol Solver (resolved/interface/unresolved) | 3 |
| 1.9 | Constructor calls, static method calls | 1 |
| 1.10 | Spring annotations: @Controller/@Service/@Repository → springLayer | 2 |
| 1.11 | @RequestMapping/@GetMapping/@PostMapping → Route nodes + HANDLES_ROUTE | 2 |
| 1.12 | @Autowired/constructor injection → INJECTS edges | 1 |
| 1.13 | Lambda + method references handling (no crash) | 1 |

### Dev 2 — GitHub Import (Tarball) + Diagrams API + WebSocket + CLI Server
| # | Task | Days |
|---|---|---|
| 2.9 | WebSocket STOMP config + `/ws/graph-updates` endpoint | 1 |
| 2.10 | **TarballImportService: pre-flight check (HEAD repo API) + reject private/oversize** | 1 |
| 2.11 | **Stream tarball qua commons-compress (Gzip + Tar), parse in-memory không ghi disk** | 1.5 |
| 2.12 | **POST /api/projects/import-github endpoint + GITHUB_TOKEN config + rate limit guard** | 1 |
| 2.13 | Incremental analysis: re-parse 1 file, update Neo4j | 2 |
| 2.14 | WS push on graph change (added/changed/removed) | 1.5 |
| 2.15 | **WebSocket endpoint nhận CLI diff + API key auth validation** | 1 |
| 2.16 | Use Case diagram API → Mermaid syntax | 2 |
| 2.17 | Class diagram API (filter by package) → Mermaid | 2 |

### Dev 3 — Focus Mode + Performance
| # | Task | Days |
|---|---|---|
| 3.10 | Focus Mode: click node → highlight neighbors + dim unrelated (Sigma reducers) | 2.5 |
| 3.11 | Focus Depth control: All / 1 / 2 / 3 / 5 hops | 1 |
| 3.12 | Filter visibility (node type, edge type toggle ẩn/hiện) | 1.5 |
| 3.13 | Edge labels on zoom threshold (CALLS, IMPORTS labels show khi zoom gần) | 1.5 |
| 3.14 | Node size by importance (số connections) | 1 |
| 3.15 | WebSocket integration: receive updates → patch Sigma graph | 2 |
| 3.16 | Edge style toggle (Curved/Straight) trong Settings | 1 |

### Dev 4 — Diagrams + Explorer
| # | Task | Days |
|---|---|---|
| 4.8 | UseCaseDiagram.vue: fetch + render Mermaid | 2 |
| 4.9 | ClassDiagram.vue: package selector + render | 2 |
| 4.10 | ExplorerPanel.vue: file tree từ project structure, click → focus graph | 2.5 |
| 4.11 | DiagramPanel tabs (Use Case / Class) | 1 |
| 4.12 | Diagram auto-refresh on WS update | 1 |
| 4.13 | Dark theme polish (giống GitNexus screenshot) | 1.5 |

### Dev 5 — Realtime pipeline + CLI Foundation + Testing
| # | Task | Days |
|---|---|---|
| 5.8 | Watcher → WebSocket integration test E2E | 1.5 |
| 5.9 | Content-hash check (SHA-256, skip unchanged file) | 1 |
| 5.10 | Load test: 500 files, rapid saves (no memory leak, < 3s update) | 1 |
| 5.11 | Structured logging + /actuator/health endpoint | 0.5 |
| 5.12 | **GitHub Import end-to-end test** | 1 |
| 5.13 | **`vibegraph-cli` Maven module setup (reuse vibegraph-core)** | 0.5 |
| 5.14 | **DirectoryWatcher (io.methvin) + DiffExtractor (JavaParser metadata diff)** | 1.5 |
| 5.15 | **WebSocket client + API key auth (CLI → Server)** | 1 |
| 5.16 | Auto-deploy pipeline polish | 0.5 |

**Milestone tuần 4:** User paste GitHub URL → backend stream tarball, parse in-memory → graph hiện. Save file local → graph cập nhật < 3s. Use Case + Class diagrams hiển thị.

---

## Sprint 3 — MCP + Polish (Week 5-6)

### Dev 1 — Robustness + MCP tools
| # | Task | Days |
|---|---|---|
| 1.14 | Parse error handling (skip bad files, log warning, không crash) | 1.5 |
| 1.15 | Parallel file parsing (virtual threads Java 21) | 2 |
| 1.16 | Content-hash caching (in-memory map filePath → SHA-256) | 1.5 |
| 1.17 | Unit tests parser module (coverage > 70%) | 2 |
| 1.18 | Detect patterns: layered architecture, DI pattern, naming convention | 2 |

### Dev 2 — Context + Impact + MCP API
| # | Task | Days |
|---|---|---|
| 2.18 | **Spring AI MCP Boot Starter setup** | 1 |
| 2.19 | **Tool: `get_project_architecture`** | 1.5 |
| 2.20 | **Tool: `get_class_context`** | 1.5 |
| 2.21 | **Tool: `get_layer_pattern`** | 1.5 |
| 2.22 | **Tool: `get_impact_analysis`** | 1.5 |
| 2.23 | Impact analysis Cypher query (5 hops) | 1 |
| 2.24 | Swagger/OpenAPI docs | 0.5 |

### Dev 3 — UI Polish
| # | Task | Days |
|---|---|---|
| 3.17 | Graph statistics panel (node count, edge count, layers) | 1 |
| 3.18 | Loading states + skeleton screens | 1 |
| 3.19 | Keyboard shortcuts (Ctrl+F search, Esc deselect) | 1 |
| 3.20 | Minimap / overview panel | 1.5 |
| 3.21 | Performance optimization 5000+ nodes | 2 |
| 3.22 | Responsive layout 768px+ | 2 |

### Dev 4 — Final UI + Tests
| # | Task | Days |
|---|---|---|
| 4.14 | CodeInspector.vue: show source code khi click file Explorer (Monaco read-only) | 2.5 |
| 4.15 | FlowsPanel.vue: list execution flows | 1.5 |
| 4.16 | Export diagram (copy Mermaid, download SVG/PNG) | 1.5 |
| 4.17 | Frontend unit tests key composables | 1.5 |
| 4.18 | Empty states + error UI | 1 |

### Dev 5 — Demo + MCP config + CLI Commands
| # | Task | Days |
|---|---|---|
| 5.17 | **CLI commands: LoginCommand, WatchCommand, SyncCommand (picocli)** | 1 |
| 5.18 | **npm wrapper package (vibegraph-cli-npm): bin/vibegraph.js + postinstall Java check** | 0.5 |
| 5.19 | **CLI E2E test: watch folder → create/delete .java → verify graph update < 1s** | 1 |
| 5.20 | Demo Spring Boot sample project (Pet Clinic hoặc tự build) | 1.5 |
| 5.21 | **MCP config templates: mcp.json cho Cursor, Kiro, Claude Code** | 1 |
| 5.22 | E2E test full flow (GitHub import → graph → MCP query) | 2 |
| 5.23 | Production docker-compose (nginx + Let's Encrypt) | 2 |
| 5.24 | Setup guide + MCP integration guide + CLI usage docs | 2 |

**Milestone tuần 6:** Toàn bộ MVP done. MCP test thành công với Cursor/Claude Code. CLI `vibegraph watch` hoạt động real-time.

---

## Sprint 4 — Deploy + Buffer (Week 7-8)

### All hands
| Task | Owner | Days |
|---|---|---|
| Mua domain `vibegraph.com` + DNS setup | Dev 5 | 0.5 |
| VPS Hetzner setup + Docker install + firewall | Dev 5 | 1 |
| Production deploy + SSL Let's Encrypt | Dev 5 | 1.5 |
| Landing page (1 trang giới thiệu + demo video) | Dev 4 | 2 |
| **Presentation HTML cho khách hàng (non-tech)** | Dev 4 | 1 |
| Bug fix backlog | All | 3 |
| Performance optimization | Dev 1 + 3 | 2 |
| Documentation polish | Dev 5 | 1.5 |
| Demo video recording | Dev 5 | 1 |
| Final integration test | Dev 5 + all | 1 |

**Milestone tuần 8:** `vibegraph.com` live. User vào web paste GitHub URL → thấy graph. AI tool kết nối MCP qua URL public.

---

## Risk Mitigation

| Risk | P | Mitigation |
|---|---|---|
| JavaParser fail trên generics phức tạp | M | confidence=0.5, log warning, tiếp tục parse |
| Symbol Solver fail trên project lớn | M | Stub method fallback (đã có trong schema) |
| Sigma.js + Vue tích hợp khó | L | Tham khảo GitNexus useSigma pattern trực tiếp |
| Watcher miss event trên Windows | M | Polling fallback every 5s |
| MCP Spring AI API breaking change | L | Pin version 1.0.x |
| Hetzner VPS không đủ RAM | L | Upgrade lên CX32 (8GB) — chỉ +$3/tháng |
| GitHub tarball repo lớn | M | Pre-flight check size via HEAD API, reject > 100MB |
| GitHub API rate limit | M | Bắt buộc GITHUB_TOKEN (5000 req/giờ), log warning khi gần limit |
| Tarball chứa symlink/special files | L | Skip entry không phải regular file trong tar stream |
| CLI yêu cầu Java 21 trên máy user | M | npm postinstall check version, hướng dẫn cài. Post-2-month: GraalVM native-image |
| CLI WebSocket disconnect giữa chừng | M | Auto-reconnect với exponential backoff, queue diff offline |
| CLI parse chậm project lớn (initial scan) | L | Parallel parse virtual threads, progress bar CLI |
