# VibeGraph — Task Breakdown (6 Weeks, 5 Devs)

---

## Team Assignment

| Dev | Role | Focus Area |
|-----|------|------------|
| **Dev 1** | Backend Lead | Parser Engine (JavaParser) |
| **Dev 2** | Backend | API + Neo4j + WebSocket |
| **Dev 3** | Frontend Lead | Sigma.js Graph Visualization |
| **Dev 4** | Frontend | Mermaid Diagrams + UI |
| **Dev 5** | Integration | File Watcher + MCP + DevOps |

---

## Sprint 1: Foundation (Week 1-2)

### Dev 1 — Parser Engine
| # | Task | Days | Deliverable |
|---|------|------|-------------|
| 1.1 | Setup vibegraph-core module, add JavaParser dependency | 0.5 | Maven module compiles |
| 1.2 | Implement ClassVisitor: extract Class, Interface, Enum nodes | 2 | Unit test: parse sample → nodes |
| 1.3 | Implement MethodVisitor: extract Method nodes with params/return | 1.5 | Unit test: methods extracted |
| 1.4 | Implement FieldVisitor: extract Field nodes with types | 1 | Unit test: fields extracted |
| 1.5 | Extract structural edges: HAS_METHOD, HAS_FIELD, CONTAINS | 1 | Unit test: edges correct |
| 1.6 | Extract EXTENDS, IMPLEMENTS edges | 1 | Unit test: inheritance tree |
| 1.7 | Setup JavaParser Symbol Solver for type resolution | 2 | Symbol solver resolves imports |
| 1.8 | Extract IMPORTS edges | 1 | Unit test: import graph |

### Dev 2 — API + Neo4j
| # | Task | Days | Deliverable |
|---|------|------|-------------|
| 2.1 | Setup vibegraph-server module (Spring Boot 3.3, Java 21) | 0.5 | App starts |
| 2.2 | Neo4j Docker setup + Spring Data Neo4j config | 1 | Connection works |
| 2.3 | Define Neo4j node models (@Node, @Relationship) | 2 | All node/edge types mapped |
| 2.4 | Implement GraphRepository (CRUD + batch operations) | 2 | Save/query graph works |
| 2.5 | REST: POST /api/projects (register project) | 1 | API test passes |
| 2.6 | REST: POST /api/projects/{id}/analyze (trigger) | 1 | Triggers parser, saves to Neo4j |
| 2.7 | REST: GET /api/projects/{id}/graph (full graph) | 1 | Returns nodes + edges JSON |
| 2.8 | REST: GET /api/projects/{id}/graph/neighbors/{nodeId} | 1 | Returns N-hop neighborhood |

### Dev 3 — Graph Visualization
| # | Task | Days | Deliverable |
|---|------|------|-------------|
| 3.1 | Setup vibegraph-web (Vue 3 + Vite + TypeScript) | 0.5 | Dev server runs |
| 3.2 | Install Sigma.js + Graphology + ForceAtlas2 | 0.5 | Dependencies resolve |
| 3.3 | Implement useSigma composable (init, destroy, refresh) | 2 | Sigma renders empty graph |
| 3.4 | Implement graph-adapter: API response → Graphology graph | 2 | Nodes/edges appear on screen |
| 3.5 | Node colors by type (Class=yellow, Method=blue, etc.) | 1 | Visual differentiation |
| 3.6 | Edge colors by relationship type | 1 | CALLS=red, EXTENDS=green, etc. |
| 3.7 | ForceAtlas2 layout (Web Worker) | 1.5 | Nodes auto-arrange |
| 3.8 | Basic interactions: zoom, pan, click node | 1.5 | Interactive graph |

### Dev 4 — Diagrams + UI Shell
| # | Task | Days | Deliverable |
|---|------|------|-------------|
| 4.1 | Install Mermaid.js, create MermaidRenderer component | 1 | Renders sample diagram |
| 4.2 | App layout: header, side panel, main canvas, diagram panel | 2 | Layout skeleton |
| 4.3 | Project selector component | 1 | Dropdown with projects |
| 4.4 | Node detail panel (shows properties on click) | 2 | Click node → see info |
| 4.5 | Filter panel UI (checkboxes for node types) | 2 | Filter toggles work |
| 4.6 | Search bar component (calls API, highlights results) | 2 | Search finds nodes |

### Dev 5 — DevOps + File Watcher
| # | Task | Days | Deliverable |
|---|------|------|-------------|
| 5.1 | File Watcher service (Java WatchService, recursive, debounce) | 2.5 | Detects .java file changes |
| 5.2 | Configurable ignore patterns (build/, target/, .git/) | 1 | Filters work |
| 5.3 | Watcher → trigger incremental re-parse pipeline | 1.5 | Changed file re-analyzed |
| 5.4 | Docker Compose setup (Neo4j + backend + frontend) | 1.5 | `docker compose up` works |
| 5.5 | CI pipeline setup (GitHub Actions: build + test) | 1 | PR checks pass |
| 5.6 | CLI entry point: `java -jar vibegraph.jar watch /path` | 1.5 | CLI starts watcher |
| 5.7 | README.md + quickstart guide | 1 | New dev can setup in 10 min |

---

## Sprint 2: Core Features (Week 3-4)

### Dev 1 — Advanced Parsing
| # | Task | Days | Deliverable |
|---|------|------|-------------|
| 1.9 | Extract CALLS edges (method invocations → resolved targets) | 3 | Call graph accurate >90% |
| 1.10 | Handle constructor calls, static method calls | 1 | Edge cases covered |
| 1.11 | Spring annotation detection (@Controller, @Service, @Repository) | 2 | springLayer property set |
| 1.12 | Detect @RequestMapping → create Route nodes | 1.5 | HTTP endpoints extracted |
| 1.13 | Detect @Autowired → create INJECTS edges | 1 | DI graph visible |
| 1.14 | Detect @Scheduled, @KafkaListener → enrich nodes | 1 | Scheduled/event nodes |
| 1.15 | Handle lambda expressions and method references | 1 | No crash on lambdas |

### Dev 2 — Realtime + Diagrams API
| # | Task | Days | Deliverable |
|---|------|------|-------------|
| 2.9 | WebSocket config (STOMP + SockJS) | 1 | WS connection works |
| 2.10 | File watcher service (Java WatchService) | 2 | Detects .java changes |
| 2.11 | Incremental analysis (re-parse changed file only) | 2 | Only affected nodes updated |
| 2.12 | WebSocket push on graph change | 1 | Frontend receives updates |
| 2.13 | Use Case diagram API: extract actors + use cases from graph | 2 | Returns Mermaid syntax |
| 2.14 | Class diagram API: extract classes + relationships per package | 2 | Returns Mermaid syntax |

### Dev 3 — Graph Interactions
| # | Task | Days | Deliverable |
|---|------|------|-------------|
| 3.9 | Node click → highlight connected nodes/edges | 2 | Visual focus |
| 3.10 | Filter by node type (show/hide) | 1.5 | Toggle Class, Method, etc. |
| 3.11 | Filter by package (subgraph view) | 1.5 | Package isolation |
| 3.12 | Search → zoom to node + highlight | 1.5 | Search navigates graph |
| 3.13 | Execution path highlight (click method → trace CALLS) | 2 | Red path through graph |
| 3.14 | WebSocket integration (receive updates → patch graph) | 2 | Realtime graph updates |

### Dev 4 — Diagram Rendering
| # | Task | Days | Deliverable |
|---|------|------|-------------|
| 4.7 | Use Case diagram view (fetch + render Mermaid) | 2 | Diagram displays |
| 4.8 | Class diagram view (package selector + render) | 2 | Diagram displays |
| 4.9 | Diagram auto-refresh on WebSocket update | 1.5 | Diagrams stay current |
| 4.10 | Diagram export (copy Mermaid, download SVG/PNG) | 1.5 | Export works |
| 4.11 | Tab navigation between Graph / Use Case / Class views | 1 | Smooth tab switching |
| 4.12 | Dark theme implementation | 2 | Consistent dark UI |

### Dev 5 — Realtime + Testing
| # | Task | Days | Deliverable |
|---|------|------|-------------|
| 5.8 | WebSocket integration: watcher → notify frontend on change | 2 | Frontend receives updates |
| 5.9 | Incremental update pipeline: only re-parse changed files | 2 | Fast update (< 3s) |
| 5.10 | Content-hash check: skip files not actually changed | 1.5 | No redundant re-parse |
| 5.11 | Integration test: file change → graph update → WS push | 2 | E2E flow works |
| 5.12 | Load test: watch project with 500 files, rapid saves | 1.5 | No crash, no memory leak |
| 5.13 | Logging + monitoring (structured logs, health endpoint) | 1 | Observable system |

---

## Sprint 3: Polish & Ship (Week 5-6)

### Dev 1 — Robustness + MCP
| # | Task | Days | Deliverable |
|---|------|------|-------------|
| 1.16 | Handle parse errors gracefully (skip bad files, log warnings) | 1.5 | No crashes on invalid Java |
| 1.17 | Performance optimization: parallel file parsing | 2 | 500 files < 30s |
| 1.18 | Content-hash caching (skip unchanged files) | 2 | Incremental fast |
| 1.19 | MCP Server setup (Spring AI MCP Boot Starter) | 1.5 | MCP endpoint responds |
| 1.20 | MCP Tools: get_project_architecture, get_class_context | 2 | Tools callable from AI |
| 1.21 | Unit test coverage > 70% for parser module | 1.5 | Tests pass |

### Dev 2 — API Polish + Context
| # | Task | Days | Deliverable |
|---|------|------|-------------|
| 2.15 | Context API: GET /api/projects/{id}/context | 2 | Returns architecture summary |
| 2.16 | Sequence diagram API (entry point → call trace → Mermaid) | 2 | Returns sequence Mermaid |
| 2.17 | Impact analysis API (blast radius from a node) | 1.5 | Returns affected nodes |
| 2.18 | MCP Tools: get_impact_analysis, get_coding_rules, get_layer_pattern | 2 | All MCP tools work |
| 2.19 | Swagger/OpenAPI documentation | 1 | API docs accessible |
| 2.20 | Error handling + Neo4j query optimization | 1.5 | Clean responses, < 500ms |

### Dev 3 — UI Polish
| # | Task | Days | Deliverable |
|---|------|------|-------------|
| 3.15 | Graph legend (node type colors + edge type colors) | 1 | Legend visible |
| 3.16 | Minimap / overview panel | 1.5 | Navigate large graphs |
| 3.17 | Responsive layout (tablet + desktop) | 2 | Works on 768px+ |
| 3.18 | Loading states + skeleton screens | 1 | No blank screens |
| 3.19 | Graph statistics panel (node count, edge count, layers) | 1 | Stats visible |
| 3.20 | Performance: handle 5000+ nodes smoothly | 2 | No lag |
| 3.21 | Keyboard shortcuts (Ctrl+F search, Esc deselect) | 1 | Shortcuts work |

### Dev 4 — Sequence Diagram + Steering Files
| # | Task | Days | Deliverable |
|---|------|------|-------------|
| 4.13 | Sequence diagram view (select entry → render) | 2.5 | Diagram displays |
| 4.14 | Steering file generator: .kiro/steering/vibegraph-context.md | 2 | File auto-generated |
| 4.15 | Steering file generator: .cursor/rules/vibegraph.mdc | 1.5 | File auto-generated |
| 4.16 | Steering file generator: CLAUDE.md section | 1 | Section appended |
| 4.17 | Hook template generator (.kiro/hooks/vibegraph-precheck.json) | 1.5 | Hook file generated |
| 4.18 | Frontend unit tests (key composables) | 1.5 | Tests pass |

### Dev 5 — DevOps + Demo
| # | Task | Days | Deliverable |
|---|------|------|-------------|
| 5.14 | Docker Compose production config (nginx, health checks) | 2 | Production-ready compose |
| 5.15 | CLI mode: `java -jar vibegraph.jar analyze /path/to/project` | 1.5 | CLI works standalone |
| 5.16 | MCP config templates (mcp.json for Cursor, Kiro, Claude) | 1 | Config files ready |
| 5.17 | End-to-end test: full flow from code change → graph update | 1.5 | E2E passes |
| 5.18 | Demo preparation: sample Spring Boot project for showcase | 1.5 | Demo project ready |
| 5.19 | Documentation: setup guide, MCP integration guide | 2 | Docs complete |

---

## Milestones

| Week | Milestone | Demo |
|------|-----------|------|
| 2 | **M1: Parse & Render** | Parse Java project → see force graph in browser |
| 4 | **M2: Full Features** | Realtime update + Use Case + Class diagrams working |
| 6 | **M3: Ship MVP** | Docker Compose + IntelliJ plugin + MCP Server + AI integration |

---

## Risk Mitigation

| Risk | Probability | Mitigation |
|------|-------------|------------|
| JavaParser Symbol Solver fails on complex generics | Medium | Fallback: mark unresolved calls with confidence=0.5 |
| Sigma.js + Vue integration issues | Low | Reference GitNexus useSigma pattern |
| Neo4j performance on large graphs | Low | Add indexes early, paginate API responses |
| WatchService misses events on some OS | Low | Fallback: periodic polling every 5s |
| Sequence diagram too complex | Medium | Limit depth to 5, defer advanced features |
| Spring AI MCP Starter API changes | Low | Pin version, follow Spring AI release notes |
