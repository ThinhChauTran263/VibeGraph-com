# VibeGraph — File Checklist

**Mục đích:** Nguồn tham chiếu duy nhất cho toàn bộ files cần tạo trong dự án.
**Cập nhật:** Tick `[x]` khi đã tạo file (dù là skeleton).
**Quy ước:**
- ✅ = Đã tạo (có nội dung)
- ⚠️ = Skeleton (file rỗng/comment)
- ❌ = Chưa tạo

---

## 📦 BACKEND — `D:\Users\User\IdeaProjects\VibeGraph\src\main\java\com\vibegraph\`

### Root
- [x] ⚠️ `VibeGraphApplication.java`

### `common/` — Shared

#### `common/config/`
- [ ] `Neo4jConfig.java` — Spring Data Neo4j configuration
- [ ] `WebSocketConfig.java` — STOMP WebSocket setup (`/ws/graph-updates`)
- [ ] `CorsConfig.java` — CORS policy (allow Vue dev server)
- [ ] `McpServerConfig.java` — MCP Server bean registration
- [ ] `AsyncConfig.java` — Virtual threads executor (Java 21)

#### `common/exception/`
- [ ] `GlobalExceptionHandler.java` — `@ControllerAdvice`
- [ ] `ProjectNotFoundException.java`
- [ ] `ParseException.java`
- [ ] `NodeNotFoundException.java`

#### `common/dto/request/`
- [ ] `PaginationRequest.java`

#### `common/dto/response/`
- [ ] `ApiResponse.java` — Wrapper `{success, data, error}`
- [ ] `ErrorResponse.java`

#### `common/node/`
- [ ] `BaseNode.java` — Abstract `@Node` parent

#### `common/util/`
- [ ] `FileUtils.java` — File I/O helpers
- [ ] `HashUtils.java` — SHA-256 for incremental cache
- [ ] `JsonUtils.java` — JSON helpers

---

### `parser/` — Java Parser Engine

#### `parser/service/`
- [ ] `ParserService.java` — interface
- [ ] `SymbolResolverService.java` — interface
- [ ] `CallGraphBuilderService.java` — interface

#### `parser/service/impl/`
- [ ] `ParserServiceImpl.java` — JavaParser orchestrator
- [ ] `SymbolResolverServiceImpl.java` — JavaParser Symbol Solver
- [ ] `CallGraphBuilderServiceImpl.java` — Build CALLS edges

#### `parser/visitor/`
- [ ] `ClassVisitor.java` — Extract Class/Interface/Enum nodes
- [ ] `MethodVisitor.java` — Extract Method nodes
- [ ] `FieldVisitor.java` — Extract Field nodes
- [ ] `SpringAnnotationVisitor.java` — Detect `@Controller`, `@Service`, `@Repository`
- [ ] `ImportVisitor.java` — Extract IMPORTS edges

#### `parser/node/`
- [ ] `ParseResult.java` — Internal result model

#### `parser/dto/request/`
- [ ] `ParseFileRequest.java`

#### `parser/dto/response/`
- [ ] `ParseResultResponse.java`

---

### `graph/` — Knowledge Graph (Neo4j)

#### `graph/controller/`
- [ ] `GraphController.java` — `GET /api/projects/{id}/graph`
- [ ] `ProjectController.java` — `POST /api/projects`
- [ ] `ImpactController.java` — `GET /api/projects/{id}/impact`

#### `graph/service/`
- [ ] `GraphService.java` — interface
- [ ] `ProjectService.java` — interface
- [ ] `AnalyzeService.java` — interface
- [ ] `ImpactService.java` — interface

#### `graph/service/impl/`
- [ ] `GraphServiceImpl.java`
- [ ] `ProjectServiceImpl.java`
- [ ] `AnalyzeServiceImpl.java`
- [ ] `ImpactServiceImpl.java`

#### `graph/node/` — Neo4j `@Node` classes
- [ ] `ClassNode.java`
- [ ] `InterfaceNode.java`
- [ ] `EnumNode.java`
- [ ] `MethodNode.java`
- [ ] `FieldNode.java`
- [ ] `FileNode.java`
- [ ] `PackageNode.java`
- [ ] `RouteNode.java`
- [ ] `ProjectNode.java`

#### `graph/repository/`
- [ ] `ClassNodeRepository.java`
- [ ] `MethodNodeRepository.java`
- [ ] `FileNodeRepository.java`
- [ ] `ProjectNodeRepository.java`
- [ ] `GraphRepository.java` — Custom Cypher

#### `graph/websocket/`
- [ ] `GraphUpdateController.java` — `@MessageMapping`
- [ ] `WebSocketEventListener.java` — Connection events

#### `graph/dto/request/`
- [ ] `CreateProjectRequest.java`
- [ ] `AnalyzeRequest.java`
- [ ] `GraphFilterRequest.java`

#### `graph/dto/response/`
- [ ] `GraphDataResponse.java` — `{nodes, edges}`
- [ ] `NodeDto.java`
- [ ] `EdgeDto.java`
- [ ] `NodeDetailResponse.java` — INCOMING + OUTGOING
- [ ] `ProjectResponse.java`
- [ ] `ImpactAnalysisResponse.java`

---

### `diagram/` — UML Generators

#### `diagram/controller/`
- [ ] `DiagramController.java` — `GET /api/diagrams/*`

#### `diagram/service/`
- [ ] `UseCaseDiagramService.java` — interface
- [ ] `ClassDiagramService.java` — interface
- [ ] `SequenceDiagramService.java` — interface
- [ ] `MermaidGeneratorService.java` — interface

#### `diagram/service/impl/`
- [ ] `UseCaseDiagramServiceImpl.java`
- [ ] `ClassDiagramServiceImpl.java`
- [ ] `SequenceDiagramServiceImpl.java`
- [ ] `MermaidGeneratorServiceImpl.java`

#### `diagram/repository/`
- [ ] `DiagramQueryRepository.java`

#### `diagram/node/`
- [ ] `DiagramData.java` — Internal model

#### `diagram/dto/request/`
- [ ] `SequenceDiagramRequest.java`

#### `diagram/dto/response/`
- [ ] `DiagramResponse.java` — `{mermaidSyntax, type}`
- [ ] `UseCaseResponse.java`

---

### `mcp/` — MCP Server

#### `mcp/controller/`
- [ ] `McpEndpointController.java` — `/mcp` endpoint

#### `mcp/tool/` — `@Tool` classes
- [ ] `ArchitectureTool.java` — `get_project_architecture`
- [ ] `ClassContextTool.java` — `get_class_context`
- [ ] `LayerPatternTool.java` — `get_layer_pattern`
- [ ] `ImpactAnalysisTool.java` — `get_impact_analysis`
- [ ] `UseCaseContextTool.java` — `get_usecase_context`
- [ ] `CodingRulesTool.java` — `get_coding_rules`

#### `mcp/service/`
- [ ] `McpToolService.java` — interface
- [ ] `ArchitectureAnalyzer.java` — interface

#### `mcp/service/impl/`
- [ ] `McpToolServiceImpl.java`
- [ ] `ArchitectureAnalyzerImpl.java`

#### `mcp/dto/request/`
- [ ] `ClassContextRequest.java`
- [ ] `LayerPatternRequest.java`

#### `mcp/dto/response/`
- [ ] `ArchitectureContextResponse.java`
- [ ] `ClassContextResponse.java`
- [ ] `LayerPatternResponse.java`
- [ ] `CodingRulesResponse.java`

---

### `steering/` — Auto-gen rules

#### `steering/service/`
- [ ] `SteeringFileService.java` — interface

#### `steering/service/impl/`
- [ ] `SteeringFileServiceImpl.java`

#### `steering/writer/`
- [ ] `SteeringWriter.java` — interface
- [ ] `KiroSteeringWriter.java` — `.kiro/steering/*.md`
- [ ] `CursorRulesWriter.java` — `.cursor/rules/*.mdc`
- [ ] `ClaudeRulesWriter.java` — `CLAUDE.md`

---

### `watcher/` — File Watcher

#### `watcher/config/`
- [ ] `WatcherProperties.java` — `@ConfigurationProperties`

#### `watcher/service/`
- [ ] `FileWatcherService.java` — interface
- [ ] `DebouncedEventHandler.java` — Helper

#### `watcher/service/impl/`
- [ ] `FileWatcherServiceImpl.java` — Java WatchService

---

## 📦 BACKEND — Resources

### `D:\Users\User\IdeaProjects\VibeGraph\src\main\resources\`
- [ ] `application.yaml` — ⚠️ Cần config Neo4j, MCP, WebSocket, watcher
- [ ] `application-dev.yaml`
- [ ] `application-docker.yaml`

---

## 🎨 FRONTEND — `D:\Users\User\IdeaProjects\VibeGraph\vibegraph-web\src\`

### `views/`
- [ ] `HomeView.vue` — Project list
- [ ] `GraphView.vue` — Main graph view (3 panels)
- [ ] `SettingsView.vue` — Settings page

### `components/layout/`
- [ ] `HeaderBar.vue`
- [ ] `MainLayout.vue`
- [ ] `SidePanel.vue`
- [ ] `StatusBar.vue`

### `components/panels/`
- [ ] `FilterPanel.vue` — NODE/EDGE TYPES + count
- [ ] `ExplorerPanel.vue` — File tree
- [ ] `FlowsPanel.vue` — Execution flows
- [ ] `NodeDetailPanel.vue` — INCOMING + OUTGOING
- [ ] `CodeInspector.vue` — Source code viewer
- [ ] `LegendPanel.vue` — Color legend
- [ ] `FocusDepthControl.vue` — All/1/2/3/5 hops

### `components/graph/`
- [ ] `GraphCanvas.vue` — Sigma.js container
- [ ] `GraphControls.vue` — Zoom/reset
- [ ] `SearchBar.vue` — Search nodes

### `components/diagram/`
- [ ] `DiagramPanel.vue` — Tabs container
- [ ] `UseCaseDiagram.vue`
- [ ] `ClassDiagram.vue`
- [ ] `SequenceDiagram.vue`

### `components/ui/`
- [ ] `Button.vue`
- [ ] `Input.vue`
- [ ] `Tabs.vue`
- [ ] `Spinner.vue`

### `composables/`
- [x] ⚠️ `useSigma.ts`
- [x] ⚠️ `useWebSocket.ts`
- [x] ⚠️ `useGraphData.ts`
- [x] ⚠️ `useDiagrams.ts`
- [x] ⚠️ `useFilters.ts`

### `lib/`
- [x] ✅ `constants.ts`
- [ ] `api.ts` — HTTP client
- [ ] `graphAdapter.ts` — Convert API → Sigma.js
- [ ] `focusMode.ts` — Sigma reducers logic
- [ ] `colors.ts` — Color helpers

### `stores/`
- [x] ⚠️ `graph.ts`
- [x] ⚠️ `filter.ts`
- [x] ⚠️ `project.ts`

### `types/`
- [x] ⚠️ `graph.ts`

---

## 🐳 DEVOPS — Root

- [ ] `docker-compose.yml` — Neo4j + Backend + Frontend
- [ ] `Dockerfile` — Backend (Spring Boot)
- [ ] `vibegraph-web/Dockerfile` — Frontend (Vue + Nginx)
- [ ] `vibegraph-web/nginx.conf` — Nginx config for SPA
- [ ] `.env.example` — Environment variables template
- [ ] `.dockerignore`
- [ ] `README.md` — Setup instructions

---

## 📦 FRONTEND — Dependencies (cài thêm)

```bash
cd vibegraph-web
npm install sigma graphology graphology-layout-forceatlas2 graphology-layout-noverlap @sigma/edge-curve axios
```

- [ ] `sigma` — WebGL graph renderer
- [ ] `graphology` — Graph data structure
- [ ] `graphology-layout-forceatlas2` — Layout algorithm
- [ ] `graphology-layout-noverlap` — Anti-overlap
- [ ] `@sigma/edge-curve` — Curved edges
- [ ] `axios` — HTTP client

---

## 📊 TỔNG KẾT

| Hạng mục | Tổng | Status |
|----------|------|--------|
| Backend Java classes | 75 files | 1/75 ✅ |
| Backend resources | 3 files | 1/3 ⚠️ |
| Frontend Vue components | 23 files | 0/23 |
| Frontend lib | 5 files | 1/5 ✅ |
| Frontend composables | 5 files | 5/5 ⚠️ |
| Frontend stores | 3 files | 3/3 ⚠️ |
| Frontend types | 1 file | 1/1 ⚠️ |
| Frontend deps | 6 packages | 0/6 |
| DevOps | 7 files | 0/7 |
| **TỔNG** | **~128 items** | **15/128** |
