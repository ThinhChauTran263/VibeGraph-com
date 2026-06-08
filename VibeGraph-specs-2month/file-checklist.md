# VibeGraph - Checklist file (Phạm vi 2 tháng)

Chú thích: `[x] xong`, `[s] scaffold/stub (file đã tạo nhưng chưa có logic — chỉ khung + TODO)`, `[~] đã hiện thực nhưng test còn @Disabled (chưa kiểm chứng)`, `[ ] cần làm`, `[defer] post-MVP`.

> **Đã đối soát ngày 2026-05-30 với cây repo thực tế.** Backend và frontend hoàn
> thiện hơn nhiều so với những bản nháp trước của checklist này ngụ ý. Tầng
> persistence giờ dùng **raw Neo4j Java Driver** (không dùng Spring Data Neo4j OGM):
> các entity `@Node` cũ dưới legacy graph-node package name và các interface `*NodeRepository` theo
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
- [x] `ImportVisitor.java` (`ImportVisitorTest` đã bật và pass)
- [x] `SpringAnnotationVisitor.java`

### `src/main/java/com/vibegraph/parser/service/`

- [x] `ParserService.java`
- [x] `impl/ParserServiceImpl.java` (`parseFile`/`parseProject` đã chạy; `parseFileWithCache` còn deferred Sprint 2 và ném `UnsupportedOperationException`)
- [s] `SymbolResolverService.java` + [s] `impl/SymbolResolverServiceImpl.java` (interface/impl còn khung TODO; symbol solving thực tế đang nằm trong `ParserServiceImpl` + visitor)
- [s] `CallGraphBuilderService.java` + [s] `impl/CallGraphBuilderServiceImpl.java` (interface/impl còn khung TODO; CALLS hiện do `MethodVisitor` phát ra cho resolved in-project calls)

### `src/main/java/com/vibegraph/common/config/`

- [x] `Neo4jMigrationRunner.java` (áp dụng `V1__init_schema.cypher` lúc khởi động; thay thế config Neo4j cũ đã bị xóa)
- [x] `WebSocketConfig.java` với `/ws/graph-updates` (endpoint/broker đã có; allowed origins/heartbeat còn TODO hardening)
- [x] `CorsConfig.java`
- [s] `McpServerConfig.java` (class config đã có; chưa đăng ký MCP tool/transport custom nào trong file này)
- [x] `AsyncConfig.java` (`@EnableAsync` + bean `analysisExecutor` bounded thread pool đã có)

### `src/main/java/com/vibegraph/common/exception/`

- [x] `GlobalExceptionHandler.java`
- [x] `ProjectNotFoundException.java`
- [x] `ParseException.java`
- [x] `NodeNotFoundException.java`
- [x] `GithubImportException.java`
- [x] `FeatureNotImplementedException.java` (map 501 cho các feature/stub chưa mở)
- [x] `ArchiveImportException.java` (lỗi upload archive: unsupported type, oversize, unsafe entry, no Java files)

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
- [x] `impl/neo4j/Neo4jGraphRepository.java` (raw Driver + Cypher, impl duy nhất; `upsertProject`/`upsertNodes`/`upsertEdges`/`deleteFile`/`getFullGraph`/`searchNodes` đã có, `getNeighborhood`/`getImpact` còn Sprint 2 và ném `UnsupportedOperationException`)
- [x] `impl/neo4j/GraphSchema.java` (mapping label/edge-type + validate property)

> **Đã gỡ trong đợt refactor sang raw Driver:** `NodeRepository.java`,
> `ClassNodeRepository.java`, `MethodNodeRepository.java`, `FileNodeRepository.java`,
> và `ProjectNodeRepository.java`. Không còn dùng Spring Data Neo4j nên không có
> interface repository theo từng type.

### Graph node entity package removed

- [removed] Toàn bộ entity Neo4j `@Node` (`ProjectNode`, `PackageNode`, `FileNode`,
  `ClassNode`, `InterfaceNode`, `EnumNode`, `MethodNode`, `FieldNode`, `RouteNode`,
  `AnnotationNode`) đã bị xóa khỏi repo. Dữ liệu graph được mang bởi
  `NodeData`/`EdgeData` của parser và ghi xuống dưới dạng raw Cypher; "label" của node
  nằm trong `GraphSchema`, không phải dưới dạng class entity Java.

### `src/main/java/com/vibegraph/graph/controller/`

- [x] `ProjectController.java`
- [x] `GraphController.java`
- [x] `ImportController.java` (`POST /api/projects/import-archive` sync/async và `POST /api/projects/import-github` đã có)
- [s] `ImpactController.java` (chỉ khung `@RestController`, chưa có endpoint — `// TODO`)

### `src/main/java/com/vibegraph/graph/service/`

- [x] `ProjectService.java` + [x] `impl/ProjectServiceImpl.java`
- [x] `AnalyzeService.java` + [x] `impl/AnalyzeServiceImpl.java`
- [x] `GraphService.java` + [x] `impl/GraphServiceImpl.java` (`GraphServiceTest` đã bật và pass cho `getFullGraph`/`searchNodes`)
- [x] `ArchiveImportService.java` + [x] `impl/ArchiveImportServiceImpl.java` (flow chính Sprint 2 — nhận upload ZIP/TAR/TAR.GZ, validate, materialize `.java`, register project, analyze sync/async)
- [s] `ImpactService.java` + [s] `impl/ImpactServiceImpl.java` (interface chưa có method; impl `// TODO: Implement`; `ImpactServiceTest` còn `@Disabled`)
- [x] `TarballImportService.java` (service contract có `importFromGithub`)
- [x] `impl/TarballImportServiceImpl.java` (GitHub public repo: parse URL, pre-flight, download tarball, extract qua archive pipeline, analyze async, broadcast status)

### `src/main/java/com/vibegraph/graph/importer/config/`

- [x] `ArchiveImportProperties.java` (config Sprint 2 cho `vibegraph.import.archive.max-size`, `workspace-root`, `ignored-paths`; đã tạo trong Task 1)

### `src/main/java/com/vibegraph/graph/importer/`

- [x] `ArchiveType.java` (ZIP/TAR/TAR_GZ/TGZ enum)
- [x] `ArchiveTypeDetector.java` (detect allow-list `.zip`, `.tar`, `.tar.gz`, `.tgz`)
- [x] `ArchiveExtractor.java` (extract safe `.java` entries, chống path traversal/absolute path/symlink/archive bomb theo giới hạn config)
- [x] `ArchiveExtractionResult.java` (extractedRoot, javaFiles, bytesWritten, warnings)

### `src/main/java/com/vibegraph/graph/websocket/`

- [~] `GraphUpdateController.java` (`broadcastStatus` đã publish `/topic/projects/{id}/status`; `broadcastFullUpdate`/`broadcastIncremental` cho graph updates còn TODO)
- [s] `WebSocketEventListener.java` (listener đã có; connect/disconnect handler còn `// TODO`)

### `src/main/java/com/vibegraph/graph/dto/`

- [x] `request/CreateProjectRequest.java`
- [x] `request/AnalyzeRequest.java`
- [x] `request/GithubImportRequest.java`
- [x] `request/GraphFilterRequest.java`
- [optional] `request/ArchiveImportRequest.java` (không bắt buộc cho MVP nếu controller nhận multipart trực tiếp bằng `@RequestParam`/`@RequestPart`; chỉ tạo nếu cần gom validation riêng)
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
- [s] `service/UseCaseDiagramService.java` + [s] `impl/UseCaseDiagramServiceImpl.java` (interface/impl còn khung; `// TODO: Implement`)
- [s] `service/ClassDiagramService.java` + [s] `impl/ClassDiagramServiceImpl.java` (interface/impl còn khung; `// TODO: Implement`)
- [s] `service/MermaidGeneratorService.java` + [s] `impl/MermaidGeneratorServiceImpl.java` (interface/impl còn khung; `// TODO: Implement`)
- [s] `repository/DiagramQueryRepository.java` (class đã có; query methods còn TODO)
- [x] `dto/response/DiagramResponse.java`
- [x] `dto/response/UseCaseResponse.java`

> `DiagramServiceTest` đã có nhưng còn `@Disabled`.

### `src/main/java/com/vibegraph/mcp/`

- [s] `tool/ArchitectureTool.java` (`// TODO: Add @Tool method`)
- [s] `tool/ClassContextTool.java` (`// TODO: Add @Tool method`)
- [s] `tool/LayerPatternTool.java` (`// TODO: Add @Tool method`)
- [s] `tool/ImpactAnalysisTool.java` (`// TODO: Add @Tool method`)
- [s] `controller/McpEndpointController.java` (class/controller đã có; chưa expose endpoint/tool method riêng trong code này)
- [s] `service/McpToolService.java` + [s] `impl/McpToolServiceImpl.java` (interface/impl còn khung; `// TODO: Implement`)
- [s] `service/ArchitectureAnalyzer.java` + [s] `impl/ArchitectureAnalyzerImpl.java` (interface/impl còn khung; `// TODO: Implement`)
- [x] response DTOs: `ArchitectureContextResponse`, `ClassContextResponse`, `LayerPatternResponse`
- [x] request DTOs: `ClassContextRequest`, `LayerPatternRequest`

> `McpToolsTest` đã có nhưng còn `@Disabled`.

### `src/main/java/com/vibegraph/watcher/`

- [x] `config/WatcherProperties.java`
- [x] `service/FileWatcherService.java` + [s] `impl/FileWatcherServiceImpl.java` (`startWatching`/`stopWatching` còn `// TODO`)
- [s] `service/DebouncedEventHandler.java` (class đã có; debounce logic còn `// TODO`)

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
- [x] `parser/visitor/ClassVisitorTest.java`, `MethodVisitorTest.java`, `FieldVisitorTest.java`, `ImportVisitorTest.java`, `SpringAnnotationVisitorTest.java`
- [x] `parser/service/ParserServiceTest.java`
- [x] `graph/repository/impl/neo4j/GraphSchemaTest.java`
- [~] `graph/repository/impl/neo4j/Neo4jGraphRepositoryIT.java` (enabled, nhưng tự skip nếu không có Neo4j reachable)
- [x] `graph/controller/ProjectControllerTest.java`, `GraphControllerTest.java`
- [x] `graph/service/GraphServiceTest.java`
- [x] `graph/service/impl/ProjectServiceImplTest.java`
- [x] `graph/importer/config/ArchiveImportPropertiesTest.java`

Đã có nhưng còn `@Disabled` hoặc chưa kiểm chứng end-to-end:

- [~] `graph/service/ImpactServiceTest.java`
- [~] `graph/service/TarballImportServiceTest.java`
- [x] `graph/service/impl/ArchiveImportServiceImplTest.java` (flow chính archive import)
- [x] `graph/importer/ArchiveExtractorTest.java` (ZIP/TAR/TAR.GZ safe extraction + unsafe entry rejection)
- [x] `graph/controller/ImportControllerTest.java` (multipart `import-archive` và `import-github` controller coverage)
- [~] `graph/controller/ImportControllerTest.java`
- [~] `diagram/service/DiagramServiceTest.java`
- [~] `mcp/tool/McpToolsTest.java`
- [~] `watcher/service/FileWatcherServiceTest.java`
- [~] `VibeGraphApplicationTests.java` (nạp context)

Test fixture:

- [x] `src/test/resources/sample-project/` (4 file Java mẫu)

## Frontend - `vibegraph-web/src/`

### Views

- [s] `HomeView.vue` (khung trang đã có; project list/create còn `// TODO`)
- [x] `GraphView.vue`
- [s] `SettingsView.vue` (khung trang đã có; settings form còn `// TODO`)

### Components

- [s] `components/layout/HeaderBar.vue` (khung đã có; project selector/search/settings còn `// TODO`)
- [s] `components/layout/MainLayout.vue` (khung đã có; chưa render `SidePanel | GraphCanvas | NodeDetailPanel`)
- [s] `components/layout/SidePanel.vue` (khung đã có; tabs/panel content còn `// TODO`)
- [s] `components/layout/StatusBar.vue` (khung đã có; connection/stats còn `// TODO`)
- [s] `components/panels/FilterPanel.vue` (khung đã có; node/edge toggles + focus depth còn `// TODO`)
- [s] `components/panels/ExplorerPanel.vue` (khung đã có; search/folder tree còn `// TODO`)
- [s] `components/panels/FlowsPanel.vue` (khung đã có; flow list/highlight còn `// TODO`)
- [s] `components/panels/NodeDetailPanel.vue` (khung đã có; incoming/outgoing detail còn `// TODO`)
- [s] `components/panels/LegendPanel.vue` (khung đã có; render legend còn `// TODO`)
- [s] `components/panels/FocusDepthControl.vue` (khung đã có; radio controls còn `// TODO`)
- [s] `components/panels/CodeInspector.vue` (khung đã có; code viewer/syntax highlight còn `// TODO`)
- [x] `components/graph/GraphCanvas.vue`
- [s] `components/graph/GraphControls.vue` (khung đã có; control buttons còn `// TODO`)
- [s] `components/graph/SearchBar.vue` (khung đã có; input/dropdown còn `// TODO`)
- [s] `components/diagram/DiagramPanel.vue` (khung đã có; tabs/render active diagram còn `// TODO`)
- [s] `components/diagram/UseCaseDiagram.vue` (khung đã có; Mermaid render còn `// TODO`)
- [s] `components/diagram/ClassDiagram.vue` (khung đã có; Mermaid render + package filter còn `// TODO`)
- [defer] `components/diagram/SequenceDiagram.vue` (file khung có sẵn, nhưng sequence diagram nằm ngoài MVP 2 tháng)
- [x] `components/projects/AddProjectArchive.vue` (flow chính — chọn file `.zip`/`.tar`/`.tar.gz`, bấm Add, hiển thị trạng thái/progress)
- [ ] `Empty placeholder: vibegraph-web/src/components/projects/GitHubImportForm.vue`
- [x] `components/ui/Button.vue`
- [x] `components/ui/Input.vue`
- [s] `components/ui/Tabs.vue` (khung đã có; tab triggers/content slot còn `// TODO`)
- [x] `components/ui/Spinner.vue` (CSS spinner đã hoạt động; TODO SVG animation không chặn MVP)

### Composables, Stores, Types

- [x] `composables/useSigma.ts`
- [x] `composables/useWebSocket.ts` (STOMP-over-SockJS connect/subscribe/disconnect đã có; dùng cho status topic)
- [x] `composables/useGraphData.ts`
- [s] `composables/useDiagrams.ts` (Mermaid rendering còn `// TODO`)
- [s] `composables/useFilters.ts` (filter state management còn `// TODO`)
- [x] `composables/useArchiveImport.ts` (flow chính — gọi `POST /api/projects/import-archive` multipart sync/async, theo dõi progress qua status topic)
- [ ] `Empty placeholder: vibegraph-web/src/composables/useGitHubImport.ts`
- [x] `lib/constants.ts`
- [x] `lib/api.ts`
- [x] `lib/graphAdapter.ts` (được bao phủ bởi `lib/__tests__/graphAdapter.spec.ts`)
- [s] `lib/focusMode.ts` (N-hop BFS/reducers còn `// TODO`)
- [x] `lib/colors.ts`
- [x] `stores/graph.ts`
- [s] `stores/filter.ts` (state đã có; toggle actions còn `// TODO`)
- [s] `stores/project.ts` (state đã có; project management actions còn `// TODO`)
- [x] `types/graph.ts`

> `stores/counter.ts` là scaffolding Vue còn sót lại và có thể gỡ bỏ.

## DevOps - Gốc repo

- [x] `pom.xml` có dependency `commons-compress` (đã có; dùng cho TAR/TAR.GZ và có thể dùng chung với GitHub tarball import)
- [x] `docker-compose.yml` cho dev cục bộ
- [x] `Dockerfile` cho module backend gốc
- [x] `vibegraph-web/Dockerfile`
- [x] `vibegraph-web/nginx.conf`
- [x] `.env.example`
- [x] `.dockerignore`
- [~] `README.md`
- [ ] `Empty placeholder: .github/workflows/ci.yml`
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
- [defer] Use-case-context MCP tool (not in current backlog)
- [defer] Coding-rules MCP tool (not in current backlog)
- [defer] service sequence diagram và UI sequence diagram bản production
- [defer] billing và tài khoản người dùng đầy đủ (auth/rate-limit tối thiểu cho public demo được theo dõi trong Sprint 2 backlog)

## Hạng mục công việc tiếp theo

Lát cắt dọc Sprint 1 đã hiện thực và xanh. Bề mặt Sprint 2-3 cho **diagram, MCP,
watcher/realtime và nhiều panel frontend hiện mới ở mức scaffold/stub** (xem các dòng
`[s]` ở trên) — nên phần việc còn lại gồm cả hiện thực, không chỉ kiểm chứng:

1. Hoàn thiện các phần còn lại quanh import: GitHub import UI (`GitHubImportForm.vue`/`useGitHubImport.ts`) và hardening public-demo cho archive/GitHub flow.
2. Hiện thực các stub `[s]`: endpoint cho `DiagramController`/`ImpactController`, các
   `*DiagramServiceImpl` + `MermaidGeneratorServiceImpl`, 4 MCP `@Tool` +
   `McpToolServiceImpl`/`ArchitectureAnalyzerImpl`, và `getNeighborhood`/`getImpact`
   trong `Neo4jGraphRepository`.
3. Bật lại các test backend đang `@Disabled` (impact, tarball/archive import, import controller,
   diagram, mcp, watcher, app context) và làm cho chúng xanh — biến `[~]` thành `[x]`.
4. Dựng các frontend scaffold `[s]`: layout shell, filter/explorer/detail panels,
   graph controls/search, filter/focus logic, graph patch realtime, và diagram UI.
5. Dựng UI import GitHub: `Empty placeholder: vibegraph-web/src/composables/useGitHubImport.ts` và
   `Empty placeholder: vibegraph-web/src/components/projects/GitHubImportForm.vue` (đường import phía backend đã có sẵn).
6. Thêm `Empty placeholder: .github/workflows/ci.yml` và các trang `docs/` setup + tích hợp MCP.
7. Gỡ scaffolding còn sót (`stores/counter.ts`).
