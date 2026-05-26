# VibeGraph — File Checklist (2-Month Scope)

**Quy ước:** ✅ done | ⚠️ skeleton | ❌ todo

---

## BACKEND — `vibegraph-core/`

### `core/parser/visitor/`
- [ ] `ClassVisitor.java`
- [ ] `MethodVisitor.java`
- [ ] `FieldVisitor.java`
- [ ] `ImportVisitor.java`
- [ ] `SpringAnnotationVisitor.java`

### `core/parser/service/`
- [ ] `ParserService.java` (interface)
- [ ] `impl/ParserServiceImpl.java`
- [ ] `SymbolResolverService.java`
- [ ] `impl/SymbolResolverServiceImpl.java`
- [ ] `CallGraphBuilderService.java`
- [ ] `impl/CallGraphBuilderServiceImpl.java`

### `core/graph/`
- [ ] `NodeData.java` (POJO, không phải @Node)
- [ ] `EdgeData.java`
- [ ] `ParseResult.java`

### `core/spring/`
- [ ] `SpringLayerDetector.java`
- [ ] `RouteExtractor.java`

---

## BACKEND — `vibegraph-server/`

### `common/config/`
- [ ] `Neo4jConfig.java`
- [ ] `WebSocketConfig.java` (STOMP)
- [ ] `CorsConfig.java`
- [ ] `McpServerConfig.java`
- [ ] `AsyncConfig.java` (virtual threads)

### `common/exception/`
- [ ] `GlobalExceptionHandler.java`
- [ ] `ProjectNotFoundException.java`
- [ ] `ParseException.java`
- [ ] `NodeNotFoundException.java`
- [ ] `GithubImportException.java` (mới)

### `common/dto/`
- [ ] `request/PaginationRequest.java`
- [ ] `response/ApiResponse.java`
- [ ] `response/ErrorResponse.java`

### `common/util/`
- [ ] `FileUtils.java`
- [ ] `HashUtils.java`
- [ ] `JsonUtils.java`

### `graph/repository/` — **QUAN TRỌNG: tách interface/impl**
- [ ] `GraphRepository.java` (interface)
- [ ] `NodeRepository.java` (interface generic)
- [ ] `impl/neo4j/Neo4jGraphRepository.java`
- [ ] `impl/neo4j/Spring DataNeo4jRepositories/` (auto-generated repos cho mỗi @Node)
  - [ ] `ClassNodeNeo4jRepository.java`
  - [ ] `MethodNodeNeo4jRepository.java`
  - [ ] `FileNodeNeo4jRepository.java`
  - [ ] `ProjectNodeRepository.java`

### `graph/node/` — Neo4j entity (CHỈ dùng trong `impl/neo4j/`)
- [ ] `ProjectNode.java`
- [ ] `PackageNode.java`
- [ ] `FileNode.java`
- [ ] `ClassNode.java`
- [ ] `InterfaceNode.java`
- [ ] `EnumNode.java`
- [ ] `MethodNode.java`
- [ ] `FieldNode.java`
- [ ] `RouteNode.java`
- [ ] `AnnotationNode.java`

### `graph/controller/`
- [ ] `ProjectController.java` (POST /api/projects)
- [ ] `GraphController.java` (GET /api/projects/{id}/graph + neighbors)
- [ ] `ImportController.java` **(MỚI — POST /api/projects/import-github)**
- [ ] `ImpactController.java` (GET /api/projects/{id}/impact/{nodeId})

### `graph/service/`
- [ ] `ProjectService.java` + `impl/ProjectServiceImpl.java`
- [ ] `AnalyzeService.java` + `impl/AnalyzeServiceImpl.java`
- [ ] `GraphService.java` + `impl/GraphServiceImpl.java`
- [ ] `ImpactService.java` + `impl/ImpactServiceImpl.java`
- [ ] `GithubImportService.java` **(MỚI)** + `impl/GithubImportServiceImpl.java`

### `graph/websocket/`
- [ ] `GraphUpdateController.java`
- [ ] `WebSocketEventListener.java`

### `graph/dto/`
- [ ] `request/CreateProjectRequest.java`
- [ ] `request/GithubImportRequest.java` **(MỚI)**
- [ ] `request/GraphFilterRequest.java`
- [ ] `response/GraphDataResponse.java`
- [ ] `response/NodeDto.java`
- [ ] `response/EdgeDto.java`
- [ ] `response/NodeDetailResponse.java`
- [ ] `response/ProjectResponse.java`
- [ ] `response/ImpactAnalysisResponse.java`

### `diagram/`
- [ ] `controller/DiagramController.java`
- [ ] `service/UseCaseDiagramService.java` + impl
- [ ] `service/ClassDiagramService.java` + impl
- [ ] `service/MermaidGeneratorService.java` + impl
- [ ] `dto/response/DiagramResponse.java`

### `mcp/` — 4 tools (giảm từ 6)
- [ ] `tool/ArchitectureTool.java`
- [ ] `tool/ClassContextTool.java`
- [ ] `tool/LayerPatternTool.java`
- [ ] `tool/ImpactAnalysisTool.java`
- [ ] `service/McpToolService.java` + impl
- [ ] `service/ArchitectureAnalyzer.java` + impl
- [ ] `dto/response/ArchitectureContextResponse.java`
- [ ] `dto/response/ClassContextResponse.java`
- [ ] `dto/response/LayerPatternResponse.java`

### `watcher/`
- [ ] `config/WatcherProperties.java`
- [ ] `service/FileWatcherService.java` + `impl/FileWatcherServiceImpl.java`
- [ ] `service/DebouncedEventHandler.java`

### Resources
- [ ] `application.yaml`
- [ ] `application-dev.yaml`
- [ ] `application-prod.yaml`
- [ ] `db/migration/V1__init_schema.cypher`

### Test
- [ ] `architecture/StorageAbstractionTest.java` (ArchUnit forbid Neo4j leak)

---

## ❌ BỎ (defer post-2-month, KHÔNG tạo)

- `steering/` module — toàn bộ folder (FR-12 defer)
- `mcp/tool/UseCaseContextTool.java`
- `mcp/tool/CodingRulesTool.java`
- `diagram/service/SequenceDiagramService.java` (FR-06 defer)
- Auth/Stripe/User module
- npm wrapper folder

---

## FRONTEND — `vibegraph-web/src/`

### `views/`
- [ ] `HomeView.vue` (project list + GitHub import form)
- [ ] `GraphView.vue` (main 3-panel layout)
- [ ] `SettingsView.vue`

### `components/layout/`
- [ ] `HeaderBar.vue`
- [ ] `MainLayout.vue`
- [ ] `SidePanel.vue` (tabs Filters/Explorer/Flows)
- [ ] `StatusBar.vue`

### `components/panels/`
- [ ] `FilterPanel.vue`
- [ ] `ExplorerPanel.vue`
- [ ] `FlowsPanel.vue`
- [ ] `NodeDetailPanel.vue`
- [ ] `CodeInspector.vue` (Monaco read-only)
- [ ] `LegendPanel.vue`
- [ ] `FocusDepthControl.vue`

### `components/graph/`
- [ ] `GraphCanvas.vue`
- [ ] `GraphControls.vue`
- [ ] `SearchBar.vue`

### `components/diagram/`
- [ ] `DiagramPanel.vue`
- [ ] `UseCaseDiagram.vue`
- [ ] `ClassDiagram.vue`
- ~~`SequenceDiagram.vue`~~ defer

### `components/import/` **(MỚI)**
- [ ] `GithubImportForm.vue` (input URL + analyze button)

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
- [ ] `useGithubImport.ts` **(MỚI)**

### `lib/`
- [x] ✅ `constants.ts`
- [ ] `api.ts`
- [ ] `graphAdapter.ts`
- [ ] `focusMode.ts`
- [ ] `colors.ts`

### `stores/`
- [x] ⚠️ `graph.ts`
- [x] ⚠️ `filter.ts`
- [x] ⚠️ `project.ts`

### `types/`
- [x] ⚠️ `graph.ts`

---

## DEVOPS — Root

- [ ] `docker-compose.yml` (dev)
- [ ] `docker-compose.prod.yml` (prod with nginx + Let's Encrypt)
- [ ] `Dockerfile` (backend)
- [ ] `vibegraph-web/Dockerfile`
- [ ] `vibegraph-web/nginx.conf`
- [ ] `.env.example`
- [ ] `.dockerignore`
- [ ] `README.md`
- [ ] `.github/workflows/ci.yml`
- [ ] `.github/workflows/deploy.yml`
- [ ] `docs/setup.md`
- [ ] `docs/mcp-integration.md`

---

## Tổng kết

| Phần | Tổng | So với spec gốc |
|---|---|---|
| Backend Java classes | ~65 files | -10 (bỏ steering, 2 MCP tools, sequence) |
| Frontend Vue | ~25 components | +1 (GithubImportForm) |
| DevOps | ~12 files | +5 (CI/CD, prod config) |
| **Tổng** | **~110 items** | Giảm ~14% so với 128 |

## Có cần đổi cấu trúc folder không?

**Cần thêm:**
1. `vibegraph-server/src/main/java/com/vibegraph/server/graph/repository/impl/neo4j/` — subpackage mới cho Neo4j-specific code
2. `vibegraph-server/.../graph/service/GithubImportService.java` + impl
3. `vibegraph-server/.../graph/controller/ImportController.java`
4. `vibegraph-web/src/components/import/` — folder mới

**Cần bỏ:**
1. `vibegraph-server/.../steering/` — bỏ toàn bộ folder
2. `vibegraph-server/.../mcp/tool/UseCaseContextTool.java` + `CodingRulesTool.java`

**Cần thêm dependency `vibegraph-server/pom.xml`:**
```xml
<dependency>
  <groupId>org.eclipse.jgit</groupId>
  <artifactId>org.eclipse.jgit</artifactId>
  <version>6.10.0.202406032230-r</version>
</dependency>
<dependency>
  <groupId>com.tngtech.archunit</groupId>
  <artifactId>archunit-junit5</artifactId>
  <version>1.3.0</version>
  <scope>test</scope>
</dependency>
```
