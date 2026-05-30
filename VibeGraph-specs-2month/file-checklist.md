# VibeGraph - Checklist file (Phạm vi 2 tháng)

Chú thích: `[x] xong`, `[s] scaffold/stub (file đã tạo nhưng chưa có logic — chỉ khung + TODO)`, `[~] đã hiện thực nhưng test còn @Disabled (chưa kiểm chứng)`, `[ ] cần làm`, `[defer] post-MVP`.

> **Đã đối soát ngày 2026-05-30 với cây repo thực tế.** Backend và frontend hoàn
> thiện hơn nhiều so với những bản nháp trước của checklist này ngụ ý. Tầng
> persistence giờ dùng **raw Neo4j Java Driver** (không dùng Spring Data Neo4j OGM):
> các entity `@Node` cũ dưới `graph/node/` và các interface `*NodeRepository` theo
> từng type đã bị xóa. Impl repository duy nhất là `graph/repository/impl/neo4j/`.

Checklist này bám theo bố cục repo hiện tại. Không tạo `vibegraph-core`, `vibegraph-server`, `vibegraph-cli`, hay `vibegraph-cli-npm` cho MVP 2 tháng.

## Backend - Module Spring Boot gốc

### `src/main/java/com/vibegraph/parser/node/`

- [x] `NodeData.java`
- [x] `EdgeData.java`
- [x] `ParseResult.java`

### `src/main/java/com/vibegraph/parser/visitor/`

- [x] `ClassVisitor.java`
- [x] `MethodVisitor.java`
- [x] `FieldVisitor.java`
- [~] `ImportVisitor.java` (đã có; `ImportVisitorTest` còn `@Disabled`)
- [x] `SpringAnnotationVisitor.java`

### `src/main/java/com/vibegraph/parser/service/`

- [x] `ParserService.java`
- [x] `impl/ParserServiceImpl.java`
- [x] `SymbolResolverService.java`
- [x] `impl/SymbolResolverServiceImpl.java`
- [x] `CallGraphBuilderService.java`
- [x] `impl/CallGraphBuilderServiceImpl.java`

### `src/main/java/com/vibegraph/common/config/`

- [x] `Neo4jMigrationRunner.java` (áp dụng `V1__init_schema.cypher` lúc khởi động; thay thế `Neo4jConfig.java` đã bị xóa)
- [x] `WebSocketConfig.java` với `/ws/graph-updates`
- [x] `CorsConfig.java`
- [x] `McpServerConfig.java`
- [x] `AsyncConfig.java`

### `src/main/java/com/vibegraph/common/exception/`

- [x] `GlobalExceptionHandler.java`
- [x] `ProjectNotFoundException.java`
- [x] `ParseException.java`
- [x] `NodeNotFoundException.java`
- [x] `GithubImportException.java`

### `src/main/java/com/vibegraph/common/dto/`

- [x] `request/PaginationRequest.java`
- [x] `response/ApiResponse.java`
- [x] `response/ErrorResponse.java`

### `src/main/java/com/vibegraph/common/util/`

- [x] `FileUtils.java`
- [x] `HashUtils.java`
- [x] `JsonUtils.java`

### `src/main/java/com/vibegraph/graph/repository/`

- [x] `GraphRepository.java` (interface)
- [x] `impl/neo4j/Neo4jGraphRepository.java` (raw Driver + Cypher, impl duy nhất)
- [x] `impl/neo4j/GraphSchema.java` (mapping label/edge-type + validate property)

> **Đã gỡ trong đợt refactor sang raw Driver:** `NodeRepository.java`,
> `ClassNodeRepository.java`, `MethodNodeRepository.java`, `FileNodeRepository.java`,
> và `ProjectNodeRepository.java`. Không còn dùng Spring Data Neo4j nên không có
> interface repository theo từng type.

### `src/main/java/com/vibegraph/graph/node/`

- [removed] Toàn bộ entity Neo4j `@Node` (`ProjectNode`, `PackageNode`, `FileNode`,
  `ClassNode`, `InterfaceNode`, `EnumNode`, `MethodNode`, `FieldNode`, `RouteNode`,
  `AnnotationNode`) đã bị xóa. Thư mục này rỗng. Dữ liệu graph được mang bởi
  `NodeData`/`EdgeData` của parser và ghi xuống dưới dạng raw Cypher; "label" của node
  nằm trong `GraphSchema`, không phải dưới dạng class entity Java.

### `src/main/java/com/vibegraph/graph/controller/`

- [x] `ProjectController.java`
- [x] `GraphController.java`
- [x] `ImportController.java`
- [s] `ImpactController.java` (chỉ khung `@RestController`, chưa có endpoint — `// TODO`)

### `src/main/java/com/vibegraph/graph/service/`

- [x] `ProjectService.java` + [x] `impl/ProjectServiceImpl.java`
- [x] `AnalyzeService.java` + [x] `impl/AnalyzeServiceImpl.java`
- [x] `GraphService.java` + [x] `impl/GraphServiceImpl.java` (`GraphServiceTest` còn `@Disabled`)
- [x] `ImpactService.java` + [s] `impl/ImpactServiceImpl.java` (`// TODO: Implement`; `ImpactServiceTest` còn `@Disabled`)
- [x] `TarballImportService.java` + [s] `impl/TarballImportServiceImpl.java` (ném "not implemented yet"; `TarballImportServiceTest` còn `@Disabled`)

### `src/main/java/com/vibegraph/graph/websocket/`

- [x] `GraphUpdateController.java`
- [x] `WebSocketEventListener.java`

### `src/main/java/com/vibegraph/graph/dto/`

- [x] `request/CreateProjectRequest.java`
- [x] `request/AnalyzeRequest.java`
- [x] `request/GithubImportRequest.java`
- [x] `request/GraphFilterRequest.java`
- [x] `response/GraphDataResponse.java`
- [x] `response/NodeDto.java`
- [x] `response/EdgeDto.java`
- [x] `response/NodeDetailResponse.java`
- [x] `response/ProjectResponse.java`
- [x] `response/ImpactAnalysisResponse.java`
- [x] `response/NodeTypeEnum.java`
- [x] `response/EdgeTypeEnum.java`

### `src/main/java/com/vibegraph/diagram/`

- [s] `controller/DiagramController.java` (chỉ khung, chưa có endpoint — `// TODO`)
- [x] `service/UseCaseDiagramService.java` + [s] `impl/UseCaseDiagramServiceImpl.java` (`// TODO: Implement`)
- [x] `service/ClassDiagramService.java` + [s] `impl/ClassDiagramServiceImpl.java` (`// TODO: Implement`)
- [x] `service/MermaidGeneratorService.java` + [s] `impl/MermaidGeneratorServiceImpl.java` (`// TODO: Implement`)
- [x] `repository/DiagramQueryRepository.java`
- [x] `dto/response/DiagramResponse.java`
- [x] `dto/response/UseCaseResponse.java`

> `DiagramServiceTest` đã có nhưng còn `@Disabled`.

### `src/main/java/com/vibegraph/mcp/`

- [s] `tool/ArchitectureTool.java` (`// TODO: Add @Tool method`)
- [s] `tool/ClassContextTool.java` (`// TODO: Add @Tool method`)
- [s] `tool/LayerPatternTool.java` (`// TODO: Add @Tool method`)
- [s] `tool/ImpactAnalysisTool.java` (`// TODO: Add @Tool method`)
- [x] `controller/McpEndpointController.java`
- [x] `service/McpToolService.java` + [s] `impl/McpToolServiceImpl.java` (`// TODO: Implement`)
- [x] `service/ArchitectureAnalyzer.java` + [s] `impl/ArchitectureAnalyzerImpl.java` (`// TODO: Implement`)
- [x] response DTOs: `ArchitectureContextResponse`, `ClassContextResponse`, `LayerPatternResponse`
- [x] request DTOs: `ClassContextRequest`, `LayerPatternRequest`

> `McpToolsTest` đã có nhưng còn `@Disabled`.

### `src/main/java/com/vibegraph/watcher/`

- [x] `config/WatcherProperties.java`
- [x] `service/FileWatcherService.java` + [x] `impl/FileWatcherServiceImpl.java`
- [x] `service/DebouncedEventHandler.java`

> `FileWatcherServiceTest` đã có nhưng còn `@Disabled`.

### Resources

- [x] `src/main/resources/application.yaml`
- [x] `src/main/resources/application-dev.yaml`
- [x] `src/main/resources/application-prod.yaml`
- [x] `src/main/resources/application-docker.yaml`
- [x] `src/main/resources/db/migration/V1__init_schema.cypher`
- [x] schema được áp dụng lúc khởi động bởi `common/config/Neo4jMigrationRunner.java`

### Test backend

Đã bật và pass:

- [x] `architecture/StorageAbstractionTest.java`
- [x] `common/exception/ExceptionsTest.java`
- [x] `common/util/FileUtilsTest.java`, `HashUtilsTest.java`, `JsonUtilsTest.java`
- [x] `parser/visitor/ClassVisitorTest.java`, `MethodVisitorTest.java`, `FieldVisitorTest.java`, `SpringAnnotationVisitorTest.java`
- [x] `parser/service/ParserServiceTest.java`
- [x] `graph/repository/impl/neo4j/GraphSchemaTest.java`
- [x] `graph/controller/ProjectControllerTest.java`
- [x] `graph/service/impl/ProjectServiceImplTest.java`

Đã có nhưng còn `@Disabled` (đã hiện thực, chờ kiểm chứng):

- [~] `parser/visitor/ImportVisitorTest.java`
- [~] `graph/service/GraphServiceTest.java`
- [~] `graph/service/ImpactServiceTest.java`
- [~] `graph/service/TarballImportServiceTest.java`
- [~] `graph/controller/ImportControllerTest.java`
- [~] `diagram/service/DiagramServiceTest.java`
- [~] `mcp/tool/McpToolsTest.java`
- [~] `watcher/service/FileWatcherServiceTest.java`
- [~] `VibeGraphApplicationTests.java` (nạp context)

Test fixture:

- [x] `src/test/resources/sample-project/` (4 file Java mẫu)

## Frontend - `vibegraph-web/src/`

### Views

- [x] `HomeView.vue`
- [x] `GraphView.vue`
- [x] `SettingsView.vue`

### Components

- [x] `components/layout/HeaderBar.vue`
- [x] `components/layout/MainLayout.vue`
- [x] `components/layout/SidePanel.vue`
- [x] `components/layout/StatusBar.vue`
- [x] `components/panels/FilterPanel.vue`
- [x] `components/panels/ExplorerPanel.vue`
- [x] `components/panels/FlowsPanel.vue`
- [x] `components/panels/NodeDetailPanel.vue`
- [x] `components/panels/LegendPanel.vue`
- [x] `components/panels/FocusDepthControl.vue`
- [x] `components/panels/CodeInspector.vue`
- [x] `components/graph/GraphCanvas.vue`
- [x] `components/graph/GraphControls.vue`
- [x] `components/graph/SearchBar.vue`
- [x] `components/diagram/DiagramPanel.vue`
- [x] `components/diagram/UseCaseDiagram.vue`
- [x] `components/diagram/ClassDiagram.vue`
- [x] `components/diagram/SequenceDiagram.vue` (có sẵn sớm; hỗ trợ sequence diagram bản production vẫn hoãn theo phạm vi)
- [ ] `components/import/GithubImportForm.vue`
- [x] `components/ui/Button.vue`
- [x] `components/ui/Input.vue`
- [x] `components/ui/Tabs.vue`
- [x] `components/ui/Spinner.vue`

### Composables, Stores, Types

- [x] `composables/useSigma.ts`
- [x] `composables/useWebSocket.ts` dùng `/ws/graph-updates`
- [x] `composables/useGraphData.ts`
- [x] `composables/useDiagrams.ts`
- [x] `composables/useFilters.ts`
- [ ] `composables/useGithubImport.ts`
- [x] `lib/constants.ts`
- [x] `lib/api.ts`
- [x] `lib/graphAdapter.ts` (được bao phủ bởi `lib/__tests__/graphAdapter.spec.ts`)
- [x] `lib/focusMode.ts`
- [x] `lib/colors.ts`
- [x] `stores/graph.ts`
- [x] `stores/filter.ts`
- [x] `stores/project.ts`
- [x] `types/graph.ts`

> `stores/counter.ts` là scaffolding Vue còn sót lại và có thể gỡ bỏ.

## DevOps - Gốc repo

- [x] `docker-compose.yml` cho dev cục bộ
- [x] `Dockerfile` cho module backend gốc
- [x] `vibegraph-web/Dockerfile`
- [x] `vibegraph-web/nginx.conf`
- [x] `.env.example`
- [x] `.dockerignore`
- [~] `README.md`
- [ ] `.github/workflows/ci.yml`
- [~] file compose/nginx/certbot cho production (template đã có tài liệu trong `deployment-plan.md`; chưa commit thành file)
- [ ] `docs/setup.md`
- [ ] `docs/mcp-integration.md`

## Hoãn lại tường minh

- [defer] `vibegraph-core/`
- [defer] `vibegraph-server/`
- [defer] `vibegraph-cli/`
- [defer] `vibegraph-cli-npm/`
- [defer] package npm wrapper
- [defer] module `steering/`
- [defer] `mcp/tool/UseCaseContextTool.java`
- [defer] `mcp/tool/CodingRulesTool.java`
- [defer] service sequence diagram và UI sequence diagram bản production
- [defer] auth, billing, tài khoản người dùng

## Hạng mục công việc tiếp theo

Lát cắt dọc Sprint 1 đã hiện thực và xanh. Bề mặt Sprint 2-3 cho **diagram và MCP
hiện mới ở mức scaffold/stub** (xem các dòng `[s]` ở trên) — nên phần việc còn lại
gồm cả hiện thực, không chỉ kiểm chứng:

1. Hiện thực các stub `[s]`: endpoint cho `DiagramController`/`ImpactController`, các
   `*DiagramServiceImpl` + `MermaidGeneratorServiceImpl`, 4 MCP `@Tool` +
   `McpToolServiceImpl`/`ArchitectureAnalyzerImpl`, và `getNeighborhood`/`getImpact`
   trong `Neo4jGraphRepository`.
2. Bật lại các test backend đang `@Disabled` (service, controller, diagram, mcp,
   watcher, import-visitor, app context) và làm cho chúng xanh — biến `[~]` thành `[x]`.
3. Dựng UI import GitHub: `composables/useGithubImport.ts` và
   `components/import/GithubImportForm.vue` (đường import phía backend đã có sẵn).
4. Thêm `.github/workflows/ci.yml` và các trang `docs/` setup + tích hợp MCP.
5. Gỡ scaffolding còn sót (`stores/counter.ts`).
