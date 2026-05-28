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

## CLI — `vibegraph-cli/` **(MỚI — Real-time Local Watch)**

### Module setup
- [ ] `pom.xml` (Java module, depends on `vibegraph-core`, picocli, java-websocket, directory-watcher)
- [ ] `VibeGraphCli.java` (main entry, picocli @Command)

### `cli/watcher/`
- [ ] `LocalWatcher.java` (io.methvin DirectoryWatcher, debounce 500ms, ignore build/target/.git/node_modules)

### `cli/parser/`
- [ ] `DiffExtractor.java` (parse changed file → extract NodeData/EdgeData diff, reuse vibegraph-core)
- [ ] `InitialScanner.java` (full scan project → push toàn bộ metadata lần đầu)

### `cli/client/`
- [ ] `WsClient.java` (WebSocket client, auto-reconnect, queue offline)
- [ ] `SessionIdGenerator.java` (hash folder path + timestamp → sessionId, dùng làm projectId demo mode)
- [ ] `DiffPayload.java` (DTO: type=INCREMENTAL, added/removed/modified nodes+edges)

### `cli/command/`
- [ ] `WatchCommand.java` (`vibegraph watch [path]` — initial scan + watch loop, không cần login)
- [ ] `SyncCommand.java` (`vibegraph sync` — full re-scan, useful sau khi disconnect lâu)

### Test
- [ ] `LocalWatcherTest.java` (E2E: tạo/xóa file → verify diff payload)
- [ ] `DiffExtractorTest.java`

---

## CLI npm wrapper — `vibegraph-cli-npm/` **(MỚI)**

- [ ] `package.json` (npm package: `vibegraph`, version 0.1.0)
- [ ] `bin/vibegraph.js` (Node.js entry, spawn `java -jar vibegraph-cli.jar`)
- [ ] `postinstall.js` (check Java 21+, fail nicely với hướng dẫn cài)
- [ ] `README.md` (npm install -g vibegraph, quickstart)
- [ ] `.npmignore`

---

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
- [ ] `TarballImportService.java` **(MỚI — thay JGit)** + `impl/TarballImportServiceImpl.java`

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
| **CLI Java classes** | **~15 files** | **(MỚI — real-time local watch)** |
| **CLI npm wrapper** | **~5 files** | **(MỚI)** |
| Frontend Vue | ~25 components | +1 (GithubImportForm) |
| DevOps | ~12 files | +5 (CI/CD, prod config) |
| **Tổng** | **~130 items** | +2 so với spec gốc (thêm CLI module) |

## Có cần đổi cấu trúc folder không?

**Cần thêm:**
1. `vibegraph-server/src/main/java/com/vibegraph/server/graph/repository/impl/neo4j/` — subpackage mới cho Neo4j-specific code
2. `vibegraph-server/.../graph/service/TarballImportService.java` + impl
3. `vibegraph-server/.../graph/controller/ImportController.java`
4. `vibegraph-web/src/components/import/` — folder mới
5. `vibegraph-cli/` — **Maven module mới cho CLI (MỚI)**
6. `vibegraph-cli-npm/` — **npm wrapper package (MỚI)**

**Cần bỏ:**
1. `vibegraph-server/.../steering/` — bỏ toàn bộ folder
2. `vibegraph-server/.../mcp/tool/UseCaseContextTool.java` + `CodingRulesTool.java`

**Cần thêm dependency `vibegraph-server/pom.xml`:**
```xml
<dependency>
  <groupId>org.apache.commons</groupId>
  <artifactId>commons-compress</artifactId>
  <version>1.26.0</version>
</dependency>
<dependency>
  <groupId>com.tngtech.archunit</groupId>
  <artifactId>archunit-junit5</artifactId>
  <version>1.3.0</version>
  <scope>test</scope>
</dependency>
```

**Cần thêm dependency `vibegraph-cli/pom.xml`:**
```xml
<dependency>
  <groupId>com.vibegraph</groupId>
  <artifactId>vibegraph-core</artifactId>
  <version>${project.version}</version>
</dependency>
<dependency>
  <groupId>info.picocli</groupId>
  <artifactId>picocli</artifactId>
  <version>4.7.6</version>
</dependency>
<dependency>
  <groupId>io.methvin</groupId>
  <artifactId>directory-watcher</artifactId>
  <version>0.18.0</version>
</dependency>
<dependency>
  <groupId>org.java-websocket</groupId>
  <artifactId>Java-WebSocket</artifactId>
  <version>1.5.6</version>
</dependency>
```
